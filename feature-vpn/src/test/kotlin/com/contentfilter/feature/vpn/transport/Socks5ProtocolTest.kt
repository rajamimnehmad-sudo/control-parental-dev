package com.contentfilter.feature.vpn.transport

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Socks5ProtocolTest {
    @Test
    fun `method and RFC1929 credentials are required`() {
        val output = ByteArrayOutputStream()
        assertTrue(
            Socks5Protocol.negotiateMethod(
                ByteArrayInputStream(byteArrayOf(5, 2, 0, 2)),
                output,
            ),
        )
        assertContentEquals(byteArrayOf(5, 2), output.toByteArray())

        val authOutput = ByteArrayOutputStream()
        assertTrue(
            Socks5Protocol.authenticate(
                ByteArrayInputStream(byteArrayOf(1, 1, 'u'.code.toByte(), 1, 'p'.code.toByte())),
                authOutput,
                byteArrayOf('u'.code.toByte()),
                byteArrayOf('p'.code.toByte()),
            ),
        )
        assertContentEquals(byteArrayOf(1, 0), authOutput.toByteArray())

        val rejectedOutput = ByteArrayOutputStream()
        assertEquals(
            false,
            Socks5Protocol.authenticate(
                ByteArrayInputStream(byteArrayOf(1, 1, 'x'.code.toByte(), 1, 'p'.code.toByte())),
                rejectedOutput,
                byteArrayOf('u'.code.toByte()),
                byteArrayOf('p'.code.toByte()),
            ),
        )
        assertContentEquals(byteArrayOf(1, 1), rejectedOutput.toByteArray())
    }

    @Test
    fun `IPv4 and IPv6 requests parse while DOMAIN rejects`() {
        val ipv4 = byteArrayOf(5, 1, 0, 1, 127, 0, 0, 1, 0x01, 0xBB.toByte())
        val ipv6 = byteArrayOf(5, 1, 0, 4) + InetAddress.getByName("2001:db8::1").address + byteArrayOf(0x01, 0xBB.toByte())
        val domain = byteArrayOf(5, 1, 0, 3, 1, 'x'.code.toByte(), 0x01, 0xBB.toByte())

        assertEquals(443, Socks5Protocol.readRequest(ByteArrayInputStream(ipv4))?.target?.port)
        assertEquals(Socks5Command.Connect, Socks5Protocol.readRequest(ByteArrayInputStream(ipv6))?.command)
        assertNull(Socks5Protocol.readRequest(ByteArrayInputStream(domain)))
    }

    @Test
    fun `UDP FRAG nonzero and truncated datagrams reject without poisoning next valid packet`() {
        val target = InetSocketAddress(InetAddress.getByName("203.0.113.8"), 443)
        val valid = Socks5Protocol.encodeUdpDatagram(target, byteArrayOf(1, 2, 3), 3)
        val fragmented = valid.copyOf().also { it[2] = 1 }

        assertNull(Socks5Protocol.parseUdpDatagram(byteArrayOf(0, 0), 2))
        assertNull(Socks5Protocol.parseUdpDatagram(fragmented, fragmented.size))
        assertEquals(Socks5UdpParseResult.Invalid, Socks5Protocol.classifyUdpDatagram(byteArrayOf(0, 0), 2))
        assertEquals(Socks5UdpParseResult.Fragmented, Socks5Protocol.classifyUdpDatagram(fragmented, fragmented.size))
        assertContentEquals(byteArrayOf(1, 2, 3), Socks5Protocol.parseUdpDatagram(valid, valid.size)?.payload)
    }
}
