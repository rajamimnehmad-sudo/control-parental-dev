package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.SocketException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromePhotosHttpsProxyConnectionTest {
    @Test
    fun `absolute HTTP uses public port 80 endpoint and origin form upstream`() {
        val upstream = ScriptedUpstream(Reply(response(body = "http", bodyLength = 4)))
        val result =
            runAbsoluteSession(
                upstream,
                "GET http://example.com:80/image.png?q=1 HTTP/1.1\r\nHost: example.com:80\r\n\r\n",
            )

        assertEquals(1, upstream.executeCalls.get())
        assertEquals(
            ChromePhotosUpstreamEndpoint(ChromePhotosUpstreamScheme.Http, "example.com", 80),
            upstream.endpoints.single(),
        )
        assertEquals("/image.png?q=1", upstream.requests.single().target)
        assertTrue(result.output.contains("http"))
    }

    @Test
    fun `absolute HTTP keep alive independently validates each public authority`() {
        val resolved = mutableListOf<String>()
        val authority =
            ChromePublicDestinationAuthority(
                ChromeHostResolver { host ->
                    resolved += host
                    listOf(InetAddress.getByName("93.184.216.34"))
                },
            )
        val upstream =
            ScriptedUpstream(
                Reply(response(body = "first", bodyLength = 5)),
                Reply(response(body = "second", bodyLength = 6)),
            )
        val input =
            "GET http://example.com/first HTTP/1.1\r\nHost: example.com\r\n\r\n" +
                "GET http://other.example/second HTTP/1.1\r\nHost: other.example:80\r\n\r\n"
        val result = runAbsoluteSession(upstream, input, authority)

        assertEquals(listOf("example.com", "other.example"), resolved)
        assertEquals(listOf("example.com", "other.example"), upstream.endpoints.map { it.host })
        assertEquals(2, result.output.responseCount())
    }

    @Test
    fun `absolute HTTP fails closed for Host mismatch and private DNS`() {
        val mismatchUpstream = ScriptedUpstream(Reply(response(body = "never", bodyLength = 5)))
        val mismatch =
            runAbsoluteSession(
                mismatchUpstream,
                "GET http://example.com/image HTTP/1.1\r\nHost: other.example\r\n\r\n",
            )
        val privateAuthority =
            ChromePublicDestinationAuthority(
                ChromeHostResolver { listOf(InetAddress.getByName("127.0.0.1")) },
            )
        val privateUpstream = ScriptedUpstream(Reply(response(body = "never", bodyLength = 5)))
        val private =
            runAbsoluteSession(
                privateUpstream,
                "GET http://private.example/image HTTP/1.1\r\nHost: private.example\r\n\r\n",
                privateAuthority,
            )

        assertTrue(mismatch.output.startsWith("HTTP/1.1 400"))
        assertEquals(0, mismatchUpstream.executeCalls.get())
        assertTrue(private.output.startsWith("HTTP/1.1 502"))
        assertEquals(0, privateUpstream.executeCalls.get())
    }

    @Test
    fun `absolute HTTP fixture stays local and uses origin form`() {
        val fixture = FakeFixtureSource()
        val upstream = ScriptedUpstream()
        val proxy =
            ChromePhotosHttpsProxy(
                tls = ChromePhotosEphemeralTls.create(),
                origin = fixture,
                onFixtureHeartbeat = {},
                onFatalFailure = {},
                destinationAuthority =
                    ChromePublicDestinationAuthority(ChromeHostResolver { error("unexpected DNS") }),
                upstream = upstream,
                transformer = chromePhotosDeterministicTransformer(fixture),
                lifecycleLog = { _, _ -> },
                infoLog = {},
                warningLog = {},
            )
        val output = ByteArrayOutputStream()
        proxy.use {
            it.handleAbsoluteHttp11Session(
                input =
                    ByteArrayInputStream(
                        (
                            "GET http://${ChromePhotosDataPlaneLabContract.FixtureHost}/health HTTP/1.1\r\n" +
                                "Host: ${ChromePhotosDataPlaneLabContract.FixtureHost}:80\r\n\r\n"
                        ).toByteArray(),
                    ),
                output = output,
                shouldContinue = { true },
            )
        }

        assertEquals(1, fixture.responseCalls.get())
        assertEquals("/health", fixture.lastTarget)
        assertEquals(0, upstream.executeCalls.get())
        assertTrue(output.toString(Charsets.US_ASCII.name()).contains("fixture"))
    }

    @Test
    fun `definite non-media fixture passthrough preserves original entity semantics`() {
        val raw = ByteArray(700) { ' '.code.toByte() } + "<svg/>".toByteArray()
        val fixture =
            FakeFixtureSource(
                configuredResponse =
                    ChromePhotosFixtureResponse(
                        resourceId = "padded-svg-as-json",
                        contentType = "application/json",
                        originalBytes = raw,
                        headers =
                            listOf(
                                ChromeHttpHeader("Cache-Control", "public,max-age=86400"),
                                ChromeHttpHeader("ETag", "raw-v1"),
                                ChromeHttpHeader("Accept-Ranges", "bytes"),
                            ),
                    ),
            )
        val proxy =
            ChromePhotosHttpsProxy(
                tls = ChromePhotosEphemeralTls.create(),
                origin = fixture,
                onFixtureHeartbeat = {},
                onFatalFailure = {},
                upstream = ScriptedUpstream(),
                transformer = chromePhotosDeterministicTransformer(fixture),
                imageAuthority = ChromeImageContentAuthority(stockMediaAuthority = true),
                lifecycleLog = { _, _ -> },
                infoLog = {},
                warningLog = {},
            )
        val output = ByteArrayOutputStream()

        proxy.use {
            it.handleHttp11Session(
                input =
                    ByteArrayInputStream(
                        "GET /padded HTTP/1.1\r\nHost: ${ChromePhotosDataPlaneLabContract.FixtureHost}\r\n\r\n"
                            .toByteArray(Charsets.US_ASCII),
                    ),
                output = output,
                connectTargetHost = ChromePhotosDataPlaneLabContract.FixtureHost,
                protocol = "http/1.1",
                shouldContinue = { true },
            )
        }

        val wire = output.toString(Charsets.ISO_8859_1.name())
        assertTrue(wire.contains("Cache-Control: public,max-age=86400"))
        assertTrue(wire.contains("X-Content-Type-Options: nosniff"))
        assertTrue(wire.contains("ETag: raw-v1"))
        assertTrue(wire.contains("Accept-Ranges: bytes"))
        assertTrue(wire.endsWith(raw.toString(Charsets.ISO_8859_1)))
    }

    @Test
    fun `CONNECT tunnel rejects absolute form instead of changing authority`() {
        val upstream = ScriptedUpstream(Reply(response(body = "never", bodyLength = 5)))
        val result =
            runSession(
                upstream,
                "GET http://example.com/image HTTP/1.1\r\nHost: example.com:443\r\n\r\n",
            )

        assertTrue(result.output.startsWith("HTTP/1.1 400"))
        assertEquals(0, upstream.executeCalls.get())
    }

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
                        contentType = "application/json",
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
    fun `real streaming path routes JSON declared JPEG magic through strict byte authority`() {
        val raw = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte()) + "DISGUISED_RAW".toByteArray()
        val upstream =
            ScriptedUpstream(
                Reply(
                    response(
                        body = raw.inputStream(),
                        bodyLength = raw.size.toLong(),
                        contentType = "application/json",
                    ),
                ),
            )

        val result =
            runSession(
                upstream = upstream,
                input =
                    "GET /payload HTTP/1.1\r\n" +
                        "Host: example.com\r\n" +
                        "Sec-Fetch-Dest: image\r\n\r\n",
                imageAuthority = ChromeImageContentAuthority(stockMediaAuthority = true),
            )

        assertFalse(result.output.contains("DISGUISED_RAW"))
        assertTrue(result.output.contains("Cache-Control: no-store"))
        assertTrue(result.output.contains("X-Content-Type-Options: nosniff"))
        assertEquals(listOf("identity"), upstream.requests.single().headerValues("Accept-Encoding"))
    }

    @Test
    fun `real media path authorizes the full body before serving a client range`() {
        val upstream =
            ScriptedUpstream(
                Reply(
                    response(
                        body = "0123456789".byteInputStream(),
                        bodyLength = 10,
                        contentType = "video/mp4",
                    ),
                ),
            )
        val mediaAuthority = ChromeMediaContentAuthority(ChromeMediaPayloadInspector { _, _ -> ChromeMediaPayloadDecision.Safe })

        val result =
            runSession(
                upstream = upstream,
                input =
                    "GET /movie.mp4 HTTP/1.1\r\n" +
                        "Host: example.com\r\n" +
                        "Sec-Fetch-Dest: video\r\n" +
                        "Range: bytes=2-5\r\n\r\n",
                mediaAuthority = mediaAuthority,
            )

        assertTrue(result.output.startsWith("HTTP/1.1 206 Partial Content"))
        assertTrue(result.output.contains("Content-Range: bytes 2-5/10"))
        assertTrue(result.output.endsWith("\r\n\r\n2345"))
        assertEquals(1, upstream.executeCalls.get())
        assertTrue(upstream.requests.single().headerValues("Range").isEmpty())
        assertEquals(listOf("identity"), upstream.requests.single().headerValues("Accept-Encoding"))
        assertEquals(1, mediaAuthority.metrics().safe)
    }

    @Test
    fun `allowed redirect body is discarded with entity metadata invalidated`() {
        val response =
            ChromePhotosUpstreamResponse(
                host = "example.com",
                statusCode = 302,
                statusText = "Found",
                headers =
                    listOf(
                        ChromeHttpHeader("Location", "https://next.example/image"),
                        ChromeHttpHeader("Content-Type", "image/jpeg"),
                        ChromeHttpHeader("Content-Encoding", "gzip"),
                        ChromeHttpHeader("ETag", "raw"),
                        ChromeHttpHeader("Digest", "sha-256=raw"),
                        ChromeHttpHeader("Content-Range", "bytes 0-9/10"),
                    ),
                body = "REDIRECT_RAW_BODY".byteInputStream(),
                bodyLength = 17,
                protocol = "h2",
            )

        val result = runSession(ScriptedUpstream(Reply(response)), "GET /from HTTP/1.1\r\nHost: example.com\r\n\r\n")

        assertTrue(result.output.startsWith("HTTP/1.1 302 Found"))
        assertTrue(result.output.contains("Location: https://next.example/image"))
        assertFalse(result.output.contains("REDIRECT_RAW_BODY"))
        assertFalse(result.output.contains("Content-Encoding:"))
        assertFalse(result.output.contains("ETag:"))
        assertFalse(result.output.contains("Digest:"))
        assertFalse(result.output.contains("Content-Range:"))
        assertTrue(result.output.contains("Content-Length: 0"))
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

    @Test
    fun `client socket reset after response is lifecycle cancellation not proxy failure`() {
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

        proxy.use {
            it.handleHttp11Session(
                input =
                    ByteArrayInputStream(
                        "GET /first HTTP/1.1\r\nHost: ${ChromePhotosDataPlaneLabContract.FixtureHost}\r\n\r\n"
                            .toByteArray(Charsets.US_ASCII),
                    ),
                output = ClientDisconnectOutputStream(),
                connectTargetHost = ChromePhotosDataPlaneLabContract.FixtureHost,
                protocol = "http/1.1",
                shouldContinue = { true },
            )

            assertEquals(0, it.metrics().failures)
            assertEquals(1, it.metrics().clientDisconnects)
        }
    }

    private fun runSession(
        upstream: ScriptedUpstream,
        input: String,
        imageAuthority: ChromeImageContentAuthority = ChromeImageContentAuthority(),
        mediaAuthority: ChromeMediaContentAuthority? = null,
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
                imageAuthority = imageAuthority,
                mediaAuthority = mediaAuthority,
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

    private fun runAbsoluteSession(
        upstream: ScriptedUpstream,
        input: String,
        authority: ChromePublicDestinationAuthority = publicAuthority(),
    ): SessionResult {
        val fixture = FakeFixtureSource()
        val proxy =
            ChromePhotosHttpsProxy(
                tls = ChromePhotosEphemeralTls.create(),
                origin = fixture,
                onFixtureHeartbeat = {},
                onFatalFailure = {},
                destinationAuthority = authority,
                upstream = upstream,
                transformer = chromePhotosDeterministicTransformer(fixture),
                lifecycleLog = { _, _ -> },
                infoLog = {},
                warningLog = {},
            )
        return proxy.use {
            val output = ByteArrayOutputStream()
            it.handleAbsoluteHttp11Session(
                input = ByteArrayInputStream(input.toByteArray(Charsets.US_ASCII)),
                output = output,
                shouldContinue = { true },
            )
            SessionResult(output.toString(Charsets.US_ASCII.name()), it.metrics().failures)
        }
    }

    private fun publicAuthority() =
        ChromePublicDestinationAuthority(
            ChromeHostResolver { listOf(InetAddress.getByName("93.184.216.34")) },
        )

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
        contentType: String = "text/plain",
    ) = ChromePhotosUpstreamResponse(
        host = "example.com",
        statusCode = 200,
        statusText = "OK",
        headers =
            listOf(ChromeHttpHeader("Content-Type", contentType)) + extraHeaders +
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
        val endpoints = mutableListOf<ChromePhotosUpstreamEndpoint>()
        val requests = mutableListOf<ChromePhotosProxyRequest>()

        override fun execute(
            host: String,
            request: ChromePhotosProxyRequest,
        ): ChromePhotosUpstreamExchange = executeScript(request)

        override fun execute(
            endpoint: ChromePhotosUpstreamEndpoint,
            request: ChromePhotosProxyRequest,
        ): ChromePhotosUpstreamExchange {
            endpoints += endpoint
            return executeScript(request)
        }

        private fun executeScript(request: ChromePhotosProxyRequest): ChromePhotosUpstreamExchange {
            executeCalls.incrementAndGet()
            requests += request
            return when (val script = scripts.removeFirst()) {
                FailBeforeResponse -> throw IOException("upstream failed before response")
                is Reply -> ChromePhotosUpstreamExchange(script.response) {}
            }
        }
    }

    private class FakeFixtureSource(
        private val configuredResponse: ChromePhotosFixtureResponse? = null,
    ) : ChromePhotosFixtureSource {
        val responseCalls = AtomicInteger()
        var lastTarget: String? = null
        override val safeImageBytes = "safe".toByteArray()
        override val sentinelImageBytes = "block".toByteArray()
        override val placeholderImageBytes = "placeholder".toByteArray()

        override fun responseFor(request: ChromePhotosProxyRequest): ChromePhotosFixtureResponse {
            responseCalls.incrementAndGet()
            lastTarget = request.target
            return configuredResponse ?: ChromePhotosFixtureResponse(
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

    private class ClientDisconnectOutputStream : OutputStream() {
        override fun write(value: Int) = throw SocketException("connection reset")
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
