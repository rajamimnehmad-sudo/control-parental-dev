package com.contentfilter.user.dag2

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DagV2ResourceRouter
    @Inject
    constructor(
        private val imagePipeline: DagV2ImagePipeline,
        private val metrics: DagV2Metrics,
        private val sessions: DagV2DocumentSession,
    ) {
        fun intercept(
            request: WebResourceRequest,
            source: DagV2ResourceSource,
        ): WebResourceResponse? {
            val session = sessions.snapshot()?.takeUnless { it.cancelled }
            return intercept(
                DagV2ResourceRequest(
                    url = request.url.toString(),
                    headers = request.requestHeaders.orEmpty(),
                    isForMainFrame = request.isForMainFrame,
                    source = source,
                    sessionId = session?.sessionId,
                    navigationToken = session?.navigationToken,
                ),
            )
        }

        fun intercept(request: DagV2ResourceRequest): WebResourceResponse? {
            if (request.source == DagV2ResourceSource.ServiceWorker) {
                if (
                    request.sessionId == null ||
                    request.navigationToken == null ||
                    !sessions.isCurrent(request.sessionId, request.navigationToken)
                ) {
                    return null
                }
                metrics.serviceWorkerRequest()
            }
            val kind = classify(request)
            return when (route(kind, request.url)) {
                DagV2ResourceRoute.Bypass -> {
                    metrics.nonImageBypass()
                    null
                }
                DagV2ResourceRoute.VisualPipeline -> imagePipeline.intercept(request, kind)
                DagV2ResourceRoute.Block -> blockedResponse()
            }
        }

        fun onNewDocument(session: DagV2DocumentSessionState) {
            metrics.beginDocument(session)
            imagePipeline.cancelBefore(session.navigationToken)
        }

        fun close() {
            imagePipeline.shutdown()
        }

        fun cancelVisualRequests() {
            imagePipeline.cancelAll()
        }

        fun classify(request: DagV2ResourceRequest): DagV2ResourceKind {
            val destination = request.header("Sec-Fetch-Dest").lowercase(Locale.ROOT)
            val accept = request.header("Accept").lowercase(Locale.ROOT)
            val extension =
                runCatching { URI(request.url).path.orEmpty().substringAfterLast('.', "").lowercase() }
                    .getOrDefault("")

            if (destination == "image" || accept.contains("image/")) {
                return if (extension == "svg") {
                    DagV2ResourceKind.SvgImage
                } else {
                    DagV2ResourceKind.RasterImage
                }
            }
            if (destination in setOf("video", "audio") || accept.startsWith("video/") || accept.startsWith("audio/")) {
                return DagV2ResourceKind.Media
            }
            if (destination in setOf("iframe", "frame")) return DagV2ResourceKind.Subframe
            if (
                destination in NonVisualDestinations ||
                NonVisualAcceptTypes.any(accept::contains) ||
                extension in NonVisualExtensions
            ) {
                return if (request.isForMainFrame) DagV2ResourceKind.MainDocument else DagV2ResourceKind.NonVisual
            }
            if (extension == "svg") return DagV2ResourceKind.SvgImage
            if (extension in RasterExtensions) return DagV2ResourceKind.RasterImage
            if (extension in MediaExtensions) return DagV2ResourceKind.Media
            if (request.isForMainFrame) return DagV2ResourceKind.MainDocument
            return DagV2ResourceKind.Unknown
        }

        fun route(
            kind: DagV2ResourceKind,
            url: String,
        ): DagV2ResourceRoute =
            when (kind) {
                DagV2ResourceKind.RasterImage,
                DagV2ResourceKind.SvgImage,
                -> DagV2ResourceRoute.VisualPipeline
                DagV2ResourceKind.Media -> DagV2ResourceRoute.Block
                DagV2ResourceKind.Subframe ->
                    if (isAuthorizedCaptchaFrame(url)) DagV2ResourceRoute.Bypass else DagV2ResourceRoute.Block
                DagV2ResourceKind.MainDocument,
                DagV2ResourceKind.NonVisual,
                DagV2ResourceKind.Unknown,
                -> DagV2ResourceRoute.Bypass
            }

        private fun DagV2ResourceRequest.header(name: String): String =
            headers.entries.firstOrNull { it.key.equals(name, true) }?.value.orEmpty()

        private fun isAuthorizedCaptchaFrame(url: String): Boolean {
            val uri = runCatching { URI(url) }.getOrNull() ?: return false
            if (!uri.scheme.equals("https", true)) return false
            val host = uri.host.orEmpty().lowercase(Locale.ROOT)
            val path = uri.path.orEmpty()
            return (
                host in setOf("www.google.com", "www.recaptcha.net") && path.startsWith("/recaptcha/")
            ) ||
                (host == "challenges.cloudflare.com" && path.startsWith("/cdn-cgi/challenge-platform/")) ||
                (host == "newassets.hcaptcha.com" && path.startsWith("/captcha/"))
        }

        private fun blockedResponse(): WebResourceResponse =
            WebResourceResponse(
                "text/plain",
                "utf-8",
                204,
                "No Content",
                mapOf("Cache-Control" to "no-store"),
                ByteArrayInputStream(ByteArray(0)),
            )

        private companion object {
            val NonVisualDestinations =
                setOf("document", "style", "script", "font", "manifest", "worker", "sharedworker", "serviceworker", "empty")
            val NonVisualAcceptTypes =
                setOf(
                    "text/html",
                    "text/css",
                    "javascript",
                    "application/json",
                    "text/x-component",
                    "application/manifest+json",
                    "application/wasm",
                    "font/",
                    "application/font",
                )
            val NonVisualExtensions =
                setOf(
                    "html",
                    "htm",
                    "css",
                    "js",
                    "mjs",
                    "json",
                    "map",
                    "woff",
                    "woff2",
                    "ttf",
                    "otf",
                    "eot",
                    "webmanifest",
                    "wasm",
                    "rsc",
                )
            val RasterExtensions =
                setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif", "heic", "heif", "ico")
            val MediaExtensions = setOf("mp4", "webm", "m3u8", "mp3", "wav", "ogg", "mov", "m4a")
        }
    }
