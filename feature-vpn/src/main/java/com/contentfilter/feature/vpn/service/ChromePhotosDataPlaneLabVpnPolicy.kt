package com.contentfilter.feature.vpn.service

import android.content.Context
import android.util.Log
import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneRuntimeAttestation
import com.contentfilter.feature.vpn.dns.VpnPacketDiagnostic
import java.net.InetAddress

internal data class ChromePhotosLabVpnRoute(
    val address: String,
    val prefixLength: Int,
)

internal data class ChromePhotosUdpFixtureGate(
    val address: String,
    val port: Int,
    val malformedProbeEnabled: Boolean,
)

internal object ChromePhotosDataPlaneLabVpnPolicy {
    fun isActive(context: Context): Boolean =
        context.packageName.endsWith(".dev") &&
            context.getSharedPreferences(
                ChromePhotosDataPlaneLabContract.PreferencesName,
                Context.MODE_PRIVATE,
            ).getBoolean(ChromePhotosDataPlaneLabContract.KeyActive, false)

    fun routes(context: Context): List<ChromePhotosLabVpnRoute> {
        val preferences =
            context.getSharedPreferences(
                ChromePhotosDataPlaneLabContract.PreferencesName,
                Context.MODE_PRIVATE,
            )
        return routes(
            active = isActive(context),
            resolvedAddresses =
                preferences.getStringSet(
                    ChromePhotosDataPlaneLabContract.KeyResolvedRouteAddresses,
                    emptySet(),
                ).orEmpty(),
            udpFixtureAddress = udpFixtureGate(context)?.address,
        )
    }

    fun routes(
        active: Boolean,
        resolvedAddresses: Collection<String> = emptySet(),
        udpFixtureAddress: String? = null,
    ): List<ChromePhotosLabVpnRoute> =
        if (active) {
            val fixture =
                ChromePhotosLabVpnRoute(
                    ChromePhotosDataPlaneLabContract.FixtureIpv4,
                    Ipv4HostPrefixLength,
                )
            val realRoutes =
                resolvedAddresses
                    .asSequence()
                    .mapNotNull { address -> runCatching { InetAddress.getByName(address) }.getOrNull() }
                    .filter { address -> !address.isAnyLocalAddress && !address.isLoopbackAddress }
                    .distinctBy(InetAddress::getHostAddress)
                    .take(MaximumResolvedRoutes)
                    .mapNotNull { address ->
                        val hostAddress = address.hostAddress ?: return@mapNotNull null
                        ChromePhotosLabVpnRoute(
                            address = hostAddress.substringBefore('%'),
                            prefixLength = if (address.address.size == Ipv4ByteCount) Ipv4HostPrefixLength else Ipv6HostPrefixLength,
                        )
                    }
                    .toList()
            val udpFixtureRoute =
                udpFixtureAddress
                    ?.let(::parseIpv4Literal)
                    ?.takeIf { address ->
                        address.address.size == Ipv4ByteCount &&
                            address.isSiteLocalAddress &&
                            !address.isAnyLocalAddress &&
                            !address.isLoopbackAddress &&
                            !address.isMulticastAddress
                    }
                    ?.let { address ->
                        ChromePhotosLabVpnRoute(address.hostAddress.orEmpty(), Ipv4HostPrefixLength)
                    }
            listOfNotNull(fixture, udpFixtureRoute) + realRoutes
        } else {
            emptyList()
        }

    fun udpFixtureGate(context: Context): ChromePhotosUdpFixtureGate? {
        if (!isActive(context)) return null
        val preferences =
            context.getSharedPreferences(
                ChromePhotosDataPlaneLabContract.PreferencesName,
                Context.MODE_PRIVATE,
            )
        if (!preferences.getBoolean(ChromePhotosDataPlaneLabContract.KeyUdpFixtureGateEnabled, false)) return null
        val address = preferences.getString(ChromePhotosDataPlaneLabContract.KeyUdpFixtureAddress, "").orEmpty()
        val parsedAddress = parseIpv4Literal(address)
        val port = preferences.getInt(ChromePhotosDataPlaneLabContract.KeyUdpFixturePort, 0)
        if (
            parsedAddress == null ||
            parsedAddress.address.size != Ipv4ByteCount ||
            !parsedAddress.isSiteLocalAddress ||
            parsedAddress.isAnyLocalAddress ||
            parsedAddress.isLoopbackAddress ||
            parsedAddress.isMulticastAddress ||
            port !in MinimumFixturePort..MaximumFixturePort
        ) {
            return null
        }
        return ChromePhotosUdpFixtureGate(
            address = parsedAddress.hostAddress.orEmpty(),
            port = port,
            malformedProbeEnabled =
                preferences.getBoolean(
                    ChromePhotosDataPlaneLabContract.KeyUdpFixtureMalformedProbeEnabled,
                    false,
                ),
        )
    }

    fun additionalAllowedPackages(context: Context): Set<String> = additionalAllowedPackages(udpFixtureGate(context))

    fun additionalAllowedPackages(gate: ChromePhotosUdpFixtureGate?): Set<String> =
        if (gate != null) setOf(ChromePhotosDataPlaneLabContract.UdpFixturePackage) else emptySet()

    fun allowedTransportPorts(context: Context): Set<Int> =
        DefaultTransportPorts + listOfNotNull(udpFixtureGate(context)?.port)

    fun isFixtureDomain(
        active: Boolean,
        normalizedDomain: String,
    ): Boolean = active && normalizedDomain == ChromePhotosDataPlaneLabContract.FixtureHost

    fun fixtureAddresses(queryType: Int): List<ByteArray> =
        if (queryType == DnsTypeA) {
            listOf(InetAddress.getByName(ChromePhotosDataPlaneLabContract.FixtureIpv4).address)
        } else {
            emptyList()
        }

    fun isTunnelConfirmed(
        active: Boolean,
        sessionId: String,
        established: Boolean,
    ): Boolean = active && sessionId.isNotBlank() && established

    fun markTunnelState(
        context: Context,
        established: Boolean,
    ) {
        val preferences =
            context.getSharedPreferences(
                ChromePhotosDataPlaneLabContract.PreferencesName,
                Context.MODE_PRIVATE,
            )
        val sessionId = preferences.getString(ChromePhotosDataPlaneLabContract.KeySessionId, null).orEmpty()
        val confirmed = isTunnelConfirmed(isActive(context), sessionId, established)
        preferences.edit()
            .putBoolean(ChromePhotosDataPlaneLabContract.KeyVpnConfirmed, confirmed)
            .putString(
                ChromePhotosDataPlaneLabContract.KeyVpnSessionId,
                if (confirmed) sessionId else "",
            )
            .commit()
        ChromePhotosDataPlaneRuntimeAttestation.markVpnConfirmed(sessionId, confirmed)
        if (sessionId.isNotBlank()) {
            Log.i(
                LogTag,
                "vpnAttestation=${if (confirmed) "confirmed" else "revoked"} " +
                    "session=${sessionId.take(SessionLogLength)}",
            )
        }
    }

    @Synchronized
    fun recordControlledTransportAttempt(
        context: Context,
        diagnostic: VpnPacketDiagnostic,
    ): Boolean {
        if (!isActive(context) || diagnostic.destinationPort != HttpsPort) return false
        val key =
            when (diagnostic.protocol.lowercase()) {
                "udp" -> ChromePhotosDataPlaneLabContract.KeyQuicAttempts
                "tcp" -> ChromePhotosDataPlaneLabContract.KeyDirectTcpAttempts
                else -> return false
            }
        val preferences =
            context.getSharedPreferences(
                ChromePhotosDataPlaneLabContract.PreferencesName,
                Context.MODE_PRIVATE,
            )
        val count = preferences.getLong(key, 0L) + 1L
        preferences.edit().putLong(key, count).commit()
        Log.i(
            LogTag,
            "transport=${diagnostic.protocol.lowercase()} destination=controlled port=$HttpsPort action=dropped count=$count",
        )
        return true
    }

    private const val DnsTypeA = 1
    private const val HttpsPort = 443
    private const val Ipv4HostPrefixLength = 32
    private const val Ipv6HostPrefixLength = 128
    private const val Ipv4ByteCount = 4
    private const val MaximumResolvedRoutes = 32
    private const val MinimumFixturePort = 20_000
    private const val MaximumFixturePort = 50_000
    private const val Ipv4OctetCount = 4
    private const val SessionLogLength = 8
    private const val LogTag = "ChromePhotosDataPlane"
    private val DefaultTransportPorts = setOf(80, 443)

    private fun parseIpv4Literal(value: String): InetAddress? {
        val octets = value.split('.')
        if (octets.size != Ipv4OctetCount) return null
        val bytes =
            octets.map { octet ->
                if (octet.isEmpty() || octet.any { character -> !character.isDigit() }) return null
                val number = octet.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
                number.toByte()
            }.toByteArray()
        return InetAddress.getByAddress(bytes)
    }
}
