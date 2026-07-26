package com.contentfilter.user.dag2

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.contentfilter.core.network.security.PublicDestinationDecision
import com.contentfilter.core.network.security.PublicNetworkDestinationGuard
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
        private val destinationGuard: PublicNetworkDestinationGuard,
        private val calibration: DagV2CalibrationController? = null,
    ) {
        fun intercept(request: DagV2ResourceRequest): WebResourceResponse? {
            val kind = classify(request)
            val destination = destinationGuard.validateImmediate(request.url)
            if (destination.decision == PublicDestinationDecision.Block) {
                return if (kind.isVisual()) imagePipeline.intercept(request, kind) else blockedResponse()
            }
            val current =
                request.attribution == DagV2RequestAttribution.Current &&
                    request.documentContext != null
            if (request.source == DagV2ResourceSource.ServiceWorker && current) {
                metrics.serviceWorkerRequest()
            }
            if (request.attribution != DagV2RequestAttribution.Current) {
                return if (kind.isVisual()) imagePipeline.intercept(request, kind) else null
            }
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
            calibration?.onNewDocument(session)
        }

        fun close() {
            imagePipeline.shutdown()
        }

        fun cancelVisualRequests() {
            imagePipeline.cancelAll()
        }

        fun classify(request: DagV2ResourceRequest): DagV2ResourceKind {
            if (request.isForMainFrame) return DagV2ResourceKind.MainDocument
            val destination = request.header("Sec-Fetch-Dest").lowercase(Locale.ROOT)
            val accept = request.header("Accept").lowercase(Locale.ROOT)
            val extension =
                runCatching { URI(request.url).path.orEmpty().substringAfterLast('.', "").lowercase() }
                    .getOrDefault("")

            kindFromDestination(destination, request.isForMainFrame, extension)?.let { return it }
            if (accept.contains("image/")) {
                return if (extension == "svg") {
                    DagV2ResourceKind.SvgImage
                } else {
                    DagV2ResourceKind.RasterImage
                }
            }
            if (accept.startsWith("video/") || accept.startsWith("audio/")) {
                return DagV2ResourceKind.Media
            }
            if (
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

        private fun kindFromDestination(
            destination: String,
            isForMainFrame: Boolean,
            extension: String,
        ): DagV2ResourceKind? =
            when {
                destination == "image" ->
                    if (extension == "svg") DagV2ResourceKind.SvgImage else DagV2ResourceKind.RasterImage
                destination == "video" || destination == "audio" -> DagV2ResourceKind.Media
                destination == "iframe" || destination == "frame" -> DagV2ResourceKind.Subframe
                destination == "document" -> DagV2ResourceKind.MainDocument
                destination in NonVisualDestinations ->
                    if (isForMainFrame) DagV2ResourceKind.MainDocument else DagV2ResourceKind.NonVisual
                else -> null
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

        private fun DagV2ResourceKind.isVisual(): Boolean =
            this == DagV2ResourceKind.RasterImage || this == DagV2ResourceKind.SvgImage

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
                setOf("style", "script", "font", "manifest", "worker", "sharedworker", "serviceworker", "empty")
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

@Singleton
class DagV2ResourceInterceptor
    @Inject
    constructor(
        private val router: DagV2ResourceRouter,
        private val contexts: DagV2DocumentContextRegistry,
    ) {
        fun intercept(
            request: WebResourceRequest,
            source: DagV2ResourceSource,
            context: DagV2DocumentRequestContext,
        ): WebResourceResponse? = interceptBound(evidence(request, source), context)

        internal fun interceptBound(
            evidence: DagV2ResourceEvidence,
            context: DagV2DocumentRequestContext,
        ): WebResourceResponse? {
            val attributed = contexts.resolveBound(context, evidence)
            return router.intercept(attributed.toRequest())
        }

        fun attribute(
            request: WebResourceRequest,
            source: DagV2ResourceSource,
            context: DagV2DocumentRequestContext,
        ): DagV2AttributedResource = contexts.resolveBound(context, evidence(request, source))

        fun interceptServiceWorker(request: WebResourceRequest): WebResourceResponse? {
            if (!contexts.hasActiveContext()) return null
            return intercept(evidence(request, DagV2ResourceSource.ServiceWorker))
        }

        internal fun interceptServiceWorker(evidence: DagV2ResourceEvidence): WebResourceResponse? {
            if (!contexts.hasActiveContext()) return null
            return intercept(evidence.copy(source = DagV2ResourceSource.ServiceWorker))
        }

        internal fun intercept(evidence: DagV2ResourceEvidence): WebResourceResponse? {
            val attributed = contexts.resolve(evidence)
            return router.intercept(attributed.toRequest())
        }

        private fun evidence(
            request: WebResourceRequest,
            source: DagV2ResourceSource,
        ) = DagV2ResourceEvidence(
            url = request.url.toString(),
            headers = request.requestHeaders.orEmpty(),
            isForMainFrame = request.isForMainFrame,
            source = source,
        )

        private fun DagV2AttributedResource.toRequest() =
            DagV2ResourceRequest(
                url = evidence.url,
                headers = evidence.headers,
                isForMainFrame = evidence.isForMainFrame,
                source = evidence.source,
                documentContext = context,
                attribution = attribution,
            )
    }
