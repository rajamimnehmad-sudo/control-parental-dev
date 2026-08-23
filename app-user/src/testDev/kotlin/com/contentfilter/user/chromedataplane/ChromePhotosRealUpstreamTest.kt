package com.contentfilter.user.chromedataplane

import okhttp3.Dns
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.util.concurrent.Executors
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromePhotosRealUpstreamTest {
    @Test
    fun `upstream default trust rejects untrusted TLS instead of disabling verification`() {
        val tls = ChromePhotosEphemeralTls.create()
        val serverMaterial = tls.serverMaterialFor("localhost")
        val loopback = InetAddress.getByName("127.0.0.1")
        val server = serverMaterial.sslContext.serverSocketFactory.createServerSocket(0, 1, loopback)
        val executor = Executors.newSingleThreadExecutor()
        val accepted =
            executor.submit {
                runCatching {
                    server.accept().use { socket ->
                        (socket as SSLSocket).apply {
                            useClientMode = false
                            startHandshake()
                        }
                    }
                }
            }
        val client =
            ChromePhotosRealUpstream.defaultClient()
                .newBuilder()
                .dns(
                    object : Dns {
                        override fun lookup(hostname: String): List<InetAddress> = listOf(loopback)
                    },
                )
                .build()
        val upstream =
            ChromePhotosRealUpstream(
                upstreamPort = server.localPort,
                client = client,
            )

        try {
            assertFailsWith<SSLHandshakeException> {
                upstream.execute("localhost", ChromePhotosProxyRequest("GET", "/image"))
            }
        } finally {
            upstream.close()
            server.close()
            accepted.get()
            executor.shutdownNow()
            tls.close()
        }
    }

    @Test
    fun `bounded reader stops after explicit image limit`() {
        val exact = ByteArrayInputStream(ByteArray(8) { it.toByte() }).readBounded(8)
        val oversized = ByteArrayInputStream(ByteArray(9) { it.toByte() }).readBounded(8)

        assertFalse(exact.exceeded)
        assertEquals(8, exact.bytes.size)
        assertTrue(oversized.exceeded)
        assertEquals(0, oversized.bytes.size)
    }

    @Test
    fun `upstream URL remains exact HTTPS host without userinfo or authority replacement`() {
        val url =
            ChromePhotosRealUpstream.buildUrl(
                ChromePhotosRealWebLabConfig.HttpBingoHost,
                "/image/png?public=1",
            )

        assertEquals("https", url.scheme)
        assertEquals(ChromePhotosRealWebLabConfig.HttpBingoHost, url.host)
        assertEquals(443, url.port)
        assertFailsWith<IllegalStateException> {
            ChromePhotosRealUpstream.buildUrl(
                ChromePhotosRealWebLabConfig.HttpBingoHost,
                "//example.com/image.png",
            )
        }
    }
}
