package com.contentfilter.user.chromedataplane

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromeHttp1ResponseWriterTest {
    private val writer = ChromeHttp1ResponseWriter(streamBufferBytes = 2)

    @Test
    fun `fixed length exact body succeeds`() {
        val output = ByteArrayOutputStream()

        val result = writer.writeStreaming(output, request(), response("abc", bodyLength = 3))

        assertEquals(3, result.bytesWritten)
        assertEquals("abc", output.bodyText())
    }

    @Test
    fun `fixed length short body fails integrity`() {
        val output = ByteArrayOutputStream()

        val error =
            assertFailsWith<ChromeHttpResponseIntegrityException> {
                writer.writeStreaming(output, request(), response("abc", bodyLength = 5))
            }

        assertEquals(5, error.declaredLength)
        assertEquals(3, error.bytesWritten)
        assertFalse(error.additionalBodyByteObserved)
        assertEquals("abc", output.bodyText())
    }

    @Test
    fun `fixed length long body fails without writing beyond declared length`() {
        val output = ByteArrayOutputStream()

        val error =
            assertFailsWith<ChromeHttpResponseIntegrityException> {
                writer.writeStreaming(output, request(), response("abcdef", bodyLength = 3))
            }

        assertEquals(3, error.declaredLength)
        assertEquals(3, error.bytesWritten)
        assertTrue(error.additionalBodyByteObserved)
        assertEquals("abc", output.bodyText())
        assertFalse(output.toString(Charsets.US_ASCII.name()).contains("abcdef"))
    }

    @Test
    fun `chunked read failure never emits terminal zero chunk`() {
        val output = ByteArrayOutputStream()
        val response = response(FailingAfterPrefixInputStream("abc".toByteArray()), bodyLength = -1)

        assertFailsWith<IOException> { writer.writeStreaming(output, request(), response) }

        val wire = output.toString(Charsets.US_ASCII.name())
        assertTrue(wire.contains("2\r\nab\r\n1\r\nc\r\n"))
        assertFalse(wire.endsWith("0\r\n\r\n"))
    }

    @Test
    fun `chunked clean EOF emits terminal zero chunk exactly once`() {
        val output = ByteArrayOutputStream()

        val result = writer.writeStreaming(output, request(), response("abc", bodyLength = -1))

        val wire = output.toString(Charsets.US_ASCII.name())
        assertEquals(3, result.bytesWritten)
        assertTrue(result.chunked)
        assertTrue(wire.endsWith("2\r\nab\r\n1\r\nc\r\n0\r\n\r\n"))
        assertEquals(1, wire.windowed("0\r\n\r\n".length).count { it == "0\r\n\r\n" })
    }

    @Test
    fun `HEAD informational 204 205 and 304 never write body bytes`() {
        val cases =
            listOf(
                request(method = "HEAD") to response("leak", bodyLength = 4),
                request() to response("leak", bodyLength = 4, statusCode = 100),
                request() to response("leak", bodyLength = 4, statusCode = 204),
                request() to response("leak", bodyLength = 4, statusCode = 205),
                request() to response("leak", bodyLength = 4, statusCode = 304),
            )

        cases.forEach { (request, response) ->
            val output = ByteArrayOutputStream()
            val result = writer.writeStreaming(output, request, response)
            val wire = output.toString(Charsets.US_ASCII.name())

            assertEquals(0, result.bytesWritten)
            assertEquals("", output.bodyText())
            when (response.statusCode) {
                100, 204 -> assertFalse(wire.contains("Content-Length:"))
                205 -> assertTrue(wire.contains("Content-Length: 0\r\n"))
                304 -> assertTrue(wire.contains("Content-Length: 4\r\n"), wire)
            }
        }
    }

    private fun request(method: String = "GET") = ChromePhotosProxyRequest(method, "/resource")

    private fun response(
        body: String,
        bodyLength: Long,
        statusCode: Int = 200,
    ) = response(body.byteInputStream(), bodyLength, statusCode)

    private fun response(
        body: InputStream,
        bodyLength: Long,
        statusCode: Int = 200,
    ) = ChromePhotosUpstreamResponse(
        host = "example.com",
        statusCode = statusCode,
        statusText = "Status",
        headers =
            listOf(
                ChromeHttpHeader("Content-Type", "application/octet-stream"),
                ChromeHttpHeader("Content-Length", bodyLength.coerceAtLeast(0).toString()),
            ),
        body = body,
        bodyLength = bodyLength,
        protocol = "h2",
    )

    private fun ByteArrayOutputStream.bodyText(): String =
        toString(Charsets.US_ASCII.name()).substringAfter("\r\n\r\n")

    private class FailingAfterPrefixInputStream(
        private val prefix: ByteArray,
    ) : InputStream() {
        private var offset = 0

        override fun read(): Int = throw IOException("fixture read failure")

        override fun read(
            bytes: ByteArray,
            destinationOffset: Int,
            length: Int,
        ): Int {
            if (offset == prefix.size) throw IOException("fixture read failure")
            val count = minOf(length, prefix.size - offset)
            prefix.copyInto(bytes, destinationOffset, offset, offset + count)
            offset += count
            return count
        }
    }
}
