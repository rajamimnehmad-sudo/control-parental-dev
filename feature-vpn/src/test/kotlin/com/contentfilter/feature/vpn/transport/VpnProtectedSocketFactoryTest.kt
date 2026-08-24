package com.contentfilter.feature.vpn.transport

import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VpnProtectedSocketFactoryTest {
    @Test
    fun `protect false means zero TCP connect and UDP send`() {
        val connects = AtomicInteger(0)
        val factory =
            VpnProtectedSocketFactory(
                protectTcp = { false },
                protectUdp = { false },
                tcpConnector = { _, _, _ -> connects.incrementAndGet() },
            )

        assertNull(factory.connectTcp(InetSocketAddress("127.0.0.1", 443)))
        assertNull(factory.openUdp())
        assertEquals(0, connects.get())
        assertEquals(0, factory.metrics().tcpConnectAttempts)
        assertEquals(0, factory.metrics().udpSendAttempts)
        assertEquals(2, factory.metrics().protectFailures)
        assertEquals(1, factory.metrics().protectedUdpSocketsCreated)
        assertEquals(0, factory.metrics().protectUdpSuccess)
        assertEquals(1, factory.metrics().protectUdpFailure)
    }

    @Test
    fun `protect happens before TCP connector`() {
        var protected = false
        val factory =
            VpnProtectedSocketFactory(
                protectTcp = {
                    assertTrue(it.isBound)
                    protected = true
                    true
                },
                protectUdp = {
                    assertTrue(it.isBound)
                    true
                },
                tcpConnector = { socket: Socket, _: InetSocketAddress, timeoutMillis: Int ->
                    check(protected)
                    assertEquals(3_000, timeoutMillis)
                    socket.close()
                },
            )

        assertNotNull(factory.connectTcp(InetSocketAddress("127.0.0.1", 443)))
        assertNotNull(factory.openUdp()).close()
        assertEquals(1, factory.metrics().tcpConnectAttempts)
        assertEquals(1, factory.metrics().protectedUdpSocketsCreated)
        assertEquals(1, factory.metrics().protectUdpSuccess)
        assertEquals(0, factory.metrics().protectUdpFailure)
    }

    @Test
    fun `TCP connect failure releases the protected resource`() {
        val resources = VpnOwnedResourceTracker()
        val factory =
            VpnProtectedSocketFactory(
                protectTcp = { true },
                protectUdp = { true },
                tcpConnector = { _, _, _ -> throw java.net.SocketTimeoutException("fixture timeout") },
                resources = resources,
            )

        assertNull(factory.connectTcp(InetSocketAddress("192.0.2.1", 443)))
        assertEquals(0, resources.snapshot().ownedFdResources)
        assertEquals(1, factory.metrics().tcpConnectAttempts)
    }
}
