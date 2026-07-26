package com.contentfilter.core.network.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

enum class PublicDestinationDecision {
    Allow,
    NeedsDnsValidation,
    Block,
}

data class PublicDestinationResult(
    val decision: PublicDestinationDecision,
    val reason: String,
    val host: String? = null,
)

/**
 * Canonical, visual-engine-neutral destination guard shared by DAG v1 and v2.
 *
 * Literal hosts are decided synchronously. Hostname DNS is resolved only from
 * suspendable navigation/network setup paths, never from WebView interception.
 */
@Singleton
class PublicNetworkDestinationGuard
    @Inject
    constructor() {
        fun validateImmediate(url: String): PublicDestinationResult {
            val uri =
                runCatching { URI(url) }.getOrNull()
                    ?: return blocked("invalid_url")
            if (!uri.scheme.equals("https", true)) return blocked("non_https")
            val host =
                uri.host
                    ?.removeSurrounding("[", "]")
                    ?.trim()
                    ?.lowercase()
                    ?.removeSuffix(".")
                    .orEmpty()
            if (host.isBlank()) return blocked("missing_host")
            if (host.isAmbiguousNumericHost()) return blocked("ambiguous_numeric_ip_literal", host)
            if (!host.isIpLiteral() && host.isSpecialHostname()) {
                return blocked("private_or_special_hostname", host)
            }
            if (!host.isIpLiteral()) {
                return PublicDestinationResult(
                    decision = PublicDestinationDecision.NeedsDnsValidation,
                    reason = "hostname_requires_dns",
                    host = host,
                )
            }
            val address =
                host.parseIpLiteral()
                    ?: return blocked("invalid_ip_literal")
            return if (isPublicAddress(address)) {
                PublicDestinationResult(PublicDestinationDecision.Allow, "public_ip_literal", host)
            } else {
                blocked("private_or_reserved_ip_literal", host)
            }
        }

        suspend fun validateNavigation(
            url: String,
            timeoutMillis: Long = DefaultDnsTimeoutMillis,
        ): PublicDestinationResult {
            val immediate = validateImmediate(url)
            if (immediate.decision != PublicDestinationDecision.NeedsDnsValidation) return immediate
            val host = immediate.host ?: return blocked("missing_host")
            val addresses =
                withTimeoutOrNull(timeoutMillis) {
                    withContext(Dispatchers.IO) { InetAddress.getAllByName(host).toList() }
                } ?: return blocked("dns_validation_failed", host)
            return if (addresses.isNotEmpty() && addresses.all(::isPublicAddress)) {
                PublicDestinationResult(PublicDestinationDecision.Allow, "public_dns_destination", host)
            } else {
                blocked("private_reserved_or_empty_dns_answer", host)
            }
        }

        companion object {
            const val DefaultDnsTimeoutMillis = 3_000L

            fun isPublicAddress(address: InetAddress): Boolean {
                if (
                    address.isAnyLocalAddress ||
                    address.isLoopbackAddress ||
                    address.isLinkLocalAddress ||
                    address.isSiteLocalAddress ||
                    address.isMulticastAddress
                ) {
                    return false
                }
                return when (address) {
                    is Inet4Address -> address.address.isPublicIpv4()
                    is Inet6Address -> address.address.isPublicIpv6()
                    else -> false
                }
            }

            private fun ByteArray.isPublicIpv4(): Boolean {
                val first = this[0].toInt() and 0xff
                val second = this[1].toInt() and 0xff
                val third = this[2].toInt() and 0xff
                return when {
                    first == 0 -> false
                    first == 10 -> false
                    first == 100 && second in 64..127 -> false
                    first == 127 -> false
                    first == 169 && second == 254 -> false
                    first == 172 && second in 16..31 -> false
                    first == 192 && second == 0 && third == 0 -> false
                    first == 192 && second == 0 && third == 2 -> false
                    first == 192 && second == 168 -> false
                    first == 198 && second in 18..19 -> false
                    first == 198 && second == 51 && third == 100 -> false
                    first == 203 && second == 0 && third == 113 -> false
                    first >= 224 -> false
                    else -> true
                }
            }

            private fun ByteArray.isPublicIpv6(): Boolean {
                val first = this[0].toInt() and 0xff
                val second = this[1].toInt() and 0xff
                val third = this[2].toInt() and 0xff
                val fourth = this[3].toInt() and 0xff
                val fifth = this[4].toInt() and 0xff
                val sixth = this[5].toInt() and 0xff
                val uniqueLocal = first and 0xfe == 0xfc
                val linkLocal = first == 0xfe && second and 0xc0 == 0x80
                val wellKnownIpv4Translation =
                    first == 0x00 &&
                        second == 0x64 &&
                        third == 0xff &&
                        fourth == 0x9b &&
                        drop(4).take(8).all { it == 0.toByte() }
                val localIpv4Translation =
                    first == 0x00 &&
                        second == 0x64 &&
                        third == 0xff &&
                        fourth == 0x9b &&
                        fifth == 0x00 &&
                        sixth == 0x01
                val discardOnly =
                    first == 0x01 &&
                        second == 0x00 &&
                        third == 0x00 &&
                        fourth == 0x00 &&
                        fifth == 0x00 &&
                        sixth == 0x00
                val ietfProtocolAssignments =
                    first == 0x20 &&
                        second == 0x01 &&
                        third <= 0x01
                val documentation = first == 0x20 && second == 0x01 && third == 0x0d && fourth == 0xb8
                val benchmark = first == 0x20 && second == 0x01 && third == 0x00 && fourth == 0x02
                val orchid =
                    first == 0x20 &&
                        second == 0x01 &&
                        (third and 0xf0 == 0x10 || third and 0xf0 == 0x20)
                val sixToFour = first == 0x20 && second == 0x02
                val additionalDocumentation = first == 0x3f && second and 0xf0 == 0xf0
                val segmentRouting = first == 0x5f
                val multicast = first == 0xff
                val allocatedGlobalUnicast = first in 0x20..0x3f
                return allocatedGlobalUnicast &&
                    !uniqueLocal &&
                    !linkLocal &&
                    !wellKnownIpv4Translation &&
                    !localIpv4Translation &&
                    !discardOnly &&
                    !ietfProtocolAssignments &&
                    !documentation &&
                    !benchmark &&
                    !orchid &&
                    !sixToFour &&
                    !additionalDocumentation &&
                    !segmentRouting &&
                    !multicast
            }

            private fun String.isIpLiteral(): Boolean = contains(':') || isStrictIpv4Literal()

            private fun String.isStrictIpv4Literal(): Boolean =
                split('.').let { parts ->
                    parts.size == 4 &&
                        parts.all { part ->
                            part.isNotBlank() &&
                                part.all(Char::isDigit) &&
                                (part == "0" || !part.startsWith('0'))
                        }
                }

            private fun String.isSpecialHostname(): Boolean =
                !contains('.') ||
                    this == "localhost" ||
                    SpecialHostnameSuffixes.any { suffix -> endsWith(suffix) }

            private fun String.isAmbiguousNumericHost(): Boolean {
                val parts = split('.')
                return (all { it.isDigit() || it == '.' } && !isStrictIpv4Literal()) ||
                    parts.any { it.startsWith("0x", true) }
            }

            private fun String.parseIpLiteral(): InetAddress? =
                if (contains(':')) {
                    runCatching { InetAddress.getByName(this) }.getOrNull()
                } else {
                    val octets =
                        split('.')
                            .takeIf { it.size == 4 }
                            ?.map { it.toIntOrNull()?.takeIf { value -> value in 0..255 } ?: return null }
                            ?: return null
                    InetAddress.getByAddress(octets.map(Int::toByte).toByteArray())
                }

            private fun blocked(
                reason: String,
                host: String? = null,
            ) = PublicDestinationResult(PublicDestinationDecision.Block, reason, host)

            private val SpecialHostnameSuffixes =
                setOf(
                    ".localhost",
                    ".local",
                    ".localdomain",
                    ".internal",
                    ".lan",
                    ".home.arpa",
                    ".test",
                    ".invalid",
                    ".example",
                    ".onion",
                )
        }
    }
