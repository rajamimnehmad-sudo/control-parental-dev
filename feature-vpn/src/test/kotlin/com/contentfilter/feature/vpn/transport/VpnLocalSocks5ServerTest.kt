package com.contentfilter.feature.vpn.transport

import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VpnLocalSocks5ServerTest {
    @Test
    fun `authenticated CONNECT relays through a protected TCP socket`() {
        val loopback = InetAddress.getByName("127.0.0.1")
        val fixture = ServerSocket(0, 1, loopback)
        val fixtureReady = CountDownLatch(1)
        val fixtureThread =
            thread(name = "SocksTcpFixture") {
                fixtureReady.countDown()
                fixture.accept().use { peer ->
                    val payload = peer.getInputStream().readNBytes(4)
                    peer.getOutputStream().write(payload)
                }
            }
        assertTrue(fixtureReady.await(1, TimeUnit.SECONDS))
        val protected = AtomicBoolean(false)
        val sockets =
            VpnProtectedSocketFactory(
                protectTcp = {
                    protected.set(true)
                    true
                },
                protectUdp = { true },
            )
        val socks =
            VpnLocalSocks5Server(
                protectedSockets = sockets,
                allowedAddresses = setOf(loopback.hostAddress.orEmpty()),
                allowedPorts = setOf(fixture.localPort),
            )

        try {
            socks.start()
            Socket(loopback, socks.port).use { client ->
                authenticate(client, socks)
                client.getOutputStream().write(connectRequest(loopback, fixture.localPort))
                assertEquals(Socks5Protocol.Success, readReply(client.getInputStream()))
                val payload = byteArrayOf(1, 2, 3, 4)
                client.getOutputStream().write(payload)
                assertContentEquals(payload, client.getInputStream().readNBytes(payload.size))
            }
            assertTrue(protected.get())
            assertEquals(1, sockets.metrics().tcpConnectAttempts)
            assertEquals(1, socks.metrics().tcpConnects)
        } finally {
            socks.close()
            fixture.close()
            fixtureThread.join(1_000)
        }
    }

    @Test
    fun `UDP associate roundtrips and malformed datagram does not poison association`() {
        val loopback = InetAddress.getByName("127.0.0.1")
        val fixture = DatagramSocket(InetSocketAddress(loopback, 0))
        val fixtureThread =
            thread(name = "SocksUdpFixture") {
                val incoming = DatagramPacket(ByteArray(128), 128)
                fixture.receive(incoming)
                fixture.send(DatagramPacket(incoming.data, incoming.length, incoming.socketAddress))
            }
        val protected = AtomicBoolean(false)
        val sockets =
            VpnProtectedSocketFactory(
                protectTcp = { true },
                protectUdp = {
                    protected.set(true)
                    true
                },
            )
        val socks =
            VpnLocalSocks5Server(
                protectedSockets = sockets,
                allowedAddresses = setOf(loopback.hostAddress.orEmpty()),
                allowedPorts = setOf(fixture.localPort),
            )

        try {
            socks.start()
            Socket(loopback, socks.port).use { control ->
                authenticate(control, socks)
                control.getOutputStream().write(udpAssociateRequest())
                val relayPort = readReplyPort(control.getInputStream())
                DatagramSocket(InetSocketAddress(loopback, 0)).use { client ->
                    client.soTimeout = 2_000
                    client.send(DatagramPacket(byteArrayOf(0, 0), 2, loopback, relayPort))
                    val payload = byteArrayOf(9, 8, 7)
                    val wrapped =
                        Socks5Protocol.encodeUdpDatagram(
                            InetSocketAddress(loopback, fixture.localPort),
                            payload,
                            payload.size,
                        )
                    client.send(DatagramPacket(wrapped, wrapped.size, loopback, relayPort))
                    val response = DatagramPacket(ByteArray(256), 256)
                    client.receive(response)
                    assertContentEquals(
                        payload,
                        Socks5Protocol.parseUdpDatagram(response.data, response.length)?.payload,
                    )
                }
            }
            assertTrue(protected.get())
            assertEquals(1, sockets.metrics().udpSendAttempts)
            assertEquals(1, socks.metrics().udpAssociations)
            assertEquals(1, socks.metrics().udpDatagrams)
            assertEquals(1, socks.metrics().malformedUdpDatagrams)
        } finally {
            socks.close()
            fixture.close()
            fixtureThread.join(1_000)
        }
    }

    private fun authenticate(
        client: Socket,
        socks: VpnLocalSocks5Server,
    ) {
        val output = client.getOutputStream()
        val input = client.getInputStream()
        output.write(byteArrayOf(5, 1, 2))
        assertContentEquals(byteArrayOf(5, 2), input.readNBytes(2))
        val username = socks.username().toByteArray(Charsets.US_ASCII)
        val password = socks.password().toByteArray(Charsets.US_ASCII)
        output.write(
            byteArrayOf(1, username.size.toByte()) +
                username +
                byteArrayOf(password.size.toByte()) +
                password,
        )
        assertContentEquals(byteArrayOf(1, 0), input.readNBytes(2))
    }

    private fun connectRequest(
        address: InetAddress,
        port: Int,
    ): ByteArray =
        byteArrayOf(5, 1, 0, 1) +
            address.address +
            byteArrayOf((port ushr 8).toByte(), port.toByte())

    private fun udpAssociateRequest(): ByteArray = byteArrayOf(5, 3, 0, 1, 0, 0, 0, 0, 0, 0)

    private fun readReply(input: InputStream): Int {
        val reply = input.readNBytes(10)
        assertEquals(10, reply.size)
        return reply[1].toInt() and 0xFF
    }

    private fun readReplyPort(input: InputStream): Int {
        val reply = input.readNBytes(10)
        assertEquals(10, reply.size)
        assertEquals(Socks5Protocol.Success, reply[1].toInt() and 0xFF)
        return ((reply[8].toInt() and 0xFF) shl 8) or (reply[9].toInt() and 0xFF)
    }
}
