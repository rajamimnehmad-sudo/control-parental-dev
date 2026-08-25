package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromePhotosHttpsProxyConnectionTest {
    @Test
    fun `truncated first response closes session before second request`() {
        val upstream =
            ScriptedUpstream(
                Reply(response(body = "abc", bodyLength = 5)),
                Reply(response(body = "second", bodyLength = 6)),
            )
        val result = runSession(upstream, twoRequests())

        assertEquals(1, upstream.executeCalls.get())
        assertEquals(1, result.output.responseCount())
        assertFalse(result.output.contains("502 Error"))
        assertEquals(1, result.failures)
    }

    @Test
    fun `failure before response emits one 502 and closes before second request`() {
        val upstream =
            ScriptedUpstream(
                FailBeforeResponse,
                Reply(response(body = "second", bodyLength = 6)),
            )
        val result = runSession(upstream, twoRequests())

        assertEquals(1, upstream.executeCalls.get())
        assertEquals(1, result.output.responseCount())
        assertTrue(result.output.startsWith("HTTP/1.1 502 Error\r\n"))
        assertTrue(result.output.contains("Connection: close\r\n"))
        assertEquals(1, result.failures)
    }

    @Test
    fun `overlong first response never writes excess or processes second request`() {
        val upstream =
            ScriptedUpstream(
                Reply(response(body = "abcdef", bodyLength = 3)),
                Reply(response(body = "second", bodyLength = 6)),
            )
        val result = runSession(upstream, twoRequests())

        assertEquals(1, upstream.executeCalls.get())
        assertEquals(1, result.output.responseCount())
        assertTrue(result.output.endsWith("\r\n\r\nabc"))
        assertFalse(result.output.contains("abcdef"))
        assertEquals(1, result.failures)
    }

    @Test
    fun `chunked body failure omits terminator and closes before second request`() {
        val upstream =
            ScriptedUpstream(
                Reply(
                    response(
                        body = FailingAfterPrefixInputStream("abc".toByteArray()),
                        bodyLength = -1,
                        extraHeaders = listOf(ChromeHttpHeader("Content-Encoding", "gzip")),
                    ),
                ),
                Reply(response(body = "second", bodyLength = 6)),
            )
        val result = runSession(upstream, twoRequests())

        assertEquals(1, upstream.executeCalls.get())
        assertEquals(1, result.output.responseCount())
        assertTrue(result.output.contains("3\r\nabc\r\n"))
        assertFalse(result.output.endsWith("0\r\n\r\n"))
        assertEquals(1, result.failures)
    }

    @Test
    fun `valid keep alive processes both requests`() {
        val upstream =
            ScriptedUpstream(
                Reply(response(body = "first", bodyLength = 5)),
                Reply(response(body = "second", bodyLength = 6)),
            )
        val result = runSession(upstream, twoRequests())

        assertEquals(2, upstream.executeCalls.get())
        assertEquals(2, result.output.responseCount())
        assertTrue(result.output.contains("first"))
        assertTrue(result.output.contains("second"))
        assertEquals(0, result.failures)
    }

    @Test
    fun `Connection close request prevents processing a buffered second request`() {
        val upstream =
            ScriptedUpstream(
                Reply(response(body = "first", bodyLength = 5)),
                Reply(response(body = "second", bodyLength = 6)),
            )
        val result = runSession(upstream, twoRequests(firstConnectionClose = true))

        assertEquals(1, upstream.executeCalls.get())
        assertEquals(1, result.output.responseCount())
        assertTrue(result.output.contains("Connection: close\r\n"))
        assertFalse(result.output.contains("second"))
        assertEquals(0, result.failures)
    }

    @Test
    fun `HTTP 1_0 defaults to close before a buffered second request`() {
        val upstream =
            ScriptedUpstream(
                Reply(response(body = "first", bodyLength = 5)),
                Reply(response(body = "second", bodyLength = 6)),
            )
        val input =
            "GET /first HTTP/1.0\r\nHost: example.com\r\n\r\n" +
                "GET /second HTTP/1.0\r\nHost: example.com\r\n\r\n"

        val result = runSession(upstream, input)

        assertEquals(1, upstream.executeCalls.get())
        assertEquals(1, result.output.responseCount())
        assertTrue(result.output.contains("Connection: close\r\n"))
        assertFalse(result.output.contains("second"))
        assertEquals(0, result.failures)
    }

    @Test
    fun `fixture write failure closes before a buffered second request`() {
        val fixture = FakeFixtureSource()
        val proxy =
            ChromePhotosHttpsProxy(
                tls = ChromePhotosEphemeralTls.create(),
                origin = fixture,
                onFixtureHeartbeat = {},
                onFatalFailure = {},
                upstream = ScriptedUpstream(),
                transformer = chromePhotosDeterministicTransformer(fixture),
                lifecycleLog = { _, _ -> },
                infoLog = {},
                warningLog = {},
            )

        val failures =
            proxy.use {
                it.handleHttp11Session(
                    input =
                        ByteArrayInputStream(
                            twoRequests(ChromePhotosDataPlaneLabContract.FixtureHost).toByteArray(Charsets.US_ASCII),
                        ),
                    output = FailAfterBytesOutputStream(12),
                    connectTargetHost = ChromePhotosDataPlaneLabContract.FixtureHost,
                    protocol = "http/1.1",
                    shouldContinue = { true },
                )
                it.metrics().failures
            }

        assertEquals(1, fixture.responseCalls.get())
        assertEquals(1, failures)
    }

    private fun runSession(
        upstream: ScriptedUpstream,
        input: String,
    ): SessionResult {
        val fixture = FakeFixtureSource()
        val proxy =
            ChromePhotosHttpsProxy(
                tls = ChromePhotosEphemeralTls.create(),
                origin = fixture,
                onFixtureHeartbeat = {},
                onFatalFailure = {},
                upstream = upstream,
                transformer = chromePhotosDeterministicTransformer(fixture),
                lifecycleLog = { _, _ -> },
                infoLog = {},
                warningLog = {},
            )
        return proxy.use {
            val output = ByteArrayOutputStream()
            it.handleHttp11Session(
                input = ByteArrayInputStream(input.toByteArray(Charsets.US_ASCII)),
                output = output,
                connectTargetHost = "example.com",
                protocol = "http/1.1",
                shouldContinue = { true },
            )
            SessionResult(
                output = output.toString(Charsets.US_ASCII.name()),
                failures = it.metrics().failures,
            )
        }
    }

    private fun twoRequests(
        host: String = "example.com",
        firstConnectionClose: Boolean = false,
    ): String =
        buildString {
            append("GET /first HTTP/1.1\r\nHost: $host\r\n")
            if (firstConnectionClose) append("Connection: close\r\n")
            append("\r\nGET /second HTTP/1.1\r\nHost: $host\r\n\r\n")
        }

    private fun response(
        body: String,
        bodyLength: Long,
    ) = response(body.byteInputStream(), bodyLength, emptyList())

    private fun response(
        body: InputStream,
        bodyLength: Long,
        extraHeaders: List<ChromeHttpHeader> = emptyList(),
    ) = ChromePhotosUpstreamResponse(
        host = "example.com",
        statusCode = 200,
        statusText = "OK",
        headers =
            listOf(ChromeHttpHeader("Content-Type", "text/plain")) + extraHeaders +
                if (bodyLength >= 0) listOf(ChromeHttpHeader("Content-Length", bodyLength.toString())) else emptyList(),
        body = body,
        bodyLength = bodyLength,
        protocol = "h2",
    )

    private fun String.responseCount(): Int = windowed("HTTP/1.1 ".length).count { it == "HTTP/1.1 " }

    private data class SessionResult(
        val output: String,
        val failures: Long,
    )

    private sealed interface Script

    private data class Reply(
        val response: ChromePhotosUpstreamResponse,
    ) : Script

    private object FailBeforeResponse : Script

    private class ScriptedUpstream(
        vararg scripts: Script,
    ) : ChromePhotosUpstream {
        private val scripts = ArrayDeque(scripts.toList())
        val executeCalls = AtomicInteger()

        override fun execute(
            host: String,
            request: ChromePhotosProxyRequest,
        ): ChromePhotosUpstreamExchange {
            executeCalls.incrementAndGet()
            return when (val script = scripts.removeFirst()) {
                FailBeforeResponse -> throw IOException("upstream failed before response")
                is Reply -> ChromePhotosUpstreamExchange(script.response) {}
            }
        }
    }

    private class FakeFixtureSource : ChromePhotosFixtureSource {
        val responseCalls = AtomicInteger()
        override val safeImageBytes = "safe".toByteArray()
        override val sentinelImageBytes = "block".toByteArray()
        override val placeholderImageBytes = "placeholder".toByteArray()

        override fun responseFor(request: ChromePhotosProxyRequest): ChromePhotosFixtureResponse {
            responseCalls.incrementAndGet()
            return ChromePhotosFixtureResponse(
                resourceId = "unused",
                contentType = "text/plain",
                originalBytes = "fixture".toByteArray(),
            )
        }
    }

    private class FailAfterBytesOutputStream(
        private val maximumBytes: Int,
    ) : OutputStream() {
        private var written = 0

        override fun write(value: Int) {
            if (written == maximumBytes) throw IOException("fixture output failure")
            written++
        }
    }

    private class FailingAfterPrefixInputStream(
        private val prefix: ByteArray,
    ) : InputStream() {
        private var delivered = false

        override fun read(): Int = throw IOException("upstream body failure")

        override fun read(
            bytes: ByteArray,
            offset: Int,
            length: Int,
        ): Int {
            if (delivered) throw IOException("upstream body failure")
            delivered = true
            prefix.copyInto(bytes, offset)
            return prefix.size
        }
    }
}
