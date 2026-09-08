package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import java.net.URI
import java.util.Locale

internal data class ChromePhotosSanitizedResponse(
    val statusCode: Int,
    val statusText: String,
    val headers: List<ChromeHttpHeader>,
    val bytes: ByteArray,
    val decision: ChromePhotosResourceDecision,
    val cacheHit: Boolean,
    val contentHash: String?,
    val inputBytes: Int,
    val observedBodyDigest: String? = null,
    val decisionResult: ChromePhotoDecisionResult? = null,
    val bodyAdmissionMs: Double? = null,
    val bodyReadMs: Double? = null,
    val processingAdmissionMs: Double? = null,
    val hashMs: Double? = null,
) {
    val contentType: String?
        get() = headers.firstValue("Content-Type")

    val location: String?
        get() = headers.firstValue("Location")
}

internal class ChromePhotosRealResponseSanitizer(
    private val transformer: ChromePhotosResourceTransformer,
    private val destinationAuthority: ChromePublicDestinationAuthority,
    private val placeholderBytes: ByteArray,
    private val maximumImageBytes: Int = DefaultMaximumImageBytes,
    private val imageAuthority: ChromeImageContentAuthority = ChromeImageContentAuthority(),
    private val visualDeliveryGate: ChromeNetworkVisualDeliveryGate = ChromeNetworkVisualDeliveryGate(),
) {
    fun sanitize(
        requestMethod: String,
        upstream: ChromePhotosUpstreamResponse,
    ): ChromePhotosSanitizedResponse =
        when (
            val inspection =
                imageAuthority.inspect(
                    ChromePhotosProxyRequest(requestMethod, "/"),
                    upstream,
                )
        ) {
            is ChromeImageContentInspection.Candidate -> sanitizeCandidate(requestMethod, inspection)
            is ChromeImageContentInspection.Passthrough -> sanitizePassthrough(requestMethod, inspection.response)
        }

    fun sanitizeCandidate(
        requestMethod: String,
        candidate: ChromeImageContentInspection.Candidate,
    ): ChromePhotosSanitizedResponse {
        val upstream = candidate.response
        if (upstream.statusCode in RedirectCodes) return sanitizeRedirect(upstream)
        if (
            requestMethod == ChromePhotosProxyRequest.Head ||
            upstream.statusCode in 100..199 ||
            upstream.statusCode in setOf(204, 205)
        ) {
            return passthroughWithoutBody(upstream)
        }
        visualDeliveryGate.replaceAllResponse(upstream)?.let { return it }
        if (upstream.statusCode == 304) return placeholderUnknown(upstream, ImageNotModifiedReason)
        if (upstream.statusCode == 206) return placeholderUnknown(upstream, PartialImageReason)
        if (!upstream.headers.hasIdentityContentEncoding()) {
            return placeholderUnknown(upstream, EncodedImageReason)
        }
        if (upstream.bodyLength > maximumImageBytes) return placeholderUnknown(upstream, ImageByteLimitReason)
        val reservedBytes = upstream.bodyLength.takeIf { it >= 0 }?.toInt()?.coerceAtLeast(1) ?: maximumImageBytes
        val admissionStarted = System.nanoTime()
        var admittedAt: Long? = null
        var readMillis: Double? = null
        var processingWaitMillis = 0.0
        val response =
            imageAuthority.withBufferedBodyBudget(
                bytes = reservedBytes,
                onRejected = { placeholderUnknown(upstream, BodyAdmissionReason) },
            ) {
                admittedAt = System.nanoTime()
                val readStarted = System.nanoTime()
                val bounded = upstream.body.readBounded(reservedBytes)
                readMillis = (System.nanoTime() - readStarted) / 1_000_000.0
                if (bounded.exceeded) return@withBufferedBodyBudget placeholderUnknown(upstream, ImageByteLimitReason)
                when (val resolution = imageAuthority.resolve(candidate, bounded.bytes)) {
                    is ChromeImageContentResolution.Reject ->
                        placeholderUnknown(
                            upstream = upstream,
                            reason = resolution.reason,
                            inputBytes = bounded.bytes.size,
                            observedBodyDigest = sha256(bounded.bytes),
                        )
                    is ChromeImageContentResolution.Inspect -> {
                        val mime = resolution.format.canonicalMimeType
                        val probe = transformer.probeCache(mime, bounded.bytes)
                        val transformed =
                            probe.result ?: run {
                                val processingStarted = System.nanoTime()
                                imageAuthority.withBodyAdmission(
                                    onRejected = { null },
                                ) {
                                    processingWaitMillis = (System.nanoTime() - processingStarted) / 1_000_000.0
                                    transformer.transform(mime, bounded.bytes).let {
                                        it.copy(hashMs = it.hashMs + probe.hashMs)
                                    }
                                }
                            }
                        if (transformed == null) {
                            placeholderUnknown(upstream, BodyAdmissionReason)
                        } else {
                            transformedResponse(upstream, bounded.bytes.size, resolution.format, transformed)
                        }
                    }
                }
            }
        return response.copy(
            bodyAdmissionMs = admittedAt?.let { (it - admissionStarted) / 1_000_000.0 },
            bodyReadMs = readMillis,
            processingAdmissionMs = processingWaitMillis,
        )
    }

    private fun sanitizePassthrough(
        requestMethod: String,
        upstream: ChromePhotosUpstreamResponse,
    ): ChromePhotosSanitizedResponse {
        if (!responseMayHaveBody(requestMethod, upstream.statusCode)) return passthroughWithoutBody(upstream)
        val bytes = upstream.body.readBounded(maximumImageBytes)
        return if (bytes.exceeded) plainFailure("Response requires streaming") else passthrough(upstream, bytes.bytes)
    }

    fun sanitizeRedirect(upstream: ChromePhotosUpstreamResponse): ChromePhotosSanitizedResponse {
        val sanitizedHeaders = ChromeHttpHeaderPolicy.downstreamResponseHeaders(upstream.headers)
        val sourceLocation =
            upstream.headers
                .filter { it.name.equals("Location", ignoreCase = true) }
                .singleOrNull()
        val location =
            sanitizedHeaders
                .filter { it.name.equals("Location", ignoreCase = true) }
                .singleOrNull()
                ?.value
                ?.takeIf { it == sourceLocation?.value }
                ?.takeIf(::isAllowedRedirect)
        return if (location != null) {
            ChromePhotosSanitizedResponse(
                statusCode = upstream.statusCode,
                statusText = upstream.statusText,
                headers =
                    sanitizedHeaders.filterNot { header ->
                        header.name.equals("Location", ignoreCase = true) ||
                            header.name.lowercase(Locale.US) in ChromeHttpHeaderPolicy.invalidatedImageEntityHeaders
                    } +
                        ChromeHttpHeader("Location", location) +
                        ChromeHttpHeader("Cache-Control", "no-store"),
                bytes = ByteArray(0),
                decision = ChromePhotosResourceDecision.Passthrough,
                cacheHit = false,
                contentHash = null,
                inputBytes = 0,
            )
        } else {
            plainFailure("Redirect blocked")
        }
    }

    internal fun isAllowedRedirect(location: String): Boolean {
        if (location.isBlank() || location != location.trim() || location.any { it.isISOControl() }) return false
        val uri = runCatching { URI(location) }.getOrNull() ?: return false
        if (!uri.isAbsolute) {
            return location.startsWith('/') && !location.startsWith("//") && uri.rawAuthority == null
        }
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.userInfo != null) return false
        if (uri.port !in setOf(-1, ChromePhotosRealUpstream.HttpsPort)) return false
        val host = uri.host ?: return false
        return host == ChromePhotosDataPlaneLabContract.FixtureHost || destinationAuthority.isSyntacticallyPublicHost(host)
    }

    private fun passthroughWithoutBody(upstream: ChromePhotosUpstreamResponse) =
        ChromePhotosSanitizedResponse(
            statusCode = upstream.statusCode,
            statusText = upstream.statusText,
            headers = ChromeHttpHeaderPolicy.downstreamResponseHeaders(upstream.headers),
            bytes = ByteArray(0),
            decision = ChromePhotosResourceDecision.Passthrough,
            cacheHit = false,
            contentHash = null,
            inputBytes = 0,
        )

    private fun passthrough(
        upstream: ChromePhotosUpstreamResponse,
        bytes: ByteArray,
    ) = ChromePhotosSanitizedResponse(
        statusCode = upstream.statusCode,
        statusText = upstream.statusText,
        headers = ChromeHttpHeaderPolicy.downstreamResponseHeaders(upstream.headers),
        bytes = bytes,
        decision = ChromePhotosResourceDecision.Passthrough,
        cacheHit = false,
        contentHash = null,
        inputBytes = bytes.size,
    )

    private fun transformedResponse(
        upstream: ChromePhotosUpstreamResponse,
        inputBytes: Int,
        format: ChromeImageFormat,
        transformed: ChromePhotosTransformResult,
    ): ChromePhotosSanitizedResponse {
        val replaced = transformed.decision != ChromePhotosResourceDecision.Safe
        return ChromePhotosSanitizedResponse(
            statusCode = if (replaced) 200 else upstream.statusCode,
            statusText = if (replaced) "OK" else upstream.statusText,
            headers =
                ChromeHttpHeaderPolicy.transformedImageHeaders(
                    upstream.headers,
                    if (replaced) PlaceholderContentType else format.canonicalMimeType,
                ),
            bytes = transformed.bytes,
            decision = transformed.decision,
            cacheHit = transformed.cacheHit,
            contentHash = transformed.contentHash,
            inputBytes = inputBytes,
            observedBodyDigest = transformed.contentHash,
            decisionResult = transformed.decisionResult,
            hashMs = transformed.hashMs,
        )
    }

    private fun placeholderUnknown(
        upstream: ChromePhotosUpstreamResponse,
        reason: String,
        inputBytes: Int = 0,
        observedBodyDigest: String? = null,
    ) = ChromePhotosSanitizedResponse(
        statusCode = 200,
        statusText = "OK",
        headers = ChromeHttpHeaderPolicy.transformedImageHeaders(upstream.headers, PlaceholderContentType),
        bytes = placeholderBytes,
        decision = ChromePhotosResourceDecision.Unknown,
        cacheHit = false,
        contentHash = null,
        inputBytes = inputBytes,
        observedBodyDigest = observedBodyDigest,
        decisionResult =
            ChromePhotoDecisionResult(
                decision = ChromePhotoDecision.Unknown,
                reason = reason,
                source = ChromePhotoDecisionSource.Error,
            ),
    )

    private fun plainFailure(message: String): ChromePhotosSanitizedResponse {
        val bytes = message.toByteArray(Charsets.UTF_8)
        return ChromePhotosSanitizedResponse(
            statusCode = 502,
            statusText = "Bad Gateway",
            headers =
                ChromeHttpHeaderPolicy.downstreamResponseHeaders(
                    listOf(ChromeHttpHeader("Content-Type", "text/plain; charset=utf-8")),
                ),
            bytes = bytes,
            decision = ChromePhotosResourceDecision.Passthrough,
            cacheHit = false,
            contentHash = null,
            inputBytes = 0,
        )
    }

    private companion object {
        const val PlaceholderContentType = "image/png"
        const val DefaultMaximumImageBytes = ChromePhotosRealUpstream.DefaultMaximumBodyBytes
        const val PartialImageReason = "partial_image_entity"
        const val ImageNotModifiedReason = "image_not_modified_without_current_authority"
        const val EncodedImageReason = "encoded_image_unsupported"
        const val BodyAdmissionReason = "image_body_admission_interrupted"
        const val ImageByteLimitReason = "image_byte_limit"
        val RedirectCodes = setOf(301, 302, 303, 307, 308)
    }
}

internal fun responseMayHaveBody(
    requestMethod: String,
    statusCode: Int,
): Boolean =
    requestMethod != ChromePhotosProxyRequest.Head &&
        statusCode !in 100..199 &&
        statusCode != 204 &&
        statusCode != 205 &&
        statusCode != 304

internal fun List<ChromeHttpHeader>.firstValue(name: String): String? =
    firstOrNull { it.name.equals(name, ignoreCase = true) }?.value
