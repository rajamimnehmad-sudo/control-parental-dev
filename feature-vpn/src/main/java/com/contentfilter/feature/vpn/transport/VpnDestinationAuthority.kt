package com.contentfilter.feature.vpn.transport

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress

internal enum class VpnTransportScope {
    Controlled,
    FullTunnelDev,
}

/** Destination admission shared by the packet policy and the loopback SOCKS endpoint. */
internal class VpnDestinationAuthority(
    controlledAddresses: Collection<String>,
    private val controlledPorts: Set<Int>,
    private val scope: VpnTransportScope,
) {
    private val controlledAddresses = controlledAddresses.mapTo(hashSetOf()) { it.substringBefore('%') }

    fun isAllowed(target: InetSocketAddress): Boolean {
        val address = target.address ?: return false
        val normalized = address.hostAddress.orEmpty().substringBefore('%')
        if (normalized in controlledAddresses && target.port in controlledPorts) return true
        if (scope != VpnTransportScope.FullTunnelDev) return false
        if (target.port !in MinimumPort..MaximumPort || target.port in ReservedDnsPorts) return false
        return address.isPublicUnicast()
    }

    private fun InetAddress.isPublicUnicast(): Boolean {
        if (isAnyLocalAddress || isLoopbackAddress || isLinkLocalAddress || isMulticastAddress || isSiteLocalAddress) {
            return false
        }
        val bytes = address
        return when (this) {
            is Inet4Address -> !bytes.isReservedIpv4()
            is Inet6Address -> !bytes.isReservedIpv6()
            else -> false
        }
    }

    private fun ByteArray.isReservedIpv4(): Boolean {
        val first = this[0].unsigned()
        val second = this[1].unsigned()
        return first == 0 ||
            first == 10 ||
            first == 127 ||
            first >= 224 ||
            (first == 100 && second in 64..127) ||
            (first == 169 && second == 254) ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 0) ||
            (first == 192 && second == 168) ||
            (first == 198 && second in 18..19) ||
            (first == 198 && second == 51 && this[2].unsigned() == 100) ||
            (first == 203 && second == 0 && this[2].unsigned() == 113)
    }

    private fun ByteArray.isReservedIpv6(): Boolean {
        val first = this[0].unsigned()
        val second = this[1].unsigned()
        val documentationPrefix =
            first == 0x20 && second == 0x01 && this[2].unsigned() == 0x0D && this[3].unsigned() == 0xB8
        return first and 0xFE == 0xFC ||
            (first == 0xFE && second and 0xC0 == 0x80) ||
            first == 0xFF ||
            documentationPrefix
    }

    private fun Byte.unsigned(): Int = toInt() and 0xFF

    private companion object {
        const val MinimumPort = 1
        const val MaximumPort = 65_535
        val ReservedDnsPorts = setOf(53, 853)
    }
}
