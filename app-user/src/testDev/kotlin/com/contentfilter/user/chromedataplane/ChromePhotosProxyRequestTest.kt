package com.contentfilter.user.chromedataplane

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChromePhotosProxyRequestTest {
    private val reader = ChromeHttp1RequestReader(maximumBodyBytes = 1024)

    @Test
    fun `all required methods parse in origin form`() {
        ChromePhotosProxyRequest.AllowedMethods.forEach { method ->
            val request = parse("$method /fixture?q=1 HTTP/1.1\r\nHost: example.com\r\n\r\n")
            assertEquals(method, request.method)
            assertEquals("/fixture?q=1", request.target)
        }
        assertNull(ChromePhotosProxyRequest.parse("GET https://other.example/image.png HTTP/1.1"))
        assertNull(ChromePhotosProxyRequest.parse("TRACE / HTTP/1.1"))
    }

    @Test
    fun `Content-Length preserves binary body and end to end headers`() {
        val body = byteArrayOf(0, 1, 2, 0x7f, 0xff.toByte())
        val prefix =
            "POST /upload HTTP/1.1\r\nHost: example.com\r\nContent-Type: multipart/form-data; boundary=x\r\n" +
                "Content-Length: ${body.size}\r\nCookie: a=1\r\nAuthorization: Bearer fixture\r\n\r\n"
        val request = reader.read(ByteArrayInputStream(prefix.toByteArray() + body))!!

        assertContentEquals(body, request.body)
        assertEquals(ChromeHttpBodyFraming.ContentLength, request.bodyFraming)
        assertEquals("a=1", request.firstHeader("Cookie"))
        assertEquals("Bearer fixture", request.firstHeader("Authorization"))
    }

    @Test
    fun `chunked body is decoded bounded and 100 continue is emitted once`() {
        var continues = 0
        val request =
            reader.read(
                ByteArrayInputStream(
                    (
                        "PATCH /resource HTTP/1.1\r\nHost: example.com\r\nTransfer-Encoding: chunked\r\n" +
                            "Content-Type: application/json\r\nExpect: 100-continue\r\n\r\n" +
                            "3\r\n{\"a\r\n3\r\n\":1\r\n1\r\n}\r\n0\r\n\r\n"
                    ).toByteArray(),
                ),
            ) { continues++ }!!

        assertEquals(1, continues)
        assertEquals("{\"a\":1}", request.body.toString(Charsets.UTF_8))
        assertEquals(ChromeHttpBodyFraming.Chunked, request.bodyFraming)
    }

    @Test
    fun `smuggling malformed truncation oversized and trailers fail closed`() {
        listOf(
            "POST / HTTP/1.1\r\nHost: example.com\r\nContent-Length: 2\r\nTransfer-Encoding: chunked\r\n\r\n",
            "POST / HTTP/1.1\r\nHost: example.com\r\nContent-Length: nope\r\n\r\n",
            "POST / HTTP/1.1\r\nHost: example.com\r\nContent-Length: 5\r\n\r\nabc",
            "POST / HTTP/1.1\r\nHost: example.com\r\nTransfer-Encoding: gzip, chunked\r\n\r\n",
            "POST / HTTP/1.1\r\nHost: example.com\r\nTransfer-Encoding: chunked\r\nTrailer: Digest\r\n\r\n0\r\n\r\n",
        ).forEach { raw -> assertFailsWith<Exception> { parse(raw) } }
        val tooLarge = "POST / HTTP/1.1\r\nHost: example.com\r\nContent-Length: 1025\r\n\r\n"
        assertEquals(413, assertFailsWith<ChromeHttpProtocolException> { parse(tooLarge) }.statusCode)
    }

    @Test
    fun `idle keep alive timeout is distinct from a partial request timeout`() {
        assertFailsWith<ChromeHttpIdleTimeoutException> {
            reader.read(TimeoutInputStream(ByteArray(0)))
        }

        val partial =
            assertFailsWith<ChromeHttpProtocolException> {
                reader.read(TimeoutInputStream("GET /partial".toByteArray()))
            }
        assertEquals(408, partial.statusCode)
    }

    @Test
    fun `request header policy preserves sensitive values for origin but never proxy authorization or hops`() {
        val request =
            ChromePhotosProxyRequest(
                method = "GET",
                target = "/",
                headers =
                    listOf(
                        ChromeHttpHeader("Cookie", "session=fixture"),
                        ChromeHttpHeader("Authorization", "Bearer fixture"),
                        ChromeHttpHeader("Range", "bytes=0-9"),
                        ChromeHttpHeader("If-Range", "\"v1\""),
                        ChromeHttpHeader("Connection", "keep-alive, X-Hop"),
                        ChromeHttpHeader("X-Hop", "drop"),
                        ChromeHttpHeader("Proxy-Authorization", "drop"),
                    ),
            )
        val headers = ChromeHttpHeaderPolicy.upstreamRequestHeaders(request)

        assertTrue(headers.any { it.name == "Cookie" && it.value == "session=fixture" })
        assertTrue(headers.any { it.name == "Authorization" && it.value == "Bearer fixture" })
        assertTrue(headers.any { it.name == "Range" })
        assertTrue(headers.any { it.name == "If-Range" })
        assertFalse(headers.any { it.name in setOf("Connection", "X-Hop", "Proxy-Authorization") })
    }

    @Test
    fun `upgrade is detected for explicit WebSocket fail close`() {
        val request =
            ChromePhotosProxyRequest(
                "GET",
                "/socket",
                headers =
                    listOf(
                        ChromeHttpHeader("Connection", "keep-alive, Upgrade"),
                        ChromeHttpHeader("Upgrade", "websocket"),
                    ),
            )

        assertTrue(request.hasUpgrade())
    }

    @Test
    fun `response writer preserves status duplicate cookies validators and body rules`() {
        val writer = ChromeHttp1ResponseWriter(streamBufferBytes = 3)
        val response =
            ChromePhotosUpstreamResponse(
                host = "example.com",
                statusCode = 206,
                statusText = "Partial Content",
                headers =
                    listOf(
                        ChromeHttpHeader("Content-Type", "application/octet-stream"),
                        ChromeHttpHeader("Content-Length", "6"),
                        ChromeHttpHeader("Content-Range", "bytes 0-5/10"),
                        ChromeHttpHeader("Set-Cookie", "a=1; Secure"),
                        ChromeHttpHeader("Set-Cookie", "b=2; HttpOnly"),
                        ChromeHttpHeader("ETag", "\"v1\""),
                    ),
                body = "abcdef".byteInputStream(),
                bodyLength = 6,
                protocol = "h2",
            )
        val output = ByteArrayOutputStream()
        writer.writeStreaming(output, ChromePhotosProxyRequest("GET", "/range"), response)
        val wire = output.toString(Charsets.US_ASCII.name())

        assertTrue(wire.startsWith("HTTP/1.1 206 Partial Content\r\n"))
        assertEquals(2, Regex("Set-Cookie:").findAll(wire).count())
        assertTrue(wire.contains("Content-Range: bytes 0-5/10"))
        assertTrue(wire.contains("ETag: \"v1\""))
        assertTrue(wire.endsWith("\r\n\r\nabcdef"))

        val noBody = ByteArrayOutputStream()
        writer.writeStreaming(
            noBody,
            ChromePhotosProxyRequest("HEAD", "/range"),
            response.copy(body = "must-not-leak".byteInputStream()),
        )
        assertFalse(noBody.toString(Charsets.US_ASCII.name()).contains("must-not-leak"))

        val notModified = ByteArrayOutputStream()
        writer.writeStreaming(
            notModified,
            ChromePhotosProxyRequest("GET", "/etag"),
            response.copy(
                statusCode = 304,
                statusText = "Not Modified",
                body = "must-not-leak".byteInputStream(),
            ),
        )
        val notModifiedWire = notModified.toString(Charsets.US_ASCII.name())
        assertFalse(notModifiedWire.contains("must-not-leak"))
        assertTrue(notModifiedWire.contains("ETag: \"v1\""))
    }

    private fun parse(raw: String): ChromePhotosProxyRequest = reader.read(ByteArrayInputStream(raw.toByteArray()))!!

    private class TimeoutInputStream(
        private val prefix: ByteArray,
    ) : InputStream() {
        private var offset = 0

        override fun read(): Int {
            if (offset >= prefix.size) throw SocketTimeoutException("fixture timeout")
            return prefix[offset++].toInt() and 0xff
        }
    }
}
