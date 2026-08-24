package com.contentfilter.feature.vpn.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class VpnPacketParserTest {
    @Test
    fun `IPv4 IHL and total length produce outbound TCP flow`() {
        val packet = ipv4(protocol = 6, transportSize = 20, ihlWords = 6)
        packet[20] = 1
        packet[21] = 2
        packet[22] = 3
        packet[23] = 4
        packet.writeUInt16(24, 41_001)
        packet.writeUInt16(26, 443)
        packet[37] = 0x02

        val parsed = assertIs<VpnPacketParseResult.Parsed>(VpnPacketParser.parse(packet, packet.size))

        assertEquals(24, parsed.transportOffset)
        assertEquals("10.8.0.2", parsed.flow.localAddress.address.hostAddress)
        assertEquals("203.0.113.8", parsed.flow.remoteAddress.address.hostAddress)
        assertEquals(41_001, parsed.flow.localAddress.port)
        assertEquals(443, parsed.flow.remoteAddress.port)
        assertEquals(true, parsed.tcpFlags?.syn)
    }

    @Test
    fun `IPv4 fragments and incoherent total length reject`() {
        val fragmented = ipv4(protocol = 17, transportSize = 8)
        fragmented[6] = 0x20
        val badLength = ipv4(protocol = 17, transportSize = 8)
        badLength.writeUInt16(2, badLength.size + 1)

        assertEquals(
            VpnPacketParseResult.Reason.Fragmented,
            assertIs<VpnPacketParseResult.Rejected>(VpnPacketParser.parse(fragmented, fragmented.size)).reason,
        )
        assertEquals(
            VpnPacketParseResult.Reason.MalformedIpv4,
            assertIs<VpnPacketParseResult.Rejected>(VpnPacketParser.parse(badLength, badLength.size)).reason,
        )
    }

    @Test
    fun `IPv6 destination extension reaches UDP tuple`() {
        val packet = ipv6(nextHeader = 60, extensionCount = 1, transportProtocol = 17, transportSize = 8)

        val parsed = assertIs<VpnPacketParseResult.Parsed>(VpnPacketParser.parse(packet, packet.size))

        assertEquals(17, parsed.flow.protocol.number)
        assertEquals(48, parsed.transportOffset)
        assertEquals(443, parsed.flow.remoteAddress.port)
    }

    @Test
    fun `IPv6 fragmented and excessive extension chains reject`() {
        val fragmented = ipv6(nextHeader = 44, extensionCount = 1, transportProtocol = 17, transportSize = 8)
        fragmented[42] = 0
        fragmented[43] = 1
        val excessive = ipv6(nextHeader = 60, extensionCount = 9, transportProtocol = 17, transportSize = 8)

        assertEquals(
            VpnPacketParseResult.Reason.Fragmented,
            assertIs<VpnPacketParseResult.Rejected>(VpnPacketParser.parse(fragmented, fragmented.size)).reason,
        )
        assertEquals(
            VpnPacketParseResult.Reason.ExtensionLimit,
            assertIs<VpnPacketParseResult.Rejected>(VpnPacketParser.parse(excessive, excessive.size)).reason,
        )
    }

    @Test
    fun `malformed transport header never authorizes`() {
        val packet = ipv4(protocol = 6, transportSize = 4)

        assertEquals(
            VpnPacketParseResult.Reason.MalformedIpv4,
            assertIs<VpnPacketParseResult.Rejected>(VpnPacketParser.parse(packet, packet.size)).reason,
        )
    }

    private fun ipv4(
        protocol: Int,
        transportSize: Int,
        ihlWords: Int = 5,
    ): ByteArray {
        val headerSize = ihlWords * 4
        return ByteArray(headerSize + transportSize).apply {
            this[0] = ((4 shl 4) or ihlWords).toByte()
            writeUInt16(2, size)
            this[9] = protocol.toByte()
            byteArrayOf(10, 8, 0, 2).copyInto(this, 12)
            byteArrayOf(203.toByte(), 0, 113, 8).copyInto(this, 16)
            if (transportSize >= 4) {
                writeUInt16(headerSize, 41_001)
                writeUInt16(headerSize + 2, 443)
            }
        }
    }

    private fun ipv6(
        nextHeader: Int,
        extensionCount: Int,
        transportProtocol: Int,
        transportSize: Int,
    ): ByteArray {
        val extensionBytes = extensionCount * 8
        return ByteArray(40 + extensionBytes + transportSize).apply {
            this[0] = 0x60
            writeUInt16(4, extensionBytes + transportSize)
            this[6] = nextHeader.toByte()
            this[8] = 0x20
            this[9] = 0x01
            this[24] = 0x20
            this[25] = 0x01
            repeat(extensionCount) { index ->
                val offset = 40 + index * 8
                this[offset] = (if (index == extensionCount - 1) transportProtocol else nextHeader).toByte()
                this[offset + 1] = 0
            }
            val transportOffset = 40 + extensionBytes
            if (transportSize >= 4) {
                writeUInt16(transportOffset, 52_000)
                writeUInt16(transportOffset + 2, 443)
            }
        }
    }

    private fun ByteArray.writeUInt16(
        offset: Int,
        value: Int,
    ) {
        this[offset] = (value ushr 8).toByte()
        this[offset + 1] = value.toByte()
    }
}
