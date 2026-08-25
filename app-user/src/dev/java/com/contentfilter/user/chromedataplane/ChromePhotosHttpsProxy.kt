package com.contentfilter.user.chromedataplane

import android.util.Log
import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLSocket

internal data class ChromePhotosProxyMetrics(
    val connections: Long = 0,
    val requests: Long = 0,
    val safeDecisions: Long = 0,
    val blockedDecisions: Long = 0,
    val unknownDecisions: Long = 0,
    val passthroughResponses: Long = 0,
    val cacheHits: Long = 0,
    val cacheMisses: Long = 0,
    val failures: Long = 0,
    val originalBytes: Long = 0,
    val deliveredBytes: Long = 0,
    val streamedResponses: Long = 0,
    val queueRejected: Long = 0,
    val activeConnectionsPeak: Int = 0,
    val latencyP50Millis: Double = 0.0,
    val latencyP95Millis: Double = 0.0,
    val latencyP99Millis: Double = 0.0,
    val upstream: ChromePhotosUpstreamMetrics = ChromePhotosUpstreamMetrics(0, 0, 0),
    val webSemanticsReport: String = "not_run",
    val imageAuthorityReport: String = "not_run",
    val decisionSession: ChromePhotoDecisionSessionMetrics = ChromePhotoDecisionSessionMetrics(),
    val imageAuthority: ChromeImageAuthorityMetrics = ChromeImageAuthorityMetrics(),
)

internal enum class ChromeHttpConnectionDisposition {
    Continue,
    Close,
}

internal class ChromePhotosHttpsProxy(
    private val tls: ChromePhotosEphemeralTlsMaterial,
    private val origin: ChromePhotosFixtureSource,
    private val onFixtureHeartbeat: () -> Unit,
    private val onFatalFailure: (String) -> Unit,
    private val destinationAuthority: ChromePublicDestinationAuthority = ChromePublicDestinationAuthority(),
    private val upstream: ChromePhotosUpstream = ChromePhotosRealUpstream(destinationAuthority = destinationAuthority),
    private val transformer: ChromePhotosResourceTransformer = chromePhotosDeterministicTransformer(origin),
    private val imageAuthority: ChromeImageContentAuthority = ChromeImageContentAuthority(),
    private val lifecycleLog: (String, Throwable?) -> Unit = ::logProxyLifecycle,
    private val infoLog: (String) -> Unit = { message -> Log.i(LogTag, message) },
    private val warningLog: (String) -> Unit = { message -> Log.w(LogTag, message) },
) : AutoCloseable {
    private val responseSanitizer =
        ChromePhotosRealResponseSanitizer(
            transformer = transformer,
            destinationAuthority = destinationAuthority,
            placeholderBytes = origin.placeholderImageBytes,
            imageAuthority = imageAuthority,
        )
    private val requestReader = ChromeHttp1RequestReader()
    private val responseWriter = ChromeHttp1ResponseWriter()
    private val running = AtomicBoolean(false)
    private val lifecycleLock = Any()
    private var terminal = false
    private var cleanupComplete = false
    private val executor =
        ThreadPoolExecutor(
            WorkerCount,
            WorkerCount,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(WorkerQueueCapacity),
            { runnable -> Thread(runnable, "chrome-web-proxy-worker").apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy(),
        )
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private val connections = AtomicLong()
    private val requests = AtomicLong()
    private val safeDecisions = AtomicLong()
    private val blockedDecisions = AtomicLong()
    private val unknownDecisions = AtomicLong()
    private val passthroughResponses = AtomicLong()
    private val cacheHits = AtomicLong()
    private val cacheMisses = AtomicLong()
    private val failures = AtomicLong()
    private val originalBytes = AtomicLong()
    private val deliveredBytes = AtomicLong()
    private val streamedResponses = AtomicLong()
    private val queueRejected = AtomicLong()
    private val activeConnections = AtomicInteger()
    private val activeConnectionsPeak = AtomicInteger()
    private val latencies = ChromeProxyLatencyWindow(MaximumLatencySamples)

    fun start() {
        synchronized(lifecycleLock) {
            check(!terminal && !cleanupComplete) { "Proxy lifecycle is terminal" }
            check(running.compareAndSet(false, true)) { "Proxy already started" }
            try {
                val socket =
                    ServerSocket(
                        ChromePhotosDataPlaneLabContract.ProxyPort,
                        SocketBacklog,
                        InetAddress.getByName(ChromePhotosDataPlaneLabContract.ProxyHost),
                    ).apply { reuseAddress = true }
                serverSocket = socket
                acceptThread =
                    Thread({ acceptLoop(socket) }, "chrome-web-proxy-accept").apply {
                        isDaemon = true
                        start()
                    }
            } catch (error: Throwable) {
                running.set(false)
                terminal = true
                throw error
            }
        }
        Log.i(LogTag, "phase=proxy_ready bind=loopback port=${ChromePhotosDataPlaneLabContract.ProxyPort}")
    }

    fun metrics(): ChromePhotosProxyMetrics {
        val latency = latencies.snapshot()
        return ChromePhotosProxyMetrics(
            connections = connections.get(),
            requests = requests.get(),
            safeDecisions = safeDecisions.get(),
            blockedDecisions = blockedDecisions.get(),
            unknownDecisions = unknownDecisions.get(),
            passthroughResponses = passthroughResponses.get(),
            cacheHits = cacheHits.get(),
            cacheMisses = cacheMisses.get(),
            failures = failures.get(),
            originalBytes = originalBytes.get(),
            deliveredBytes = deliveredBytes.get(),
            streamedResponses = streamedResponses.get(),
            queueRejected = queueRejected.get(),
            activeConnectionsPeak = activeConnectionsPeak.get(),
            latencyP50Millis = latency.p50,
            latencyP95Millis = latency.p95,
            latencyP99Millis = latency.p99,
            upstream = upstream.metrics(),
            webSemanticsReport = origin.webSemanticsReport(),
            imageAuthorityReport = origin.imageAuthorityReport(),
            decisionSession = transformer.decisionMetrics(),
            imageAuthority = imageAuthority.metrics(),
        )
    }

    fun isHealthy(): Boolean = running.get() && serverSocket?.isClosed == false && !executor.isShutdown

    override fun close() {
        val resources =
            synchronized(lifecycleLock) {
                if (cleanupComplete) return
                running.set(false)
                terminal = true
                cleanupComplete = true
                ProxyResources(serverSocket, acceptThread).also {
                    serverSocket = null
                    acceptThread = null
                }
            }
        runCatching { resources.serverSocket?.close() }
        resources.acceptThread?.interrupt()
        executor.shutdownNow()
        val cleanupFailure =
            listOf(
                runCatching { transformer.close() },
                runCatching { upstream.close() },
                runCatching { tls.close() },
            ).firstNotNullOfOrNull { result -> result.exceptionOrNull() }
        lifecycleLog(
            "phase=proxy_stopped cacheEntries=${transformer.cacheSize()} " +
                "cleanup=${if (cleanupFailure == null) "complete" else "partial_failure"}",
            cleanupFailure,
        )
    }

    private fun acceptLoop(socket: ServerSocket) {
        try {
            while (running.get()) {
                val client = socket.accept()
                connections.incrementAndGet()
                try {
                    executor.execute { handleClient(client) }
                } catch (_: RejectedExecutionException) {
                    queueRejected.incrementAndGet()
                    failures.incrementAndGet()
                    runCatching { client.close() }
                    Log.w(LogTag, "phase=connection_rejected reason=bounded_worker_queue")
                }
            }
        } catch (error: SocketException) {
            if (running.get()) fatal(error)
        } catch (error: Throwable) {
            fatal(error)
        }
    }

    private fun handleClient(client: Socket) {
        val active = activeConnections.incrementAndGet()
        activeConnectionsPeak.accumulateAndGet(active, ::maxOf)
        try {
            client.use { socket ->
                runCatching {
                    socket.soTimeout = SocketTimeoutMillis
                    val requestLine = socket.getInputStream().readConnectLine() ?: return
                    consumeConnectHeaders(socket.getInputStream())
                    val connectTarget = destinationAuthority.admitConnect(requestLine)
                    if (connectTarget == null) {
                        writePlainError(socket.getOutputStream(), 502, "Destination unavailable")
                        Log.i(LogTag, "decision=fail_closed scope=connect_not_public")
                        return
                    }
                    ChromeHttp1Wire.writeAscii(socket.getOutputStream(), "HTTP/1.1 200 Connection Established\r\n\r\n")
                    socket.getOutputStream().flush()
                    handleTlsTunnel(socket, connectTarget)
                }.onFailure { error ->
                    failures.incrementAndGet()
                    Log.w(LogTag, "phase=connection_failed error=${error.javaClass.simpleName}")
                }
            }
        } finally {
            activeConnections.decrementAndGet()
        }
    }

    private fun handleTlsTunnel(
        client: Socket,
        connectTarget: ChromePhotosConnectTarget,
    ) {
        val serverMaterial = tls.serverMaterialFor(connectTarget.host)
        val tlsSocket =
            serverMaterial.sslContext.socketFactory.createSocket(
                client,
                connectTarget.host,
                HttpsPort,
                false,
            ) as SSLSocket
        tlsSocket.useClientMode = false
        tlsSocket.sslParameters = tlsSocket.sslParameters.apply { applicationProtocols = arrayOf(Http11) }
        tlsSocket.startHandshake()
        val protocol = tlsSocket.applicationProtocol.ifBlank { Http11 }
        Log.i(
            LogTag,
            "phase=tls_ready host=${connectTarget.host} clientProtocol=$protocol " +
                "ca=${tls.caFingerprint.take(FingerprintLogLength)}",
        )

        tlsSocket.use { secureSocket ->
            handleHttp11Session(
                input = secureSocket.inputStream,
                output = BufferedOutputStream(secureSocket.outputStream),
                connectTargetHost = connectTarget.host,
                protocol = protocol,
            )
        }
    }

    internal fun handleHttp11Session(
        input: InputStream,
        output: OutputStream,
        connectTargetHost: String,
        protocol: String,
        shouldContinue: () -> Boolean = { running.get() },
    ) {
        while (shouldContinue()) {
            val request =
                try {
                    requestReader.read(input) {
                        ChromeHttp1Wire.writeAscii(output, "HTTP/1.1 100 Continue\r\n\r\n")
                        output.flush()
                    } ?: break
                } catch (_: ChromeHttpIdleTimeoutException) {
                    break
                } catch (error: ChromeHttpProtocolException) {
                    writePlainError(output, error.statusCode, error.message ?: "Invalid request")
                    break
                }
            if (!request.authorityMatches(connectTargetHost)) {
                writePlainError(output, 400, "Host mismatch")
                break
            }
            if (request.hasUpgrade()) {
                writePlainError(output, 501, "Upgrade unsupported")
                infoLog("decision=fail_closed scope=upgrade_unsupported")
                break
            }
            val disposition =
                if (connectTargetHost == ChromePhotosDataPlaneLabContract.FixtureHost) {
                    serveFixtureRequest(request, protocol, output)
                } else {
                    serveRealRequest(connectTargetHost, request, protocol, output)
                }
            if (disposition == ChromeHttpConnectionDisposition.Close) break
        }
    }

    private fun serveFixtureRequest(
        request: ChromePhotosProxyRequest,
        protocol: String,
        output: OutputStream,
    ): ChromeHttpConnectionDisposition {
        var responseStarted = false
        return try {
            val started = System.nanoTime()
            val response = origin.responseFor(imageAuthority.normalizeUpstreamRequest(request))
            val fixtureUpstream = response.asUpstreamResponse()
            val sanitized =
                when (val inspection = imageAuthority.inspectBuffered(request, fixtureUpstream, response.originalBytes)) {
                    is ChromeImageContentInspection.Candidate ->
                        responseSanitizer.sanitizeCandidate(request.method, inspection)
                    is ChromeImageContentInspection.Passthrough ->
                        response.asPassthroughSanitizedResponse()
                }
            requests.incrementAndGet()
            originalBytes.addAndGet(response.originalBytes.size.toLong())
            responseStarted = true
            val result = responseWriter.writeBuffered(output, request, sanitized, forceChunked = response.chunked)
            deliveredBytes.addAndGet(result.bytesWritten)
            recordDecision(sanitized)
            latencies.add(System.nanoTime() - started)
            if (response.resourceId in FixturePresenceResourceIds) onFixtureHeartbeat()
            if (response.resourceId != FixtureHeartbeatId) {
                infoLog(
                    "resource=${response.resourceId} origin=fixture_local destination=chrome protocol=$protocol " +
                        "method=${request.method} status=${response.statusCode} bytesIn=${response.originalBytes.size} " +
                        "bytesOut=${result.bytesWritten} decision=${sanitized.decision.name.lowercase(Locale.US)} " +
                        sanitized.decisionResult.logFields(),
                )
            }
            request.successDisposition()
        } catch (error: Throwable) {
            failures.incrementAndGet()
            warningLog(
                "phase=fixture_failed responseStarted=$responseStarted error=${error.javaClass.simpleName}",
            )
            ChromeHttpConnectionDisposition.Close
        }
    }

    internal fun serveRealRequest(
        host: String,
        request: ChromePhotosProxyRequest,
        clientProtocol: String,
        output: OutputStream,
    ): ChromeHttpConnectionDisposition {
        val started = System.nanoTime()
        var responseStarted = false
        return try {
            val upstreamRequest = imageAuthority.normalizeUpstreamRequest(request)
            upstream.execute(host, upstreamRequest).use { exchange ->
                val response = exchange.response
                requests.incrementAndGet()
                val redirectBlocked =
                    response.statusCode in RedirectCodes &&
                        response.headers.firstValue("Location")?.let(responseSanitizer::isAllowedRedirect) != true
                val inspection =
                    if (response.statusCode in RedirectCodes) {
                        null
                    } else {
                        imageAuthority.inspect(request, response)
                    }
                if (redirectBlocked || inspection is ChromeImageContentInspection.Candidate) {
                    val sanitized =
                        if (redirectBlocked) {
                            responseSanitizer.sanitizeRedirect(response)
                        } else {
                            responseSanitizer.sanitizeCandidate(
                                request.method,
                                inspection as ChromeImageContentInspection.Candidate,
                            )
                        }
                    responseStarted = true
                    val result = responseWriter.writeBuffered(output, request, sanitized)
                    originalBytes.addAndGet(sanitized.inputBytes.toLong())
                    deliveredBytes.addAndGet(result.bytesWritten)
                    recordDecision(sanitized)
                    logRealResponse(host, request, clientProtocol, response, sanitized, result, started)
                } else {
                    val streamingResponse =
                        (inspection as? ChromeImageContentInspection.Passthrough)?.response ?: response
                    responseStarted = true
                    val result = responseWriter.writeStreaming(output, request, streamingResponse)
                    streamedResponses.incrementAndGet()
                    originalBytes.addAndGet(result.bytesWritten)
                    deliveredBytes.addAndGet(result.bytesWritten)
                    passthroughResponses.incrementAndGet()
                    infoLog(
                        "origin=real host=$host method=${request.method} clientProtocol=$clientProtocol " +
                            "upstreamProtocol=${streamingResponse.protocol} status=${streamingResponse.statusCode} " +
                            "contentType=${streamingResponse.headers.firstValue("Content-Type").safeLogContentType()} " +
                            "bytesOut=${result.bytesWritten} transfer=${if (result.chunked) "chunked" else "fixed"}",
                    )
                }
            }
            latencies.add(System.nanoTime() - started)
            request.successDisposition()
        } catch (error: Throwable) {
            failures.incrementAndGet()
            val errorResponseWritten =
                !responseStarted &&
                    runCatching { writePlainError(output, 502, "Upstream unavailable") }.isSuccess
            warningLog(
                "phase=upstream_failed host=$host responseStarted=$responseStarted " +
                    "errorResponseWritten=$errorResponseWritten error=${error.javaClass.simpleName}",
            )
            ChromeHttpConnectionDisposition.Close
        }
    }

    private fun logRealResponse(
        host: String,
        request: ChromePhotosProxyRequest,
        clientProtocol: String,
        response: ChromePhotosUpstreamResponse,
        sanitized: ChromePhotosSanitizedResponse,
        result: ChromeStreamResult,
        started: Long,
    ) {
        infoLog(
            "origin=real host=$host method=${request.method} clientProtocol=$clientProtocol " +
                "upstreamProtocol=${response.protocol} status=${sanitized.statusCode} " +
                "contentType=${sanitized.contentType.safeLogContentType()} bytesIn=${sanitized.inputBytes} " +
                "bytesOut=${result.bytesWritten} cache=${if (sanitized.cacheHit) "hit" else "miss"} " +
                "decision=${sanitized.decision.name.lowercase(Locale.US)} ${sanitized.decisionResult.logFields()} " +
                "requestToDeliveryMs=${started.elapsedMillis(System.nanoTime())}",
        )
    }

    private fun recordDecision(result: ChromePhotosSanitizedResponse) {
        when (result.decision) {
            ChromePhotosResourceDecision.Safe -> safeDecisions.incrementAndGet()
            ChromePhotosResourceDecision.Block -> blockedDecisions.incrementAndGet()
            ChromePhotosResourceDecision.Unknown -> unknownDecisions.incrementAndGet()
            ChromePhotosResourceDecision.Passthrough -> passthroughResponses.incrementAndGet()
        }
        if (result.contentHash != null) {
            if (result.cacheHit) cacheHits.incrementAndGet() else cacheMisses.incrementAndGet()
        }
    }

    internal fun fatal(error: Throwable) {
        val transitioned =
            synchronized(lifecycleLock) {
                if (terminal) {
                    false
                } else {
                    true.also {
                        terminal = true
                        running.set(false)
                    }
                }
            }
        if (!transitioned) return
        failures.incrementAndGet()
        lifecycleLog("phase=proxy_fatal error=${error.javaClass.simpleName}", error)
        onFatalFailure(error.javaClass.simpleName)
    }

    internal fun cleanupCompleted(): Boolean = synchronized(lifecycleLock) { cleanupComplete }

    private fun consumeConnectHeaders(input: InputStream) {
        var total = 0
        repeat(MaxConnectHeaderCount) {
            val line = input.readConnectLine() ?: error("Truncated CONNECT headers")
            total += line.length + 2
            check(total <= MaxConnectHeaderBytes) { "CONNECT headers too large" }
            if (line.isEmpty()) return
            check(':' in line && line.firstOrNull()?.isWhitespace() != true) { "Malformed CONNECT header" }
        }
        error("Too many CONNECT headers")
    }

    private fun InputStream.readConnectLine(): String? {
        val bytes = ArrayList<Byte>()
        var carriageReturn = false
        while (bytes.size <= MaxLineBytes) {
            val value = read()
            if (value < 0 && bytes.isEmpty() && !carriageReturn) return null
            if (value < 0) error("Truncated CONNECT line")
            if (carriageReturn) {
                check(value == '\n'.code) { "CONNECT requires CRLF" }
                return bytes.toByteArray().toString(Charsets.US_ASCII)
            }
            when (value) {
                '\r'.code -> carriageReturn = true
                '\n'.code -> error("Bare LF")
                else -> bytes += value.toByte()
            }
        }
        error("CONNECT line too long")
    }

    private fun writePlainError(
        output: OutputStream,
        code: Int,
        message: String,
    ) {
        val body = message.toByteArray(Charsets.UTF_8)
        ChromeHttp1Wire.writeAscii(
            output,
            "HTTP/1.1 $code Error\r\nContent-Type: text/plain; charset=utf-8\r\n" +
                "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n",
        )
        output.write(body)
        output.flush()
    }

    private fun Long.elapsedMillis(end: Long): String = "%.3f".format(Locale.US, (end - this) / NanosPerMillis)

    private companion object {
        const val HttpsPort = 443
        const val WorkerCount = 8
        const val WorkerQueueCapacity = 32
        const val SocketBacklog = 32
        const val SocketTimeoutMillis = 20_000
        const val MaxLineBytes = 8 * 1024
        const val MaxConnectHeaderCount = 100
        const val MaxConnectHeaderBytes = 64 * 1024
        const val Http11 = "http/1.1"
        const val FixtureHeartbeatId = "fixture-heartbeat"
        const val FingerprintLogLength = 16
        const val NanosPerMillis = 1_000_000.0
        const val MaximumLatencySamples = 512
        const val LogTag = "ChromePhotosDataPlane"
        val FixturePresenceResourceIds = setOf(FixtureHeartbeatId)
        val RedirectCodes = setOf(301, 302, 303, 307, 308)
    }

    private data class ProxyResources(
        val serverSocket: ServerSocket?,
        val acceptThread: Thread?,
    )
}

private fun logProxyLifecycle(
    message: String,
    error: Throwable?,
) {
    if (error == null) Log.i("ChromePhotosDataPlane", message) else Log.e("ChromePhotosDataPlane", message, error)
}

private fun ChromePhotosProxyRequest.successDisposition(): ChromeHttpConnectionDisposition =
    if (closeAfterResponse) ChromeHttpConnectionDisposition.Close else ChromeHttpConnectionDisposition.Continue

private fun ChromePhotosFixtureResponse.asUpstreamResponse(): ChromePhotosUpstreamResponse =
    ChromePhotosUpstreamResponse(
        host = ChromePhotosDataPlaneLabContract.FixtureHost,
        statusCode = statusCode,
        statusText = statusText,
        headers = headers + ChromeHttpHeader("Content-Type", contentType),
        body = originalBytes.inputStream(),
        bodyLength = originalBytes.size.toLong(),
        protocol = "fixture",
    )

private fun ChromePhotosFixtureResponse.asPassthroughSanitizedResponse(): ChromePhotosSanitizedResponse =
    ChromePhotosSanitizedResponse(
        statusCode = statusCode,
        statusText = statusText,
        headers =
            ChromeHttpHeaderPolicy.downstreamResponseHeaders(headers) +
                ChromeHttpHeader("Content-Type", contentType),
        bytes = originalBytes,
        decision = ChromePhotosResourceDecision.Passthrough,
        cacheHit = false,
        contentHash = null,
        inputBytes = originalBytes.size,
    )

private data class ChromeProxyLatencySnapshot(
    val p50: Double,
    val p95: Double,
    val p99: Double,
)

private class ChromeProxyLatencyWindow(
    private val capacity: Int,
) {
    private val samples = ArrayDeque<Long>(capacity)

    @Synchronized
    fun add(nanos: Long) {
        if (samples.size == capacity) samples.removeFirst()
        samples.addLast(nanos)
    }

    @Synchronized
    fun snapshot(): ChromeProxyLatencySnapshot {
        if (samples.isEmpty()) return ChromeProxyLatencySnapshot(0.0, 0.0, 0.0)
        val sorted = samples.sorted()

        fun percentile(value: Double): Double {
            val index = ((sorted.size - 1) * value).toInt().coerceIn(sorted.indices)
            return sorted[index] / 1_000_000.0
        }
        return ChromeProxyLatencySnapshot(percentile(0.50), percentile(0.95), percentile(0.99))
    }
}
