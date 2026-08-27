package com.contentfilter.user.chromedataplane

import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromeProtectedSocketFactoryTest {
    @Test
    fun `protect false closes socket and performs zero connect`() {
        val socket = TrackingSocket()
        val factory = ChromeProtectedSocketFactory(protect = { false }, delegate = SingleSocketFactory(socket))

        assertFailsWith<IOException> { factory.createSocket("93.184.216.34", 443) }

        assertEquals(1, socket.bindAttempts)
        assertTrue(socket.closed)
        assertEquals(0, socket.connectAttempts)
        assertEquals(ChromePhotosUpstreamMetrics(1, 0, 1), factory.metrics())
    }

    @Test
    fun `protect true happens before any connect attempt`() {
        val socket = TrackingSocket()
        var protectedBeforeConnect = false
        val factory =
            ChromeProtectedSocketFactory(
                protect = {
                    protectedBeforeConnect = socket.bindAttempts == 1 && socket.connectAttempts == 0
                    true
                },
                delegate = SingleSocketFactory(socket),
            )

        factory.createSocket("93.184.216.34", 443)

        assertTrue(protectedBeforeConnect)
        assertEquals(1, socket.bindAttempts)
        assertEquals(1, socket.connectAttempts)
        assertFalse(socket.closed)
        assertEquals(ChromePhotosUpstreamMetrics(1, 1, 0), factory.metrics())
    }

    private class TrackingSocket : Socket() {
        var bindAttempts = 0
        var connectAttempts = 0
        var closed = false

        override fun bind(bindpoint: java.net.SocketAddress?) {
            bindAttempts++
        }

        override fun connect(endpoint: java.net.SocketAddress?) {
            connectAttempts++
        }

        override fun close() {
            closed = true
        }
    }

    private class SingleSocketFactory(
        private val socket: Socket,
    ) : SocketFactory() {
        override fun createSocket(): Socket = socket

        override fun createSocket(
            host: String?,
            port: Int,
        ): Socket = error("unused")

        override fun createSocket(
            host: String?,
            port: Int,
            localHost: InetAddress?,
            localPort: Int,
        ): Socket = error("unused")

        override fun createSocket(
            host: InetAddress?,
            port: Int,
        ): Socket = error("unused")

        override fun createSocket(
            address: InetAddress?,
            port: Int,
            localAddress: InetAddress?,
            localPort: Int,
        ): Socket = error("unused")
    }
}
