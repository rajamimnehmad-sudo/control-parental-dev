package com.contentfilter.feature.vpn.service

import java.net.InetAddress
import java.net.InetSocketAddress

/** Minimal, payload-free parser for outbound IPv4/IPv6 TCP and UDP flow tuples. */
internal object VpnFlowTupleParser {
    fun parse(
        packet: ByteArray,
        length: Int,
    ): VpnFlowTuple? {
        if (length <= 0 || length > packet.size) return null
        return when ((packet[0].toInt() ushr VersionShift) and VersionMask) {
            Ipv4Version -> parseIpv4(packet, length)
            Ipv6Version -> parseIpv6(packet, length)
            else -> null
        }
    }

    private fun parseIpv4(
        packet: ByteArray,
        length: Int,
    ): VpnFlowTuple? {
        if (length < Ipv4MinimumHeaderSize) return null
        val headerLength = (packet[0].toInt() and Ipv4HeaderLengthMask) * IntBytes
        if (headerLength < Ipv4MinimumHeaderSize || length < headerLength + PortPairSize) return null
        val fragmentBits = readUInt16(packet, Ipv4FlagsAndFragmentOffset)
        if ((fragmentBits and Ipv4FragmentOffsetMask) != 0) return null
        val protocol = packet[Ipv4ProtocolOffset].toProtocol() ?: return null
        return tuple(
            protocol = protocol,
            packet = packet,
            sourceOffset = Ipv4SourceOffset,
            destinationOffset = Ipv4DestinationOffset,
            addressSize = Ipv4AddressSize,
            transportOffset = headerLength,
        )
    }

    private fun parseIpv6(
        packet: ByteArray,
        length: Int,
    ): VpnFlowTuple? {
        if (length < Ipv6HeaderSize + PortPairSize) return null
        val protocol = packet[Ipv6NextHeaderOffset].toProtocol() ?: return null
        return tuple(
            protocol = protocol,
            packet = packet,
            sourceOffset = Ipv6SourceOffset,
            destinationOffset = Ipv6DestinationOffset,
            addressSize = Ipv6AddressSize,
            transportOffset = Ipv6HeaderSize,
        )
    }

    private fun tuple(
        protocol: VpnTransportProtocol,
        packet: ByteArray,
        sourceOffset: Int,
        destinationOffset: Int,
        addressSize: Int,
        transportOffset: Int,
    ): VpnFlowTuple {
        val source =
            InetAddress.getByAddress(
                packet.copyOfRange(sourceOffset, sourceOffset + addressSize),
            )
        val destination =
            InetAddress.getByAddress(
                packet.copyOfRange(destinationOffset, destinationOffset + addressSize),
            )
        return VpnFlowTuple(
            protocol = protocol,
            localAddress = InetSocketAddress(source, readUInt16(packet, transportOffset)),
            remoteAddress =
                InetSocketAddress(
                    destination,
                    readUInt16(packet, transportOffset + PortFieldSize),
                ),
        )
    }

    private fun Byte.toProtocol(): VpnTransportProtocol? =
        when (toInt() and ByteMask) {
            TcpProtocol -> VpnTransportProtocol.Tcp
            UdpProtocol -> VpnTransportProtocol.Udp
            else -> null
        }

    private fun readUInt16(
        packet: ByteArray,
        offset: Int,
    ): Int =
        ((packet[offset].toInt() and ByteMask) shl ByteBits) or
            (packet[offset + 1].toInt() and ByteMask)

    private const val ByteBits = 8
    private const val ByteMask = 0xFF
    private const val IntBytes = 4
    private const val Ipv4AddressSize = 4
    private const val Ipv4DestinationOffset = 16
    private const val Ipv4FlagsAndFragmentOffset = 6
    private const val Ipv4FragmentOffsetMask = 0x1FFF
    private const val Ipv4HeaderLengthMask = 0x0F
    private const val Ipv4MinimumHeaderSize = 20
    private const val Ipv4ProtocolOffset = 9
    private const val Ipv4SourceOffset = 12
    private const val Ipv4Version = 4
    private const val Ipv6AddressSize = 16
    private const val Ipv6DestinationOffset = 24
    private const val Ipv6HeaderSize = 40
    private const val Ipv6NextHeaderOffset = 6
    private const val Ipv6SourceOffset = 8
    private const val Ipv6Version = 6
    private const val PortFieldSize = 2
    private const val PortPairSize = 4
    private const val TcpProtocol = 6
    private const val UdpProtocol = 17
    private const val VersionMask = 0x0F
    private const val VersionShift = 4
}
