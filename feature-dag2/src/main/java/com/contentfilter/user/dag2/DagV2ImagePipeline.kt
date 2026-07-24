package com.contentfilter.user.dag2

import android.webkit.WebResourceResponse
import okhttp3.Call
import okhttp3.Dispatcher
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class DagV2ImageDecision {
    Hide,
}

interface DagV2ImageDecisionEngine {
    fun decideRaster(): DagV2ImageDecision
}

@Singleton
class HideAllPendingModel
    @Inject
    constructor() : DagV2ImageDecisionEngine {
        override fun decideRaster(): DagV2ImageDecision = DagV2ImageDecision.Hide
    }

@Singleton
class DagV2NeutralImageFactory
    @Inject
    constructor() {
        fun create(): WebResourceResponse =
            WebResourceResponse(
                "image/svg+xml",
                "utf-8",
                200,
                "OK",
                SafeImageHeaders,
                ByteArrayInputStream(NeutralSvg),
            )

        fun bytesForTest(): ByteArray = NeutralSvg.copyOf()

        private companion object {
            val SafeImageHeaders =
                mapOf(
                    "Cache-Control" to "no-store, max-age=0",
                    "Content-Security-Policy" to "default-src 'none'; style-src 'none'; sandbox",
                    "X-Content-Type-Options" to "nosniff",
                )
            val NeutralSvg =
                """
                <svg xmlns="http://www.w3.org/2000/svg" width="640" height="480" viewBox="0 0 640 480">
                  <rect width="640" height="480" fill="#E9EDF2"/>
                </svg>
                """.trimIndent().encodeToByteArray()
        }
    }

object DagV2SafeSvgValidator {
    const val MaximumSvgBytes = 256 * 1024

    fun isSafe(bytes: ByteArray): Boolean {
        if (bytes.isEmpty() || bytes.size > MaximumSvgBytes) return false
        val source = runCatching { bytes.decodeToString() }.getOrNull()?.trim() ?: return false
        val normalized = source.lowercase()
        if (!normalized.startsWith("<svg") && !normalized.startsWith("<?xml")) return false
        if (!normalized.contains("<svg")) return false
        return ForbiddenFragments.none(normalized::contains) &&
            EventHandler.find(normalized) == null &&
            ExternalReference.find(normalized) == null
    }

    private val ForbiddenFragments =
        setOf(
            "<script",
            "<foreignobject",
            "<image",
            "<iframe",
            "<object",
            "<embed",
            "<audio",
            "<video",
            "<canvas",
            "<animate",
            "<animatemotion",
            "<animatetransform",
            "<set",
            "<use",
            "<!doctype",
            "<!entity",
            "@import",
            "url(",
            "javascript:",
            "data:",
            "blob:",
        )
    private val EventHandler = Regex("""\son[a-z0-9_-]+\s*=""")
    private val ExternalReference = Regex("""\s(?:href|xlink:href)\s*=""")
}

@Singleton
class DagV2ImagePipeline
    @Inject
    constructor(
        private val decisionEngine: HideAllPendingModel,
        private val neutralImageFactory: DagV2NeutralImageFactory,
        private val sessions: DagV2DocumentSession,
        private val metrics: DagV2Metrics,
    ) {
        private val dispatcher =
            Dispatcher().apply {
                maxRequests = MaximumGlobalVisualDownloads
                maxRequestsPerHost = MaximumVisualDownloadsPerHost
            }
        private val client =
            OkHttpClient
                .Builder()
                .dispatcher(dispatcher)
                .dns(PublicOnlyDns)
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(NetworkTimeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(NetworkTimeoutSeconds, TimeUnit.SECONDS)
                .callTimeout(NetworkTimeoutSeconds, TimeUnit.SECONDS)
                .build()

        fun intercept(
            request: DagV2ResourceRequest,
            kind: DagV2ResourceKind,
        ): WebResourceResponse {
            val session = sessions.snapshot()
            metrics.imageRequest(request.source, session)
            if (session == null || !sessions.isCurrent(session.sessionId, session.navigationToken)) {
                metrics.imageCancelled()
                return placeholder(session)
            }
            if (kind != DagV2ResourceKind.SvgImage) {
                check(decisionEngine.decideRaster() == DagV2ImageDecision.Hide)
                return if (stillCurrent(session)) placeholder(session) else cancelledPlaceholder(session)
            }
            return loadValidatedSvg(request, session)
        }

        fun cancelBefore(activeNavigationToken: String) {
            dispatcher.queuedCalls().cancelIfStale(activeNavigationToken)
            dispatcher.runningCalls().cancelIfStale(activeNavigationToken)
        }

        fun shutdown() {
            dispatcher.cancelAll()
        }

        fun cancelAll() {
            dispatcher.cancelAll()
        }

        private fun loadValidatedSvg(
            resource: DagV2ResourceRequest,
            session: DagV2DocumentSessionState,
        ): WebResourceResponse {
            if (!stillCurrent(session)) return cancelledPlaceholder(session)
            val request =
                runCatching {
                    Request
                        .Builder()
                        .url(resource.url)
                        .header("Accept", "image/svg+xml")
                        .get()
                        .tag(String::class.java, session.navigationToken)
                        .build()
                }.getOrElse { return placeholder(session) }
            val result =
                runCatching {
                    client.newCall(request).execute().use { response ->
                        if (!stillCurrent(session)) return@use null
                        if (!response.isSuccessful || response.isRedirect) return@use null
                        val contentType = response.header("Content-Type").orEmpty().substringBefore(';').trim()
                        if (contentType != "image/svg+xml") return@use null
                        val declaredLength = response.body?.contentLength() ?: -1L
                        if (declaredLength > DagV2SafeSvgValidator.MaximumSvgBytes) return@use null
                        val bytes =
                            response.body
                                ?.byteStream()
                                ?.use {
                                    it.readDagV2Bounded(DagV2SafeSvgValidator.MaximumSvgBytes) {
                                        stillCurrent(session)
                                    }
                                }
                                ?: return@use null
                        if (!stillCurrent(session)) return@use null
                        bytes.takeIf(DagV2SafeSvgValidator::isSafe)
                    }
                }.getOrNull()
            if (result == null || !stillCurrent(session)) {
                return if (stillCurrent(session)) placeholder(session) else cancelledPlaceholder(session)
            }
            if (!stillCurrent(session)) return cancelledPlaceholder(session)
            metrics.event("image_svg_safe", session)
            if (!stillCurrent(session)) return cancelledPlaceholder(session)
            metrics.imageCompleted(session)
            if (!stillCurrent(session)) return cancelledPlaceholder(session)
            return WebResourceResponse(
                "image/svg+xml",
                "utf-8",
                200,
                "OK",
                mapOf(
                    "Cache-Control" to "no-store, max-age=0",
                    "Content-Security-Policy" to "default-src 'none'; style-src 'unsafe-inline'; sandbox",
                    "X-Content-Type-Options" to "nosniff",
                ),
                ByteArrayInputStream(result),
            )
        }

        private fun stillCurrent(session: DagV2DocumentSessionState): Boolean =
            sessions.isCurrent(session.sessionId, session.navigationToken)

        private fun placeholder(session: DagV2DocumentSessionState?): WebResourceResponse {
            metrics.imagePlaceholder(session)
            return neutralImageFactory.create()
        }

        private fun cancelledPlaceholder(session: DagV2DocumentSessionState?): WebResourceResponse {
            metrics.imageCancelled()
            return placeholder(session)
        }

        private fun List<Call>.cancelIfStale(activeNavigationToken: String) {
            filter { it.request().tag(String::class.java) != activeNavigationToken }.forEach(Call::cancel)
        }

        private fun InputStream.readDagV2Bounded(
            maximumBytes: Int,
            isCurrent: () -> Boolean,
        ): ByteArray? {
            val output = ByteArrayOutputStream(minOf(maximumBytes, InitialSvgBufferBytes))
            val buffer = ByteArray(SvgReadBufferBytes)
            var total = 0
            while (true) {
                if (!isCurrent()) return null
                val read = read(buffer)
                if (read < 0) break
                total += read
                if (total > maximumBytes) return null
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        }

        private object PublicOnlyDns : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val addresses = Dns.SYSTEM.lookup(hostname)
                if (
                    addresses.isEmpty() ||
                    addresses.any { !with(DagV2NetworkGuard.Companion) { it.isPublicDagV2Address() } }
                ) {
                    throw UnknownHostException("Private or reserved destination rejected.")
                }
                return addresses
            }
        }

        companion object {
            const val MaximumGlobalVisualDownloads = 4
            const val MaximumVisualDownloadsPerHost = 2
            const val NetworkTimeoutSeconds = 10L
            const val InitialSvgBufferBytes = 16 * 1024
            const val SvgReadBufferBytes = 8 * 1024
        }
    }
