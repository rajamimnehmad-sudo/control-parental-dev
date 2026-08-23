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
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLSocket

internal data class ChromePhotosProxyMetrics(
    val connections: Long,
    val requests: Long,
    val safeDecisions: Long,
    val blockedDecisions: Long,
    val unknownDecisions: Long,
    val passthroughResponses: Long,
    val cacheHits: Long,
    val cacheMisses: Long,
    val failures: Long,
    val originalBytes: Long,
    val deliveredBytes: Long,
)

internal class ChromePhotosHttpsProxy(
    private val tls: ChromePhotosEphemeralTlsMaterial,
    private val origin: ChromePhotosFixtureSource,
    private val onFixtureHeartbeat: () -> Unit,
    private val onFatalFailure: (String) -> Unit,
    private val upstream: ChromePhotosUpstream = ChromePhotosRealUpstream(),
    private val transformer: ChromePhotosResourceTransformer = chromePhotosDeterministicTransformer(origin),
    private val lifecycleLog: (String, Throwable?) -> Unit = ::logProxyLifecycle,
) : AutoCloseable {
    private val hostAllowlist = ChromePhotosHostAllowlist(ChromePhotosRealWebLabConfig.allowedHosts)
    private val responseSanitizer =
        ChromePhotosRealResponseSanitizer(
            transformer = transformer,
            allowlist = hostAllowlist,
            placeholderBytes = origin.placeholderImageBytes,
        )
    private val running = AtomicBoolean(false)
    private val lifecycleLock = Any()
    private var terminal = false
    private var cleanupComplete = false
    private val executor: ExecutorService = Executors.newFixedThreadPool(WorkerCount)
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
                    Thread(
                        {
                            try {
                                while (running.get()) {
                                    val client = socket.accept()
                                    connections.incrementAndGet()
                                    executor.execute { handleClient(client) }
                                }
                            } catch (error: SocketException) {
                                if (running.get()) fatal(error)
                            } catch (error: Throwable) {
                                fatal(error)
                            }
                        },
                        "chrome-photos-proxy-accept",
                    ).apply {
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

    fun metrics(): ChromePhotosProxyMetrics =
        ChromePhotosProxyMetrics(
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
        )

    fun isHealthy(): Boolean = running.get() && serverSocket?.isClosed == false

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
                runCatching { transformer.clear() },
                runCatching { upstream.close() },
                runCatching { tls.close() },
            ).firstNotNullOfOrNull { result -> result.exceptionOrNull() }
        lifecycleLog(
            "phase=proxy_stopped cacheEntries=${transformer.cacheSize()} " +
                "cleanup=${if (cleanupFailure == null) "complete" else "partial_failure"}",
            cleanupFailure,
        )
    }

    private fun handleClient(client: Socket) {
        client.use { socket ->
            runCatching {
                socket.soTimeout = SocketTimeoutMillis
                val requestLine = socket.getInputStream().readAsciiLine(MaxLineBytes) ?: return
                consumeHeaders(socket.getInputStream())
                val connectTarget = ChromePhotosConnectTarget.parse(requestLine, hostAllowlist)
                if (connectTarget == null) {
                    writePlainError(socket.getOutputStream(), 502, "Host not allowed")
                    Log.i(LogTag, "decision=fail_closed scope=connect_not_allowed")
                    return
                }
                socket.getOutputStream().writeAscii("HTTP/1.1 200 Connection Established\r\n\r\n")
                socket.getOutputStream().flush()
                handleTlsTunnel(socket, connectTarget)
            }.onFailure { error ->
                failures.incrementAndGet()
                Log.w(LogTag, "phase=connection_failed error=${error.javaClass.simpleName}")
            }
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
        tlsSocket.sslParameters =
            tlsSocket.sslParameters.apply {
                applicationProtocols = arrayOf(Http11)
            }
        tlsSocket.startHandshake()
        val protocol = tlsSocket.applicationProtocol.ifBlank { Http11 }
        Log.i(
            LogTag,
            "phase=tls_ready host=${connectTarget.host} clientProtocol=$protocol " +
                "ca=${tls.caFingerprint.take(FingerprintLogLength)}",
        )

        val input = tlsSocket.inputStream
        val output = BufferedOutputStream(tlsSocket.outputStream)
        while (running.get()) {
            val requestLine = input.readAsciiLine(MaxLineBytes) ?: break
            if (requestLine.isBlank()) continue
            val closeRequested = consumeHeaders(input)
            val request = ChromePhotosProxyRequest.parse(requestLine)
            if (request == null || request.method !in ChromePhotosProxyRequest.AllowedMethods) {
                writePlainError(output, 405, "Method not allowed")
                break
            }
            if (connectTarget.host == ChromePhotosDataPlaneLabContract.FixtureHost) {
                serveFixtureRequest(request, protocol, output)
            } else {
                serveRealRequest(connectTarget.host, request, protocol, output)
            }
            if (closeRequested) break
        }
    }

    private fun serveFixtureRequest(
        request: ChromePhotosProxyRequest,
        protocol: String,
        output: OutputStream,
    ) {
        val requestStarted = System.nanoTime()
        val response = origin.responseFor(request.target)
        val transformed =
            transformer.transform(
                contentType = response.contentType,
                candidateBytes = response.originalBytes,
            )
        val decisionAt = System.nanoTime()
        requests.incrementAndGet()
        originalBytes.addAndGet(response.originalBytes.size.toLong())
        deliveredBytes.addAndGet(transformed.bytes.size.toLong())
        recordDecision(transformed)

        val headers =
            buildString {
                append("HTTP/1.1 ${response.statusCode} ${response.statusText}\r\n")
                append("Content-Type: ${response.contentType}\r\n")
                append(
                    "Content-Length: ${if (request.method == ChromePhotosProxyRequest.Head) 0 else transformed.bytes.size}\r\n",
                )
                append("Cache-Control: no-store\r\n")
                append("X-Content-Type-Options: nosniff\r\n")
                append(
                    "Content-Security-Policy: default-src 'self'; img-src 'self' " +
                        "https://${ChromePhotosRealWebLabConfig.HttpBingoHost} " +
                        "https://${ChromePhotosRealWebLabConfig.GoogleStaticHost} " +
                        "https://${ChromePhotosRealWebLabConfig.GitHubHost} " +
                        "https://${ChromePhotosRealWebLabConfig.GitHubRawHost}; " +
                        "script-src 'self'; style-src 'unsafe-inline'\r\n",
                )
                append("Connection: keep-alive\r\n\r\n")
            }
        output.writeAscii(headers)
        val firstByteAt = System.nanoTime()
        if (request.method != ChromePhotosProxyRequest.Head) output.write(transformed.bytes)
        output.flush()
        val deliveredAt = System.nanoTime()
        if (response.resourceId in FixturePresenceResourceIds) onFixtureHeartbeat()

        if (response.resourceId != FixtureHeartbeatId) {
            Log.i(
                LogTag,
                "resource=${response.resourceId} origin=fixture_local destination=chrome protocol=$protocol " +
                    "bytesIn=${response.originalBytes.size} bytesOut=${transformed.bytes.size} " +
                    "cache=${if (transformed.cacheHit) "hit" else "miss"} " +
                    "decision=${transformed.decision.name.lowercase(Locale.US)} " +
                    "requestToDecisionMs=${requestStarted.elapsedMillis(decisionAt)} " +
                    "requestToFirstByteMs=${requestStarted.elapsedMillis(firstByteAt)} " +
                    "requestToDeliveryMs=${requestStarted.elapsedMillis(deliveredAt)}",
            )
        }
    }

    private fun serveRealRequest(
        host: String,
        request: ChromePhotosProxyRequest,
        clientProtocol: String,
        output: OutputStream,
    ) {
        val requestStarted = System.nanoTime()
        runCatching {
            val upstreamResponse = upstream.execute(host, request)
            val sanitized = responseSanitizer.sanitize(request.method, upstreamResponse)
            val decisionAt = System.nanoTime()
            requests.incrementAndGet()
            originalBytes.addAndGet(upstreamResponse.body.size.toLong())
            deliveredBytes.addAndGet(sanitized.bytes.size.toLong())
            recordDecision(sanitized)
            writeSanitizedResponse(output, request.method, sanitized)
            val deliveredAt = System.nanoTime()
            Log.i(
                LogTag,
                "origin=real host=$host clientProtocol=$clientProtocol " +
                    "upstreamProtocol=${upstreamResponse.protocol} status=${sanitized.statusCode} " +
                    "contentType=${sanitized.contentType.safeLogContentType()} " +
                    "bytesIn=${upstreamResponse.body.size} bytesOut=${sanitized.bytes.size} " +
                    "cache=${if (sanitized.cacheHit) "hit" else "miss"} " +
                    "decision=${sanitized.decision.name.lowercase(Locale.US)} " +
                    "requestToDecisionMs=${requestStarted.elapsedMillis(decisionAt)} " +
                    "requestToDeliveryMs=${requestStarted.elapsedMillis(deliveredAt)}",
            )
        }.onFailure { error ->
            failures.incrementAndGet()
            writePlainError(output, 502, "Upstream unavailable")
            Log.w(LogTag, "phase=upstream_failed host=$host error=${error.javaClass.simpleName}")
        }
    }

    private fun writeSanitizedResponse(
        output: OutputStream,
        requestMethod: String,
        response: ChromePhotosSanitizedResponse,
    ) {
        val bodyLength = if (requestMethod == ChromePhotosProxyRequest.Head) 0 else response.bytes.size
        val headers = ChromePhotosClientResponseHeaderPolicy.headersFor(response, bodyLength)
        output.writeAscii("HTTP/1.1 ${response.statusCode} ${response.statusText}\r\n")
        headers.forEach { (name, value) -> output.writeAscii("$name: $value\r\n") }
        output.writeAscii("\r\n")
        if (requestMethod != ChromePhotosProxyRequest.Head) output.write(response.bytes)
        output.flush()
    }

    private fun recordDecision(result: ChromePhotosTransformResult) {
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
                    terminal = true
                    running.set(false)
                    true
                }
            }
        if (!transitioned) return
        failures.incrementAndGet()
        lifecycleLog("phase=proxy_fatal error=${error.javaClass.simpleName}", error)
        onFatalFailure(error.javaClass.simpleName)
    }

    internal fun cleanupCompleted(): Boolean = synchronized(lifecycleLock) { cleanupComplete }

    private fun consumeHeaders(input: InputStream): Boolean {
        var closeRequested = false
        repeat(MaxHeaderCount) {
            val line = input.readAsciiLine(MaxLineBytes) ?: return true
            if (line.isEmpty()) return closeRequested
            if (line.startsWith("Connection:", ignoreCase = true) && line.contains("close", ignoreCase = true)) {
                closeRequested = true
            }
        }
        error("Too many headers")
    }

    private fun InputStream.readAsciiLine(maximumBytes: Int): String? {
        val bytes = ArrayList<Byte>()
        while (bytes.size < maximumBytes) {
            val value = read()
            if (value == EndOfStream && bytes.isEmpty()) return null
            if (value == EndOfStream || value == NewLine) break
            if (value != CarriageReturn) bytes += value.toByte()
        }
        check(bytes.size < maximumBytes) { "Line too long" }
        return bytes.toByteArray().toString(StandardCharsets.US_ASCII)
    }

    private fun OutputStream.writeAscii(value: String) {
        write(value.toByteArray(StandardCharsets.US_ASCII))
    }

    private fun writePlainError(
        output: OutputStream,
        code: Int,
        message: String,
    ) {
        val body = message.toByteArray(StandardCharsets.UTF_8)
        output.writeAscii(
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
        const val SocketBacklog = 16
        const val SocketTimeoutMillis = 15_000
        const val MaxLineBytes = 8 * 1024
        const val MaxHeaderCount = 100
        const val EndOfStream = -1
        const val NewLine = 10
        const val CarriageReturn = 13
        const val Http11 = "http/1.1"
        const val FixtureHeartbeatId = "fixture-heartbeat"
        const val FingerprintLogLength = 16
        const val NanosPerMillis = 1_000_000.0
        const val LogTag = "ChromePhotosDataPlane"
        val FixturePresenceResourceIds = setOf(FixtureHeartbeatId)
    }

    private data class ProxyResources(
        val serverSocket: ServerSocket?,
        val acceptThread: Thread?,
    )
}

internal data class ChromePhotosProxyRequest(
    val method: String,
    val target: String,
) {
    companion object {
        const val Head = "HEAD"
        val AllowedMethods = setOf("GET", Head)

        fun parse(line: String): ChromePhotosProxyRequest? {
            val parts = line.trim().split(' ')
            if (parts.size != 3 || !parts[2].startsWith("HTTP/1.")) return null
            if (!parts[1].startsWith('/')) return null
            return ChromePhotosProxyRequest(parts[0].uppercase(Locale.US), parts[1])
        }
    }
}

private fun String?.safeLogContentType(): String =
    this
        ?.substringBefore(';')
        ?.lowercase(Locale.US)
        ?.filter { character -> character.isLetterOrDigit() || character in "/+.-" }
        ?.take(64)
        .orEmpty()

private fun logProxyLifecycle(
    message: String,
    error: Throwable?,
) {
    if (error == null) Log.i("ChromePhotosDataPlane", message) else Log.e("ChromePhotosDataPlane", message, error)
}

internal object ChromePhotosClientResponseHeaderPolicy {
    val invalidatedEntityHeaders: Set<String> =
        setOf(
            "content-encoding",
            "transfer-encoding",
            "etag",
            "last-modified",
            "content-md5",
            "digest",
            "content-range",
            "accept-ranges",
        )

    fun headersFor(
        response: ChromePhotosSanitizedResponse,
        bodyLength: Int,
    ): Map<String, String> =
        linkedMapOf<String, String>().apply {
            response.contentType?.let { put("Content-Type", it) }
            response.location?.let { put("Location", it) }
            put("Content-Length", bodyLength.toString())
            put("Cache-Control", "no-store")
            put("X-Content-Type-Options", "nosniff")
            put("Connection", "keep-alive")
        }
}
