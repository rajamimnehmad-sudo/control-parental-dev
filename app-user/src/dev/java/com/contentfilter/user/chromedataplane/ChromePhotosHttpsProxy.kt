package com.contentfilter.user.chromedataplane

import android.util.Log
import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.SequenceInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLSocket

internal class ChromePhotosHttpsProxy(
    private val tls: ChromePhotosEphemeralTlsMaterial,
    private val origin: ChromePhotosFixtureSource,
    private val onFixtureHeartbeat: () -> Unit,
    private val onFatalFailure: (String) -> Unit,
    private val destinationAuthority: ChromePublicDestinationAuthority = ChromePublicDestinationAuthority(),
    private val upstream: ChromePhotosUpstream = ChromePhotosRealUpstream(destinationAuthority = destinationAuthority),
    private val transformer: ChromePhotosResourceTransformer = chromePhotosDeterministicTransformer(origin),
    private val imageAuthority: ChromeImageContentAuthority = ChromeImageContentAuthority(),
    private val visualDeliveryGate: ChromeNetworkVisualDeliveryGate =
        ChromeNetworkVisualDeliveryGate(replacementPlaceholderBytes = origin.placeholderImageBytes),
    private val coverageLedger: ChromeRealWebProvenanceLedger? = null,
    private val documentAuthority: ChromeMediaShieldDocumentAuthority? = null,
    private val readyEndpoint: ChromeMediaShieldReadyEndpoint? = null,
    private val originalUiSvgAuthority: ChromeOriginalUiSvgAuthority? = null,
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
            visualDeliveryGate = visualDeliveryGate,
        )
    private val requestReader = ChromeHttp1RequestReader()
    private val responseWriter = ChromeHttp1ResponseWriter()
    private val running = AtomicBoolean(false)
    private val lifecycleLock = Any()
    private var terminal = false
    private var cleanupComplete = false
    private val admission =
        ChromeProxyAdmission(
            workerCount = WorkerCount,
            queueCapacity = WorkerQueueCapacity,
        )
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private val connectionIds = AtomicLong()
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
            preRenderShieldReport = origin.preRenderShieldReport(),
            mediaShieldReport = origin.mediaShieldReport(),
            decisionSession = transformer.decisionMetrics(),
            imageAuthority = imageAuthority.metrics(),
            networkVisualDelivery = visualDeliveryGate.snapshot(),
            mediaShieldDocuments = documentAuthority?.metrics() ?: ChromeMediaShieldDocumentMetrics(),
            mediaShieldReady = readyEndpoint?.metrics() ?: ChromeMediaShieldReadyEndpointMetrics(),
            originalUiSvg = originalUiSvgAuthority?.metrics() ?: ChromeOriginalUiSvgMetrics(),
        )
    }

    fun isHealthy(): Boolean = running.get() && serverSocket?.isClosed == false && !admission.isShutdown()

    fun markCoverageState(
        label: String,
        newNavigation: Boolean,
    ): Long? = coverageLedger?.markState(label, newNavigation)

    fun coverageSnapshot(): ChromeCoverageLedgerSnapshot? = coverageLedger?.snapshot()

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
        admission.close()
        resources.acceptThread?.interrupt()
        val cleanupFailure =
            listOf(
                runCatching { transformer.close() },
                runCatching { originalUiSvgAuthority?.close() },
                runCatching { coverageLedger?.close() },
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
                val connectionId = connectionIds.incrementAndGet()
                val correlationId = "c$connectionId"
                connections.incrementAndGet()
                when (
                    admission.dispatch(
                        onDiscard = { runCatching { client.close() } },
                        block = { handleClient(client, correlationId) },
                    )
                ) {
                    ChromeProxyAdmissionResult.Accepted -> Unit
                    ChromeProxyAdmissionResult.Closed -> {
                        if (running.get()) fatal(IllegalStateException("Proxy admission closed while running"))
                        return
                    }
                    ChromeProxyAdmissionResult.Rejected -> {
                        if (!running.get()) return
                        queueRejected.incrementAndGet()
                        warningLog(
                            "phase=connection_rejected reason=admission_invariant correlationId=$correlationId",
                        )
                        fatal(IllegalStateException("Proxy admission rejected while running"))
                        return
                    }
                }
            }
        } catch (error: SocketException) {
            if (running.get()) fatal(error)
        } catch (error: Throwable) {
            fatal(error)
        }
    }

    private fun handleClient(
        client: Socket,
        correlationId: String,
    ) {
        val active = activeConnections.incrementAndGet()
        activeConnectionsPeak.accumulateAndGet(active, ::maxOf)
        var connectTarget: ChromePhotosConnectTarget? = null
        try {
            client.use { socket ->
                socket.soTimeout = SocketTimeoutMillis
                val requestLine = socket.getInputStream().readChromeConnectLine(MaxLineBytes) ?: return
                if (!requestLine.startsWith("CONNECT ")) {
                    val prefixedInput =
                        SequenceInputStream(
                            ByteArrayInputStream("$requestLine\r\n".toByteArray(Charsets.US_ASCII)),
                            socket.getInputStream(),
                        )
                    handleAbsoluteHttp11Session(
                        input = prefixedInput,
                        output = BufferedOutputStream(socket.getOutputStream()),
                        connectionCorrelationId = correlationId,
                    )
                    return
                }
                consumeChromeConnectHeaders(
                    input = socket.getInputStream(),
                    maximumHeaderCount = MaxConnectHeaderCount,
                    maximumHeaderBytes = MaxConnectHeaderBytes,
                    maximumLineBytes = MaxLineBytes,
                )
                connectTarget = destinationAuthority.admitConnect(requestLine)
                val admittedTarget = connectTarget
                if (admittedTarget == null) {
                    writePlainError(socket.getOutputStream(), 502, "Destination unavailable")
                    infoLog("decision=fail_closed scope=connect_not_public correlationId=$correlationId")
                    return
                }
                ChromeHttp1Wire.writeAscii(socket.getOutputStream(), "HTTP/1.1 200 Connection Established\r\n\r\n")
                socket.getOutputStream().flush()
                handleTlsTunnel(socket, admittedTarget, correlationId)
            }
        } catch (error: Throwable) {
            failures.incrementAndGet()
            val target = connectTarget
            val tlsFailure = ChromeProxyTlsDiagnostics.classify(error)
            if (target != null && tlsFailure != null) {
                val stage = (error as? ChromeProxyTlsStageException)?.stage ?: TlsSessionStage
                warningLog(
                    tlsFailure.logLine(
                        ChromeProxyTlsContext(
                            side = ChromeProxyTlsSide.Client,
                            stage = stage,
                            correlationId = correlationId,
                            host = target.host,
                            authority = "${target.host}:${target.port}",
                            sni = null,
                        ),
                    ),
                )
            } else {
                warningLog(
                    "phase=connection_failed correlationId=$correlationId error=${error.javaClass.simpleName}",
                )
            }
        } finally {
            activeConnections.decrementAndGet()
        }
    }

    private fun handleTlsTunnel(
        client: Socket,
        connectTarget: ChromePhotosConnectTarget,
        correlationId: String,
    ) {
        val serverMaterial = tls.serverMaterialFor(connectTarget.host)
        val tlsSocket =
            try {
                serverMaterial.sslContext.socketFactory.createSocket(
                    client,
                    connectTarget.host,
                    HttpsPort,
                    false,
                ) as SSLSocket
            } catch (error: Throwable) {
                throw ChromeProxyTlsStageException(TlsServerSocketStage, error)
            }
        tlsSocket.use { secureSocket ->
            secureSocket.useClientMode = false
            secureSocket.sslParameters = secureSocket.sslParameters.apply { applicationProtocols = arrayOf(Http11) }
            try {
                secureSocket.startHandshake()
            } catch (error: Throwable) {
                throw ChromeProxyTlsStageException(TlsHandshakeStage, error)
            }
            val protocol = secureSocket.applicationProtocol.ifBlank { Http11 }
            infoLog(
                "phase=tls_ready correlationId=$correlationId " +
                    "hostClass=${ChromeProxyLogPrivacy.hostClass(connectTarget.host)} " +
                    "hostHash=${ChromeProxyLogPrivacy.digest(connectTarget.host)} clientProtocol=$protocol " +
                    "ca=${tls.caFingerprint.take(FingerprintLogLength)}",
            )
            handleHttp11Session(
                input = secureSocket.inputStream,
                output = BufferedOutputStream(secureSocket.outputStream),
                connectTargetHost = connectTarget.host,
                protocol = protocol,
                connectionCorrelationId = correlationId,
            )
        }
    }

    internal fun handleHttp11Session(
        input: InputStream,
        output: OutputStream,
        connectTargetHost: String,
        protocol: String,
        shouldContinue: () -> Boolean = { running.get() },
        connectionCorrelationId: String = StandaloneConnectionId,
    ) {
        var requestNumber = 0L
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
            val requestCorrelationId = "$connectionCorrelationId-r${++requestNumber}"
            if (request.absoluteHttpTargetOrNull() != null) {
                writePlainError(output, 400, "Absolute target forbidden in tunnel")
                break
            }
            if (!request.authorityMatches(connectTargetHost)) {
                writePlainError(output, 400, "Host mismatch")
                break
            }
            if (request.hasUpgrade()) {
                writePlainError(output, 501, "Upgrade unsupported")
                infoLog("decision=fail_closed scope=upgrade_unsupported correlationId=$requestCorrelationId")
                break
            }
            val disposition =
                if (connectTargetHost == ChromePhotosDataPlaneLabContract.OriginalUiSvgHost) {
                    serveOriginalUiSvgAsset(request, output)
                } else if (connectTargetHost == ChromePhotosDataPlaneLabContract.FixtureHost) {
                    serveFixtureRequest(request, protocol, output, requestCorrelationId)
                } else {
                    serveRealRequest(
                        host = connectTargetHost,
                        request = request,
                        clientProtocol = protocol,
                        output = output,
                        correlationId = requestCorrelationId,
                    )
                }
            if (disposition == ChromeHttpConnectionDisposition.Close) break
        }
    }

    internal fun handleAbsoluteHttp11Session(
        input: InputStream,
        output: OutputStream,
        shouldContinue: () -> Boolean = { running.get() },
        connectionCorrelationId: String = StandaloneConnectionId,
    ) {
        var requestNumber = 0L
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
            val correlationId = "$connectionCorrelationId-r${++requestNumber}"
            val target = request.absoluteHttpTargetOrNull()
            if (
                target == null ||
                request.headerValues("Host").size != 1 ||
                !request.authorityMatches(target.host, ChromeHttpAbsoluteTarget.Port)
            ) {
                writePlainError(output, 400, "Invalid absolute HTTP authority")
                break
            }
            if (request.hasUpgrade()) {
                writePlainError(output, 501, "Upgrade unsupported")
                infoLog("decision=fail_closed scope=upgrade_unsupported correlationId=$correlationId")
                break
            }
            if (target.host != ChromePhotosDataPlaneLabContract.FixtureHost) {
                val admitted = runCatching { destinationAuthority.resolvePublic(target.host) }.getOrNull()
                if (admitted.isNullOrEmpty()) {
                    writePlainError(output, 502, "Destination unavailable")
                    infoLog("decision=fail_closed scope=http_not_public correlationId=$correlationId")
                    break
                }
            }
            val originRequest = request.copy(target = target.originForm)
            val disposition =
                if (target.host == ChromePhotosDataPlaneLabContract.FixtureHost) {
                    serveFixtureRequest(originRequest, Http11, output, correlationId)
                } else {
                    serveRealRequest(
                        host = target.host,
                        request = originRequest,
                        clientProtocol = Http11,
                        output = output,
                        correlationId = correlationId,
                        endpoint =
                            ChromePhotosUpstreamEndpoint(
                                scheme = ChromePhotosUpstreamScheme.Http,
                                host = target.host,
                                port = ChromeHttpAbsoluteTarget.Port,
                            ),
                    )
                }
            if (disposition == ChromeHttpConnectionDisposition.Close) break
        }
    }

    private fun serveFixtureRequest(
        request: ChromePhotosProxyRequest,
        protocol: String,
        output: OutputStream,
        correlationId: String,
    ): ChromeHttpConnectionDisposition {
        var responseStarted = false
        val coverageToken =
            if (
                request.target == ChromePhotosDataPlaneLabContract.MediaShieldReadyPath ||
                request.target == ChromePhotosDataPlaneLabContract.MediaShieldSelfReadyPath ||
                request.target == ChromePhotosDataPlaneLabContract.MediaShieldSelfShieldTracePath ||
                request.target == ChromePhotosDataPlaneLabContract.MediaShieldBootstrapDiagnosticPath ||
                request.target == ChromePhotosDataPlaneLabContract.MediaShieldRendererMetricsPath ||
                request.target == ChromePhotosDataPlaneLabContract.OriginalUiSvgRewritePath ||
                request.target == ChromePhotosDataPlaneLabContract.MediaShieldParserBarrierPath
            ) {
                null
            } else {
                coverageLedger?.beginRequest(
                    host = ChromePhotosDataPlaneLabContract.FixtureHost,
                    request = request,
                    correlationId = correlationId,
                )
            }
        return try {
            val started = System.nanoTime()
            readyEndpoint?.handle(request)?.let { readyResponse ->
                requests.incrementAndGet()
                responseStarted = true
                val result = responseWriter.writeBuffered(output, request, readyResponse)
                deliveredBytes.addAndGet(result.bytesWritten)
                latencies.add(System.nanoTime() - started)
                infoLog(
                    "phase=media_shield_ready result=${readyResponse.statusCode} " +
                        "bytesOut=${result.bytesWritten}",
                )
                return request.successDisposition()
            }
            val normalizedRequest = normalizeUpstreamRequest(request)
            val response = origin.responseFor(normalizedRequest)
            val fixtureUpstream = response.asUpstreamResponse()
            val documentResult =
                documentAuthority?.processBuffered(
                    request = normalizedRequest,
                    response = fixtureUpstream,
                    bytes = response.originalBytes,
                )
            if (documentResult != null) {
                requests.incrementAndGet()
                originalBytes.addAndGet(response.originalBytes.size.toLong())
                responseStarted = true
                val sanitized = documentResult.asSanitizedResponse()
                val result = responseWriter.writeBuffered(output, request, sanitized, forceChunked = false)
                deliveredBytes.addAndGet(result.bytesWritten)
                latencies.add(System.nanoTime() - started)
                if (response.resourceId in FixturePresenceResourceIds) onFixtureHeartbeat()
                infoLog(
                    "phase=media_shield_document origin=fixture resource=${response.resourceId} " +
                        "result=${documentResult.logValue()} bytesOut=${result.bytesWritten}",
                )
                return request.successDisposition()
            }
            originalUiSvgAuthority?.processStylesheet(normalizedRequest, fixtureUpstream)?.let { sanitized ->
                requests.incrementAndGet()
                originalBytes.addAndGet(response.originalBytes.size.toLong())
                responseStarted = true
                val result = responseWriter.writeBuffered(output, request, sanitized)
                deliveredBytes.addAndGet(result.bytesWritten)
                passthroughResponses.incrementAndGet()
                latencies.add(System.nanoTime() - started)
                return request.successDisposition()
            }
            originalUiSvgAuthority?.processNetworkSvg(normalizedRequest, fixtureUpstream)?.let { sanitized ->
                requests.incrementAndGet()
                originalBytes.addAndGet(sanitized.inputBytes.toLong())
                responseStarted = true
                val result = responseWriter.writeBuffered(output, request, sanitized)
                deliveredBytes.addAndGet(result.bytesWritten)
                recordDecision(sanitized)
                latencies.add(System.nanoTime() - started)
                return request.successDisposition()
            }
            val inspection = imageAuthority.inspectBuffered(request, fixtureUpstream, response.originalBytes)
            val sanitized =
                when (inspection) {
                    is ChromeImageContentInspection.Candidate ->
                        responseSanitizer.sanitizeCandidate(request.method, inspection)
                    is ChromeImageContentInspection.Passthrough ->
                        response.asPassthroughSanitizedResponse(inspection.response)
                }
            if (
                inspection is ChromeImageContentInspection.Candidate &&
                !visualDeliveryGate.isCandidateDeliveryAuthorized(sanitized)
            ) {
                error("candidate_wire_authority_rejected")
            }
            requests.incrementAndGet()
            originalBytes.addAndGet(response.originalBytes.size.toLong())
            responseStarted = true
            val result = responseWriter.writeBuffered(output, request, sanitized, forceChunked = response.chunked)
            deliveredBytes.addAndGet(result.bytesWritten)
            if (inspection is ChromeImageContentInspection.Candidate) {
                visualDeliveryGate.recordCandidateDelivery(sanitized)
            }
            recordDecision(sanitized)
            if (coverageToken != null) {
                if (response.statusCode in RedirectCodes) {
                    coverageLedger?.recordRedirect(coverageToken, response.statusCode, sanitized.location)
                } else if (
                    sanitized.decision != ChromePhotosResourceDecision.Passthrough ||
                    sanitized.observedBodyDigest != null
                ) {
                    coverageLedger?.recordInspected(
                        token = coverageToken,
                        statusCode = response.statusCode,
                        declaredContentType = response.contentType,
                        response = sanitized,
                    )
                }
            }
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
            if (coverageToken != null) coverageLedger?.recordFailure(coverageToken, error.javaClass.simpleName)
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
        correlationId: String = StandaloneRequestId,
        endpoint: ChromePhotosUpstreamEndpoint =
            ChromePhotosUpstreamEndpoint(
                scheme = ChromePhotosUpstreamScheme.Https,
                host = host,
                port = HttpsPort,
            ),
    ): ChromeHttpConnectionDisposition {
        val started = System.nanoTime()
        var responseStarted = false
        var upstreamExchangeReady = false
        readyEndpoint?.handle(request)?.let { readyResponse ->
            requests.incrementAndGet()
            responseStarted = true
            val result = responseWriter.writeBuffered(output, request, readyResponse)
            deliveredBytes.addAndGet(result.bytesWritten)
            infoLog(
                "phase=media_shield_ready origin=same_origin result=${readyResponse.statusCode} bytesOut=${result.bytesWritten}",
            )
            return request.successDisposition()
        }
        val coverageToken = coverageLedger?.beginRequest(host, request, correlationId)
        return try {
            val upstreamRequest = normalizeUpstreamRequest(request)
            require(endpoint.host == host)
            upstream.execute(endpoint, upstreamRequest).use { exchange ->
                upstreamExchangeReady = true
                val response = exchange.response
                requests.incrementAndGet()
                if (response.statusCode in RedirectCodes) {
                    val sanitized = responseSanitizer.sanitizeRedirect(response)
                    responseStarted = true
                    val result = responseWriter.writeBuffered(output, request, sanitized)
                    deliveredBytes.addAndGet(result.bytesWritten)
                    recordDecision(sanitized)
                    if (coverageToken != null) {
                        coverageLedger.recordRedirect(coverageToken, response.statusCode, sanitized.location)
                    }
                    logRealResponse(host, request, clientProtocol, response, sanitized, result, started)
                    latencies.add(System.nanoTime() - started)
                    return request.successDisposition()
                }
                if (documentAuthority?.requiresBufferedDecision(upstreamRequest, response) == true) {
                    val bounded = response.body.readBounded(MaximumDocumentBytes)
                    val documentResult =
                        checkNotNull(
                            documentAuthority.processBuffered(
                                request = upstreamRequest,
                                response = response,
                                bytes = bounded.bytes,
                                bodyExceeded = bounded.exceeded,
                            ),
                        )
                    responseStarted = true
                    val sanitized = documentResult.asSanitizedResponse()
                    val result = responseWriter.writeBuffered(output, request, sanitized)
                    originalBytes.addAndGet(if (bounded.exceeded) 0L else bounded.bytes.size.toLong())
                    deliveredBytes.addAndGet(result.bytesWritten)
                    infoLog(
                        "phase=media_shield_document origin=real " +
                            "hostClass=${ChromeProxyLogPrivacy.hostClass(host)} " +
                            "hostHash=${ChromeProxyLogPrivacy.digest(host)} result=${documentResult.logValue()} " +
                            "bytesOut=${result.bytesWritten}",
                    )
                    latencies.add(System.nanoTime() - started)
                    return request.successDisposition()
                }
                originalUiSvgAuthority?.processStylesheet(upstreamRequest, response)?.let { sanitized ->
                    responseStarted = true
                    val result = responseWriter.writeBuffered(output, request, sanitized)
                    originalBytes.addAndGet(sanitized.inputBytes.toLong())
                    deliveredBytes.addAndGet(result.bytesWritten)
                    passthroughResponses.incrementAndGet()
                    latencies.add(System.nanoTime() - started)
                    return request.successDisposition()
                }
                originalUiSvgAuthority?.processNetworkSvg(upstreamRequest, response)?.let { sanitized ->
                    responseStarted = true
                    val result = responseWriter.writeBuffered(output, request, sanitized)
                    originalBytes.addAndGet(sanitized.inputBytes.toLong())
                    deliveredBytes.addAndGet(result.bytesWritten)
                    recordDecision(sanitized)
                    latencies.add(System.nanoTime() - started)
                    return request.successDisposition()
                }
                val inspection = imageAuthority.inspect(request, response)
                if (inspection is ChromeImageContentInspection.Candidate) {
                    val sanitized = responseSanitizer.sanitizeCandidate(request.method, inspection)
                    if (!visualDeliveryGate.isCandidateDeliveryAuthorized(sanitized)) {
                        error("candidate_wire_authority_rejected")
                    }
                    responseStarted = true
                    val result = responseWriter.writeBuffered(output, request, sanitized)
                    originalBytes.addAndGet(sanitized.inputBytes.toLong())
                    deliveredBytes.addAndGet(result.bytesWritten)
                    visualDeliveryGate.recordCandidateDelivery(sanitized)
                    recordDecision(sanitized)
                    if (coverageToken != null) {
                        coverageLedger.recordInspected(
                            token = coverageToken,
                            statusCode = response.statusCode,
                            declaredContentType = response.headers.firstValue("Content-Type"),
                            response = sanitized,
                        )
                    }
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
                        "origin=real hostClass=${ChromeProxyLogPrivacy.hostClass(host)} " +
                            "hostHash=${ChromeProxyLogPrivacy.digest(host)} method=${request.method} " +
                            "clientProtocol=$clientProtocol " +
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
            if (coverageToken != null) coverageLedger.recordFailure(coverageToken, error.javaClass.simpleName)
            val errorResponseWritten =
                !responseStarted &&
                    runCatching { writePlainError(output, 502, "Upstream unavailable") }.isSuccess
            val tlsFailure = ChromeProxyTlsDiagnostics.classify(error)
            if (tlsFailure != null) {
                val stage =
                    when {
                        tlsFailure.isHandshake -> TlsHandshakeStage
                        upstreamExchangeReady -> TlsResponseStreamStage
                        else -> TlsConnectStage
                    }
                warningLog(
                    tlsFailure.logLine(
                        ChromeProxyTlsContext(
                            side = ChromeProxyTlsSide.Upstream,
                            stage = stage,
                            correlationId = correlationId,
                            host = host,
                            authority = "$host:$HttpsPort",
                            sni = host,
                        ),
                    ) +
                        " responseStarted=$responseStarted errorResponseWritten=$errorResponseWritten",
                )
            } else {
                warningLog(
                    "phase=upstream_failed correlationId=$correlationId " +
                        "hostClass=${ChromeProxyLogPrivacy.hostClass(host)} " +
                        "hostHash=${ChromeProxyLogPrivacy.digest(host)} responseStarted=$responseStarted " +
                        "errorResponseWritten=$errorResponseWritten error=${error.javaClass.simpleName}",
                )
            }
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
            "origin=real hostClass=${ChromeProxyLogPrivacy.hostClass(host)} " +
                "hostHash=${ChromeProxyLogPrivacy.digest(host)} method=${request.method} clientProtocol=$clientProtocol " +
                "upstreamProtocol=${response.protocol} status=${sanitized.statusCode} " +
                "contentType=${sanitized.contentType.safeLogContentType()} bytesIn=${sanitized.inputBytes} " +
                "bytesOut=${result.bytesWritten} cache=${if (sanitized.cacheHit) "hit" else "miss"} " +
                "decision=${sanitized.decision.name.lowercase(Locale.US)} ${sanitized.decisionResult.logFields()} " +
                "requestToDeliveryMs=${started.elapsedMillis(System.nanoTime())}",
        )
    }

    private fun normalizeUpstreamRequest(request: ChromePhotosProxyRequest): ChromePhotosProxyRequest {
        val documentNormalized = documentAuthority?.normalizeUpstreamRequest(request) ?: request
        val svgNormalized = originalUiSvgAuthority?.normalizeUpstreamRequest(documentNormalized) ?: documentNormalized
        return imageAuthority.normalizeUpstreamRequest(svgNormalized)
    }

    private fun serveOriginalUiSvgAsset(
        request: ChromePhotosProxyRequest,
        output: OutputStream,
    ): ChromeHttpConnectionDisposition {
        requests.incrementAndGet()
        val response = originalUiSvgAuthority?.serveAsset(request)
        if (response == null) {
            writePlainError(output, 404, "Not Found")
            return ChromeHttpConnectionDisposition.Close
        }
        val result = responseWriter.writeBuffered(output, request, response)
        deliveredBytes.addAndGet(result.bytesWritten)
        passthroughResponses.incrementAndGet()
        return request.successDisposition()
    }

    private fun recordDecision(result: ChromePhotosSanitizedResponse) {
        when (result.decision) {
            ChromePhotosResourceDecision.Safe -> safeDecisions.incrementAndGet()
            ChromePhotosResourceDecision.Block -> blockedDecisions.incrementAndGet()
            ChromePhotosResourceDecision.Unknown -> unknownDecisions.incrementAndGet()
            ChromePhotosResourceDecision.Passthrough -> passthroughResponses.incrementAndGet()
            ChromePhotosResourceDecision.AuditReplaced -> Unit
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

    private fun writePlainError(
        output: OutputStream,
        code: Int,
        message: String,
    ) = writeChromeProxyPlainError(output, code, message)

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
        const val MaximumDocumentBytes = 2 * 1024 * 1024
        const val LogTag = "ChromePhotosDataPlane"
        const val StandaloneConnectionId = "standalone"
        const val StandaloneRequestId = "standalone-r1"
        const val TlsServerSocketStage = "server_socket"
        const val TlsHandshakeStage = "handshake"
        const val TlsSessionStage = "session"
        const val TlsConnectStage = "connect_tls"
        const val TlsResponseStreamStage = "response_stream"
        val FixturePresenceResourceIds = setOf(FixtureHeartbeatId)
        val RedirectCodes = setOf(301, 302, 303, 307, 308)
    }

    private data class ProxyResources(
        val serverSocket: ServerSocket?,
        val acceptThread: Thread?,
    )
}

private class ChromeProxyTlsStageException(
    val stage: String,
    cause: Throwable,
) : Exception(cause)
