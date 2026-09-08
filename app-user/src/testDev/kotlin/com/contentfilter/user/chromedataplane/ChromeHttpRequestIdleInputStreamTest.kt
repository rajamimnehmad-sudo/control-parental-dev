package com.contentfilter.user.chromedataplane

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.SocketException
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class ChromeHttpRequestIdleInputStreamTest {
    @Test
    fun `request headers and upload body retain active deadline after the first byte`() {
        val deadlines = mutableListOf<Int>()
        val source = "POST /form HTTP/1.1\r\nHost: example.com\r\nContent-Length: 5\r\nExpect: 100-continue\r\n\r\na=b&c"
        val input = deadlineInput(ByteArrayInputStream(source.toByteArray()), deadlines)
        input.beginRequest()
        assertEquals(listOf(2_000), deadlines)
        var continued = false
        val request =
            assertNotNull(
                ChromeHttp1RequestReader().read(input) {
                    assertEquals(20_000, deadlines.last())
                    continued = true
                },
            )
        assertEquals(true, continued)
        assertContentEquals("a=b&c".toByteArray(), request.body)
        assertEquals(listOf(2_000, 20_000), deadlines)
    }

    @Test
    fun `pipelined requests rearm idle deadline without losing any bytes`() {
        val deadlines = mutableListOf<Int>()
        val source = "GET /one HTTP/1.1\r\nHost: example.com\r\n\r\nGET /two HTTP/1.1\r\nHost: example.com\r\n\r\n"
        val input = deadlineInput(ByteArrayInputStream(source.toByteArray()), deadlines)
        val reader = ChromeHttp1RequestReader()
        input.beginRequest()
        assertEquals("/one", reader.read(input)?.target)
        input.beginRequest()
        assertEquals("/two", reader.read(input)?.target)
        assertEquals(listOf(2_000, 20_000, 2_000, 20_000), deadlines)
    }

    @Test
    fun `only timeout before a request byte is classified as idle`() {
        val input =
            deadlineInput(
                object : InputStream() {
                    override fun read(): Int = throw SocketTimeoutException("no bytes")
                },
                mutableListOf(),
            )
        input.beginRequest()
        assertFailsWith<ChromeHttpIdleTimeoutException> { ChromeHttp1RequestReader().read(input) }
    }

    @Test
    fun `partial request timeout remains a protocol error rather than an idle close`() {
        val deadlines = mutableListOf<Int>()
        val input =
            deadlineInput(
                object : InputStream() {
                    private var started = false

                    override fun read(): Int {
                        if (!started) {
                            started = true
                            return 'G'.code
                        }
                        assertEquals(20_000, deadlines.last())
                        throw SocketTimeoutException("partial request")
                    }
                },
                deadlines,
            )
        input.beginRequest()
        val error = assertFailsWith<ChromeHttpProtocolException> { ChromeHttp1RequestReader().read(input) }
        assertEquals(408, error.statusCode)
    }

    @Test
    fun `socket errors do not become expected idle cancellation`() {
        val error = SocketException("broken transport")
        val input =
            deadlineInput(
                object : InputStream() {
                    override fun read(): Int = throw error
                },
                mutableListOf(),
            )
        input.beginRequest()
        assertSame(error, assertFailsWith<SocketException> { input.read() })
    }

    @Test
    fun `bulk reads preserve bytes and zero length reads leave idle deadline armed`() {
        val deadlines = mutableListOf<Int>()
        val bytes = byteArrayOf(0, 1, 2, -1)
        val input = deadlineInput(ByteArrayInputStream(bytes), deadlines)
        input.beginRequest()
        val output = ByteArray(4)
        assertEquals(0, input.read(output, 0, 0))
        assertEquals(listOf(2_000), deadlines)
        assertEquals(4, input.read(output))
        assertContentEquals(bytes, output)
        assertEquals(listOf(2_000, 20_000), deadlines)
    }

    private fun deadlineInput(
        input: InputStream,
        deadlines: MutableList<Int>,
    ) = ChromeHttpRequestIdleInputStream(input, deadlines::add, 2_000, 20_000)
}
