package com.contentfilter.user.dag2

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DagV2NetworkGuard
    @Inject
    constructor() {
        suspend fun validate(url: String): DagV2PolicyResult {
            val uri =
                runCatching { URI(url) }.getOrNull()
                    ?: return blocked("La dirección no es válida.")
            if (!uri.scheme.equals("https", true) || uri.host.isNullOrBlank()) {
                return blocked("Sólo se permiten destinos HTTPS.")
            }
            val addresses =
                withTimeoutOrNull(DnsTimeoutMillis) {
                    withContext(Dispatchers.IO) { InetAddress.getAllByName(uri.host).toList() }
                } ?: return blocked("No se pudo validar el destino de red.")
            if (addresses.isEmpty() || addresses.any { !it.isPublicDagV2Address() }) {
                return blocked("El destino pertenece a una red privada o reservada.")
            }
            return DagV2PolicyResult(DagV2SiteDecision.Allow, "Destino de red público.")
        }

        companion object {
            const val DnsTimeoutMillis = 3_000L

            fun InetAddress.isPublicDagV2Address(): Boolean {
                if (
                    isAnyLocalAddress ||
                    isLoopbackAddress ||
                    isLinkLocalAddress ||
                    isSiteLocalAddress ||
                    isMulticastAddress
                ) {
                    return false
                }
                return when (this) {
                    is Inet4Address -> {
                        val octets = address.map { it.toInt() and 0xff }
                        val cgnat = octets[0] == 100 && octets[1] in 64..127
                        val benchmark = octets[0] == 198 && octets[1] in 18..19
                        val documentation =
                            (octets[0] == 192 && octets[1] == 0 && octets[2] == 2) ||
                                (octets[0] == 198 && octets[1] == 51 && octets[2] == 100) ||
                                (octets[0] == 203 && octets[1] == 0 && octets[2] == 113)
                        val reserved = octets[0] == 0 || octets[0] >= 240
                        !cgnat && !benchmark && !documentation && !reserved
                    }
                    is Inet6Address -> {
                        val bytes = address
                        val uniqueLocal = (bytes[0].toInt() and 0xfe) == 0xfc
                        val documentation =
                            bytes[0] == 0x20.toByte() && bytes[1] == 0x01.toByte() &&
                                bytes[2] == 0x0d.toByte() && bytes[3] == 0xb8.toByte()
                        !uniqueLocal && !documentation
                    }
                    else -> false
                }
            }

            private fun blocked(reason: String) = DagV2PolicyResult(DagV2SiteDecision.Block, reason)
        }
    }
