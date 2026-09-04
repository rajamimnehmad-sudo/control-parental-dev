package com.contentfilter.user.chromedataplane

import java.net.URI
import java.util.Locale
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

internal enum class ChromeMediaKind {
    ProgressiveVideo,
    HlsManifest,
    HlsSegment,
}

internal data class ChromeMediaAuthorityMetrics(
    val candidates: Long = 0L,
    val bodyAdmissionPeak: Int = 0,
    val bodyAdmissionRejects: Long = 0L,
    val safe: Long = 0L,
    val blocked: Long = 0L,
    val unknown: Long = 0L,
)

internal sealed interface ChromeMediaContentInspection {
    val response: ChromePhotosUpstreamResponse
    val kind: ChromeMediaKind
    val declaredMimeType: String?

    data class Candidate(
        override val response: ChromePhotosUpstreamResponse,
        override val kind: ChromeMediaKind,
        override val declaredMimeType: String?,
        val requestIntent: Boolean,
    ) : ChromeMediaContentInspection

    data class Passthrough(
        override val response: ChromePhotosUpstreamResponse,
        override val kind: ChromeMediaKind = ChromeMediaKind.ProgressiveVideo,
        override val declaredMimeType: String? = null,
    ) : ChromeMediaContentInspection
}

internal sealed interface ChromeMediaPayloadDecision {
    data object Safe : ChromeMediaPayloadDecision

    data object Block : ChromeMediaPayloadDecision

    data class Unknown(val reason: String) : ChromeMediaPayloadDecision
}

/** A bounded, fail-closed authority for clear progressive video and HLS playlists. */
internal class ChromeMediaContentAuthority(
    private val payloadInspector: ChromeMediaPayloadInspector,
    maximumConcurrentBodies: Int = DefaultMaximumConcurrentBodies,
) {
    private val bodyPermits = Semaphore(maximumConcurrentBodies, true)
    private val activeBodies = AtomicInteger()
    private val bodyAdmissionPeak = AtomicInteger()
    private val candidates = AtomicLong()
    private val bodyAdmissionRejects = AtomicLong()
    private val safe = AtomicLong()
    private val blocked = AtomicLong()
    private val unknown = AtomicLong()

    init {
        require(maximumConcurrentBodies > 0)
    }

    fun normalizeUpstreamRequest(request: ChromePhotosProxyRequest): ChromePhotosProxyRequest {
        if (!request.isMediaIntent()) return request
        val headers =
            request.headers.filterNot { header ->
                header.name.lowercase(Locale.US) in MediaRequestHeadersRemoved
            } + ChromeHttpHeader("Accept-Encoding", "identity")
        return request.copy(headers = headers)
    }

    fun inspect(
        request: ChromePhotosProxyRequest,
        response: ChromePhotosUpstreamResponse,
    ): ChromeMediaContentInspection {
        val requestIntent = request.isMediaIntent()
        val declared = response.headers.singleDeclaredContentTypeOrNull()
        val effectiveDeclared = declared.effectiveMediaMimeType(request.target)
        val kind = mediaKind(effectiveDeclared, request.target)
        if (!requestIntent && kind == null) {
            return ChromeMediaContentInspection.Passthrough(response)
        }
        candidates.incrementAndGet()
        return ChromeMediaContentInspection.Candidate(
            response = response,
            kind = kind ?: ChromeMediaKind.ProgressiveVideo,
            declaredMimeType = effectiveDeclared,
            requestIntent = requestIntent,
        )
    }

    fun <T> withBodyAdmission(
        onRejected: () -> T,
        block: () -> T,
    ): T {
        try {
            bodyPermits.acquire()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            bodyAdmissionRejects.incrementAndGet()
            return onRejected()
        }
        val active = activeBodies.incrementAndGet()
        bodyAdmissionPeak.accumulateAndGet(active, ::maxOf)
        return try {
            block()
        } finally {
            activeBodies.decrementAndGet()
            bodyPermits.release()
        }
    }

    fun record(decision: ChromeMediaPayloadDecision) {
        when (decision) {
            ChromeMediaPayloadDecision.Safe -> safe.incrementAndGet()
            ChromeMediaPayloadDecision.Block -> blocked.incrementAndGet()
            is ChromeMediaPayloadDecision.Unknown -> unknown.incrementAndGet()
        }
    }

    fun metrics(): ChromeMediaAuthorityMetrics =
        ChromeMediaAuthorityMetrics(
            candidates = candidates.get(),
            bodyAdmissionPeak = bodyAdmissionPeak.get(),
            bodyAdmissionRejects = bodyAdmissionRejects.get(),
            safe = safe.get(),
            blocked = blocked.get(),
            unknown = unknown.get(),
        )

    internal fun inspectPayload(
        candidate: ChromeMediaContentInspection.Candidate,
        bytes: ByteArray,
    ): ChromeMediaPayloadDecision {
        val mime = candidate.declaredMimeType ?: return ChromeMediaPayloadDecision.Unknown(UnknownMimeReason)
        if (!candidate.response.headers.hasIdentityContentEncoding()) {
            return ChromeMediaPayloadDecision.Unknown(EncodedMediaReason)
        }
        if (candidate.response.statusCode == 206) {
            return ChromeMediaPayloadDecision.Unknown(PartialMediaReason)
        }
        if (candidate.response.statusCode !in setOf(200, 203)) {
            return ChromeMediaPayloadDecision.Unknown("media_status_${candidate.response.statusCode}")
        }
        return when (candidate.kind) {
            ChromeMediaKind.HlsManifest -> ChromeHlsManifestPolicy.inspect(bytes)
            ChromeMediaKind.HlsSegment -> payloadInspector.inspect(bytes, mime)
            ChromeMediaKind.ProgressiveVideo -> payloadInspector.inspect(bytes, mime)
        }
    }

    private fun ChromePhotosProxyRequest.isMediaIntent(): Boolean {
        val destinations = headerValues("Sec-Fetch-Dest").map { it.trim().lowercase(Locale.US) }
        return destinations.any { it == "video" } || targetLooksLikeMedia(target)
    }

    private companion object {
        const val DefaultMaximumConcurrentBodies = 2
        const val UnknownMimeReason = "media_mime_missing_or_ambiguous"
        const val EncodedMediaReason = "encoded_media_unsupported"
        const val PartialMediaReason = "partial_media_without_full_authority"
        val MediaRequestHeadersRemoved =
            setOf("accept-encoding", "range", "if-range", "if-none-match", "if-modified-since")
        val HlsMimeTypes =
            setOf(
                "application/vnd.apple.mpegurl",
                "application/x-mpegurl",
                "application/mpegurl",
                "audio/mpegurl",
            )
        val MediaPathSuffixes = setOf(".mp4", ".m4v", ".m3u8", ".m3u", ".m4s", ".ts")

        fun mediaKind(
            declaredMimeType: String?,
            target: String,
        ): ChromeMediaKind? =
            when {
                declaredMimeType in HlsMimeTypes -> ChromeMediaKind.HlsManifest
                targetLooksLikeHls(target) -> ChromeMediaKind.HlsManifest
                targetLooksLikeHlsSegment(target) -> ChromeMediaKind.HlsSegment
                declaredMimeType?.startsWith("video/") == true -> ChromeMediaKind.ProgressiveVideo
                targetLooksLikeProgressiveVideo(target) -> ChromeMediaKind.ProgressiveVideo
                else -> null
            }

        fun targetLooksLikeMedia(target: String): Boolean =
            targetLooksLikeHls(target) || targetLooksLikeProgressiveVideo(target)

        fun targetLooksLikeHls(target: String): Boolean =
            rawPath(target).lowercase(Locale.US).endsWith(".m3u8") ||
                rawPath(target).lowercase(Locale.US).endsWith(".m3u")

        fun targetLooksLikeHlsSegment(target: String): Boolean =
            rawPath(target).lowercase(Locale.US).let { path ->
                path.endsWith(".ts") || path.endsWith(".m4s")
            }

        fun targetLooksLikeProgressiveVideo(target: String): Boolean =
            rawPath(target).lowercase(Locale.US).let { path -> MediaPathSuffixes.any(path::endsWith) && !targetLooksLikeHls(target) }

        fun rawPath(target: String): String =
            runCatching { URI(target).rawPath }.getOrNull()?.ifEmpty { "/" }
                ?: target.substringBefore('?').substringBefore('#')
    }
}

private fun String?.effectiveMediaMimeType(target: String): String? {
    if (this != null && this !in AmbiguousMediaMimeTypes) return this
    val path =
        runCatching { URI(target).rawPath.orEmpty() }
            .getOrDefault(target.substringBefore('?').substringBefore('#'))
            .lowercase(Locale.US)
    return when {
        path.endsWith(".ts") -> "video/mp2t"
        path.endsWith(".m4s") -> "video/mp4"
        else -> this
    }
}

private val AmbiguousMediaMimeTypes =
    setOf(
        "application/octet-stream",
        "binary/octet-stream",
        "application/unknown",
        "application/x-unknown",
        "unknown/unknown",
    )

internal fun interface ChromeMediaPayloadInspector {
    fun inspect(
        bytes: ByteArray,
        declaredMimeType: String,
    ): ChromeMediaPayloadDecision
}

/** HLS is admitted only as a clear, syntactically bounded playlist. Every referenced segment is gated separately. */
internal object ChromeHlsManifestPolicy {
    private const val MaximumManifestBytes = 1 * 1024 * 1024
    private const val MaximumLineBytes = 16 * 1024
    private const val MaximumLines = 4096
    private val UriAttributePattern = Regex("(?i)(?:^|,)\\s*URI\\s*=\\s*(?:\"([^\"]*)\"|([^,]*))")

    fun inspect(bytes: ByteArray): ChromeMediaPayloadDecision {
        if (bytes.isEmpty() || bytes.size > MaximumManifestBytes) {
            return ChromeMediaPayloadDecision.Unknown("hls_manifest_size")
        }
        val source =
            runCatching {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes))
                    .toString()
            }.getOrNull() ?: return ChromeMediaPayloadDecision.Unknown("hls_manifest_encoding")
        val lines = source.split('\n')
        if (lines.size > MaximumLines || lines.firstOrNull()?.removeSuffix("\r") != "#EXTM3U") {
            return ChromeMediaPayloadDecision.Unknown("hls_manifest_header")
        }
        return try {
            var uriCount = 0
            lines.drop(1).forEach { rawLine ->
                val line = rawLine.removeSuffix("\r")
                if (line.length > MaximumLineBytes || line.any(Char::isISOControl)) {
                    throw HlsManifestRejected("hls_manifest_line")
                }
                val upper = line.uppercase(Locale.US)
                if (upper.contains("METHOD=AES-128") || upper.contains("METHOD=SAMPLE-AES") || upper.contains("KEYFORMAT=")) {
                    throw HlsManifestRejected("hls_encryption_not_inspectable")
                }
                UriAttributePattern.findAll(line).forEach { match ->
                    val uri = match.groupValues[1].ifEmpty { match.groupValues[2] }.trim()
                    if (!isAllowedReference(uri)) throw HlsManifestRejected("hls_uri_unsafe")
                    uriCount++
                }
                if (line.isNotEmpty() && !line.startsWith('#')) {
                    if (!isAllowedReference(line.trim())) throw HlsManifestRejected("hls_segment_uri_unsafe")
                    uriCount++
                }
            }
            if (uriCount == 0) {
                ChromeMediaPayloadDecision.Unknown("hls_manifest_without_media_reference")
            } else {
                ChromeMediaPayloadDecision.Safe
            }
        } catch (rejected: HlsManifestRejected) {
            ChromeMediaPayloadDecision.Unknown(rejected.message ?: "hls_manifest_rejected")
        }
    }

    private fun isAllowedReference(value: String): Boolean {
        if (value.isBlank() || value.any(Char::isISOControl)) return false
        if (value.startsWith("data:", true) || value.startsWith("blob:", true) || value.startsWith("javascript:", true)) return false
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        if (!uri.isAbsolute) return !value.startsWith("//")
        return uri.scheme.equals("https", true) && uri.userInfo == null && uri.port in setOf(-1, 443) && uri.host != null
    }

    private class HlsManifestRejected(message: String) : Exception(message)
}

private fun List<ChromeHttpHeader>.singleDeclaredContentTypeOrNull(): String? {
    val values = filter { it.name.equals("Content-Type", true) }.map { it.value.normalizedImageMimeType() }
    return values.singleOrNull()
}
