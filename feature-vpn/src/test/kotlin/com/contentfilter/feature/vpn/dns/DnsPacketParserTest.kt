package com.contentfilter.feature.vpn.dns

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DnsPacketParserTest {
    private val parser = DnsPacketParser()

    @Test
    fun `parses basic dns query`() {
        val packet = dnsQueryPacket("example.com")

        val question = parser.parseQuery(packet, packet.size)

        assertNotNull(question)
        assertEquals("example.com", question.domain)
        assertEquals(1, question.type)
        assertEquals(53, question.destinationPort)
        assertEquals(43210, question.sourcePort)
    }

    @Test
    fun `returns null for invalid domain`() {
        val packet = dnsQueryPacket("_invalid.example")

        val question = parser.parseQuery(packet, packet.size)

        assertNull(question)
    }

    @Test
    fun `builds nxdomain ipv4 response packet`() {
        val packet = dnsQueryPacket("blocked.example")
        val question = requireNotNull(parser.parseQuery(packet, packet.size))

        val response = DnsResponseFactory().nxdomainPacket(question)

        assertEquals(0x45, response[0].toInt() and 0xFF)
        assertEquals(17, response[9].toInt() and 0xFF)
        assertTrue(response.size > question.queryPayload.size)
    }

    private fun dnsQueryPacket(domain: String): ByteArray {
        val dnsPayload = dnsPayload(domain)
        val packet = ByteArray(Ipv4HeaderSize + UdpHeaderSize + dnsPayload.size)
        packet[0] = 0x45
        packet[8] = 64
        packet[9] = 17
        writeUInt16(packet, 2, packet.size)
        packet[12] = 10
        packet[13] = 0
        packet[14] = 0
        packet[15] = 2
        packet[16] = 1
        packet[17] = 1
        packet[18] = 1
        packet[19] = 1
        writeUInt16(packet, Ipv4HeaderSize, 43210)
        writeUInt16(packet, Ipv4HeaderSize + 2, 53)
        writeUInt16(packet, Ipv4HeaderSize + 4, UdpHeaderSize + dnsPayload.size)
        dnsPayload.copyInto(packet, Ipv4HeaderSize + UdpHeaderSize)
        return packet
    }

    private fun dnsPayload(domain: String): ByteArray {
        val question =
            domain.split(".").flatMap { label ->
                listOf(label.length.toByte()) + label.encodeToByteArray().toList()
            } + listOf(0, 0, 1, 0, 1).map { it.toByte() }
        val payload = ByteArray(DnsHeaderSize + question.size)
        writeUInt16(payload, 0, 0x1234)
        writeUInt16(payload, 4, 1)
        question.toByteArray().copyInto(payload, DnsHeaderSize)
        return payload
    }

    private fun writeUInt16(
        data: ByteArray,
        offset: Int,
        value: Int,
    ) {
        data[offset] = (value ushr 8).toByte()
        data[offset + 1] = value.toByte()
    }

    private companion object {
        const val DnsHeaderSize = 12
        const val Ipv4HeaderSize = 20
        const val UdpHeaderSize = 8
    }
}
