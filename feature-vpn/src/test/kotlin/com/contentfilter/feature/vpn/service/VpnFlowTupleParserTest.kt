package com.contentfilter.feature.vpn.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VpnFlowTupleParserTest {
    @Test
    fun `parses outbound IPv4 TCP tuple without payload`() {
        val packet = ipv4Packet(protocol = 6, sourcePort = 41_001, destinationPort = 443)

        val flow = requireNotNull(VpnFlowTupleParser.parse(packet, packet.size))

        assertEquals(VpnTransportProtocol.Tcp, flow.protocol)
        assertEquals("10.8.0.2", flow.localAddress.address.hostAddress)
        assertEquals(41_001, flow.localAddress.port)
        assertEquals("203.0.113.8", flow.remoteAddress.address.hostAddress)
        assertEquals(443, flow.remoteAddress.port)
    }

    @Test
    fun `parses outbound IPv4 UDP tuple`() {
        val flow =
            requireNotNull(
                VpnFlowTupleParser.parse(
                    ipv4Packet(protocol = 17, sourcePort = 52_002, destinationPort = 53),
                    24,
                ),
            )

        assertEquals(VpnTransportProtocol.Udp, flow.protocol)
        assertEquals(53, flow.remoteAddress.port)
    }

    @Test
    fun `rejects unsupported protocol and non-initial fragment`() {
        val icmp = ipv4Packet(protocol = 1, sourcePort = 0, destinationPort = 0)
        val fragment = ipv4Packet(protocol = 6, sourcePort = 41_001, destinationPort = 443)
        fragment[7] = 1

        assertNull(VpnFlowTupleParser.parse(icmp, icmp.size))
        assertNull(VpnFlowTupleParser.parse(fragment, fragment.size))
    }

    private fun ipv4Packet(
        protocol: Int,
        sourcePort: Int,
        destinationPort: Int,
    ): ByteArray =
        ByteArray(24).apply {
            this[0] = 0x45
            this[9] = protocol.toByte()
            this[12] = 10
            this[13] = 8
            this[14] = 0
            this[15] = 2
            this[16] = 203.toByte()
            this[17] = 0
            this[18] = 113
            this[19] = 8
            writeUInt16(offset = 20, value = sourcePort)
            writeUInt16(offset = 22, value = destinationPort)
        }

    private fun ByteArray.writeUInt16(
        offset: Int,
        value: Int,
    ) {
        this[offset] = (value ushr 8).toByte()
        this[offset + 1] = value.toByte()
    }
}
