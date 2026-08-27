package com.contentfilter.feature.vpn.service

import java.net.InetAddress
import java.net.InetSocketAddress

internal sealed interface VpnPacketParseResult {
    data class Parsed(
        val flow: VpnFlowTuple,
        val ipVersion: Int,
        val packetLength: Int,
        val transportOffset: Int,
        val tcpFlags: VpnTcpFlags? = null,
    ) : VpnPacketParseResult

    data class Rejected(
        val reason: Reason,
    ) : VpnPacketParseResult

    enum class Reason {
        InvalidLength,
        MalformedIpv4,
        MalformedIpv6,
        Fragmented,
        ExtensionLimit,
        UnsupportedProtocol,
    }
}

internal data class VpnTcpFlags(
    val syn: Boolean,
    val ack: Boolean,
    val fin: Boolean,
    val rst: Boolean,
)

/** Bounded outbound IP parser used by the 09A transport authority. */
internal object VpnPacketParser {
    fun parse(
        packet: ByteArray,
        length: Int,
    ): VpnPacketParseResult {
        if (length <= 0 || length > packet.size) {
            return VpnPacketParseResult.Rejected(VpnPacketParseResult.Reason.InvalidLength)
        }
        return when ((packet[0].unsigned() ushr VersionShift) and VersionMask) {
            Ipv4Version -> parseIpv4(packet, length)
            Ipv6Version -> parseIpv6(packet, length)
            else -> VpnPacketParseResult.Rejected(VpnPacketParseResult.Reason.UnsupportedProtocol)
        }
    }

    private fun parseIpv4(
        packet: ByteArray,
        length: Int,
    ): VpnPacketParseResult {
        if (length < Ipv4MinimumHeaderSize) return rejectedMalformedIpv4()
        val headerLength = (packet[0].unsigned() and Ipv4HeaderLengthMask) * IntBytes
        val totalLength = readUInt16(packet, Ipv4TotalLengthOffset)
        if (
            headerLength < Ipv4MinimumHeaderSize ||
            totalLength < headerLength ||
            totalLength > length
        ) {
            return rejectedMalformedIpv4()
        }
        val fragmentBits = readUInt16(packet, Ipv4FlagsAndFragmentOffset)
        if ((fragmentBits and (Ipv4MoreFragmentsMask or Ipv4FragmentOffsetMask)) != 0) {
            return VpnPacketParseResult.Rejected(VpnPacketParseResult.Reason.Fragmented)
        }
        return parseTransport(
            packet = packet,
            packetLength = totalLength,
            ipVersion = Ipv4Version,
            nextHeader = packet[Ipv4ProtocolOffset].unsigned(),
            sourceOffset = Ipv4SourceOffset,
            destinationOffset = Ipv4DestinationOffset,
            addressSize = Ipv4AddressSize,
            transportOffset = headerLength,
        )
    }

    private fun parseIpv6(
        packet: ByteArray,
        length: Int,
    ): VpnPacketParseResult {
        if (length < Ipv6HeaderSize) return rejectedMalformedIpv6()
        val payloadLength = readUInt16(packet, Ipv6PayloadLengthOffset)
        if (payloadLength == 0) return rejectedMalformedIpv6()
        val packetLength = Ipv6HeaderSize + payloadLength
        if (packetLength > length) return rejectedMalformedIpv6()

        var nextHeader = packet[Ipv6NextHeaderOffset].unsigned()
        var offset = Ipv6HeaderSize
        var extensionCount = 0
        while (nextHeader in Ipv6VariableExtensionHeaders || nextHeader == Ipv6FragmentHeader) {
            extensionCount++
            if (extensionCount > MaximumIpv6ExtensionHeaders) {
                return VpnPacketParseResult.Rejected(VpnPacketParseResult.Reason.ExtensionLimit)
            }
            if (nextHeader == Ipv6FragmentHeader) {
                if (offset + Ipv6FragmentHeaderSize > packetLength) return rejectedMalformedIpv6()
                val fragmentBits = readUInt16(packet, offset + Ipv6FragmentBitsOffset)
                if ((fragmentBits and Ipv6FragmentMeaningfulMask) != 0) {
                    return VpnPacketParseResult.Rejected(VpnPacketParseResult.Reason.Fragmented)
                }
                nextHeader = packet[offset].unsigned()
                offset += Ipv6FragmentHeaderSize
            } else {
                if (offset + Ipv6ExtensionMinimumSize > packetLength) return rejectedMalformedIpv6()
                val extensionSize = (packet[offset + 1].unsigned() + 1) * Ipv6ExtensionUnitSize
                if (extensionSize < Ipv6ExtensionMinimumSize || offset + extensionSize > packetLength) {
                    return rejectedMalformedIpv6()
                }
                nextHeader = packet[offset].unsigned()
                offset += extensionSize
            }
        }
        return parseTransport(
            packet = packet,
            packetLength = packetLength,
            ipVersion = Ipv6Version,
            nextHeader = nextHeader,
            sourceOffset = Ipv6SourceOffset,
            destinationOffset = Ipv6DestinationOffset,
            addressSize = Ipv6AddressSize,
            transportOffset = offset,
        )
    }

    private fun parseTransport(
        packet: ByteArray,
        packetLength: Int,
        ipVersion: Int,
        nextHeader: Int,
        sourceOffset: Int,
        destinationOffset: Int,
        addressSize: Int,
        transportOffset: Int,
    ): VpnPacketParseResult {
        val protocol =
            when (nextHeader) {
                TcpProtocol -> VpnTransportProtocol.Tcp
                UdpProtocol -> VpnTransportProtocol.Udp
                else -> return VpnPacketParseResult.Rejected(VpnPacketParseResult.Reason.UnsupportedProtocol)
            }
        val minimumTransportLength = if (protocol == VpnTransportProtocol.Tcp) TcpMinimumHeaderSize else UdpHeaderSize
        if (transportOffset + minimumTransportLength > packetLength) {
            return if (ipVersion == Ipv4Version) rejectedMalformedIpv4() else rejectedMalformedIpv6()
        }
        val source = InetAddress.getByAddress(packet.copyOfRange(sourceOffset, sourceOffset + addressSize))
        val destination =
            InetAddress.getByAddress(packet.copyOfRange(destinationOffset, destinationOffset + addressSize))
        val flags =
            if (protocol == VpnTransportProtocol.Tcp) {
                val value = packet[transportOffset + TcpFlagsOffset].unsigned()
                VpnTcpFlags(
                    syn = value and TcpSynMask != 0,
                    ack = value and TcpAckMask != 0,
                    fin = value and TcpFinMask != 0,
                    rst = value and TcpRstMask != 0,
                )
            } else {
                null
            }
        return VpnPacketParseResult.Parsed(
            flow =
                VpnFlowTuple(
                    protocol = protocol,
                    localAddress = InetSocketAddress(source, readUInt16(packet, transportOffset)),
                    remoteAddress = InetSocketAddress(destination, readUInt16(packet, transportOffset + PortFieldSize)),
                ),
            ipVersion = ipVersion,
            packetLength = packetLength,
            transportOffset = transportOffset,
            tcpFlags = flags,
        )
    }

    private fun readUInt16(
        packet: ByteArray,
        offset: Int,
    ): Int = (packet[offset].unsigned() shl ByteBits) or packet[offset + 1].unsigned()

    private fun Byte.unsigned(): Int = toInt() and ByteMask

    private fun rejectedMalformedIpv4() = VpnPacketParseResult.Rejected(VpnPacketParseResult.Reason.MalformedIpv4)

    private fun rejectedMalformedIpv6() = VpnPacketParseResult.Rejected(VpnPacketParseResult.Reason.MalformedIpv6)

    private const val ByteBits = 8
    private const val ByteMask = 0xFF
    private const val IntBytes = 4
    private const val VersionMask = 0x0F
    private const val VersionShift = 4

    private const val Ipv4Version = 4
    private const val Ipv4AddressSize = 4
    private const val Ipv4MinimumHeaderSize = 20
    private const val Ipv4HeaderLengthMask = 0x0F
    private const val Ipv4TotalLengthOffset = 2
    private const val Ipv4FlagsAndFragmentOffset = 6
    private const val Ipv4MoreFragmentsMask = 0x2000
    private const val Ipv4FragmentOffsetMask = 0x1FFF
    private const val Ipv4ProtocolOffset = 9
    private const val Ipv4SourceOffset = 12
    private const val Ipv4DestinationOffset = 16

    private const val Ipv6Version = 6
    private const val Ipv6AddressSize = 16
    private const val Ipv6HeaderSize = 40
    private const val Ipv6PayloadLengthOffset = 4
    private const val Ipv6NextHeaderOffset = 6
    private const val Ipv6SourceOffset = 8
    private const val Ipv6DestinationOffset = 24
    private const val Ipv6FragmentHeader = 44
    private const val Ipv6FragmentHeaderSize = 8
    private const val Ipv6FragmentBitsOffset = 2
    private const val Ipv6FragmentMeaningfulMask = 0xFFF9
    private const val Ipv6ExtensionMinimumSize = 8
    private const val Ipv6ExtensionUnitSize = 8
    private const val MaximumIpv6ExtensionHeaders = 8
    private val Ipv6VariableExtensionHeaders = setOf(0, 43, 60)

    private const val TcpProtocol = 6
    private const val UdpProtocol = 17
    private const val TcpMinimumHeaderSize = 20
    private const val UdpHeaderSize = 8
    private const val PortFieldSize = 2
    private const val TcpFlagsOffset = 13
    private const val TcpFinMask = 0x01
    private const val TcpSynMask = 0x02
    private const val TcpRstMask = 0x04
    private const val TcpAckMask = 0x10
}
