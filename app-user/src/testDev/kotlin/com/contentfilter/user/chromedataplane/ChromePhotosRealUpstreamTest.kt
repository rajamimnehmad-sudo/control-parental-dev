package com.contentfilter.user.chromedataplane

import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocket
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChromePhotosRealUpstreamTest {
    @Test
    fun `default upstream dispatcher preserves bounded multi-origin fanout`() {
        val dispatcher = ChromePhotosRealUpstream.defaultClient().dispatcher

        assertEquals(ChromePhotosRealUpstream.DefaultMaximumRequests, dispatcher.maxRequests)
        assertEquals(ChromePhotosRealUpstream.DefaultMaximumRequestsPerHost, dispatcher.maxRequestsPerHost)
    }

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
        val upstream = ChromePhotosRealUpstream(upstreamPort = server.localPort, client = client)

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
    fun `request preserves method body credentials cookies and conditional headers without following redirect`() {
        val captured = AtomicReference<okhttp3.Request>()
        val client =
            OkHttpClient.Builder()
                .addInterceptor(
                    Interceptor { chain ->
                        captured.set(chain.request())
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(307)
                            .message("Temporary Redirect")
                            .addHeader("Location", "https://next.example/final")
                            .addHeader("Set-Cookie", "a=1; Secure")
                            .addHeader("Set-Cookie", "b=2; HttpOnly")
                            .body("redirect-body".toResponseBody("text/plain".toMediaType()))
                            .build()
                    },
                ).build()
        val upstream = ChromePhotosRealUpstream(client = client)
        val body = "{\"fixture\":true}".toByteArray()
        val request =
            ChromePhotosProxyRequest(
                method = "POST",
                target = "/login?next=1",
                headers =
                    listOf(
                        ChromeHttpHeader("Host", "example.com"),
                        ChromeHttpHeader("Content-Type", "application/json"),
                        ChromeHttpHeader("Cookie", "session=fixture"),
                        ChromeHttpHeader("Authorization", "Bearer fixture"),
                        ChromeHttpHeader("If-None-Match", "\"v1\""),
                        ChromeHttpHeader("Connection", "keep-alive, X-Hop"),
                        ChromeHttpHeader("X-Hop", "removed"),
                        ChromeHttpHeader("Proxy-Authorization", "removed"),
                    ),
                body = body,
                bodyFraming = ChromeHttpBodyFraming.ContentLength,
            )

        upstream.execute("example.com", request).use { exchange ->
            assertEquals(307, exchange.response.statusCode)
            assertEquals(2, exchange.response.headers.count { it.name == "Set-Cookie" })
        }
        val sent = captured.get()
        val sink = Buffer()
        sent.body!!.writeTo(sink)
        assertEquals("POST", sent.method)
        assertEquals("session=fixture", sent.header("Cookie"))
        assertEquals("Bearer fixture", sent.header("Authorization"))
        assertEquals("\"v1\"", sent.header("If-None-Match"))
        assertNull(sent.header("X-Hop"))
        assertNull(sent.header("Proxy-Authorization"))
        assertContentEquals(body, sink.readByteArray())
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
    fun `upstream timeout or reset propagates without fabricated response`() {
        val client =
            OkHttpClient.Builder()
                .addInterceptor(Interceptor { throw IOException("controlled reset") })
                .build()
        val upstream = ChromePhotosRealUpstream(client = client)

        assertFailsWith<IOException> {
            upstream.execute("example.com", ChromePhotosProxyRequest("GET", "/reset"))
        }
    }

    @Test
    fun `upstream URL remains exact HTTPS host without userinfo or authority replacement`() {
        val url = ChromePhotosRealUpstream.buildUrl("example.com", "/image/png?public=1")

        assertEquals("https", url.scheme)
        assertEquals("example.com", url.host)
        assertEquals(443, url.port)
        assertFailsWith<IllegalStateException> {
            ChromePhotosRealUpstream.buildUrl("example.com", "//other.example/image.png")
        }
    }

    @Test
    fun `upstream endpoint builds exact HTTP port 80 URL`() {
        val url =
            ChromePhotosRealUpstream.buildUrl(
                scheme = ChromePhotosUpstreamScheme.Http,
                host = "example.com",
                target = "/image.png?q=1",
                port = 80,
            )

        assertEquals("http", url.scheme)
        assertEquals("example.com", url.host)
        assertEquals(80, url.port)
        assertEquals("/image.png?q=1", url.encodedPath + "?" + url.encodedQuery)
    }
}
