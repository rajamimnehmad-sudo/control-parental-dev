package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import java.net.URI

internal data class ChromePhotosSanitizedResponse(
    val statusCode: Int,
    val statusText: String,
    val headers: List<ChromeHttpHeader>,
    val bytes: ByteArray,
    val decision: ChromePhotosResourceDecision,
    val cacheHit: Boolean,
    val contentHash: String?,
    val inputBytes: Int,
    val decisionResult: ChromePhotoDecisionResult? = null,
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
) {
    fun sanitize(
        requestMethod: String,
        upstream: ChromePhotosUpstreamResponse,
    ): ChromePhotosSanitizedResponse {
        if (upstream.statusCode in RedirectCodes) return sanitizeRedirect(upstream)
        if (!responseMayHaveBody(requestMethod, upstream.statusCode)) return passthroughWithoutBody(upstream)
        if (!isImage(upstream)) {
            val bytes = upstream.body.readBounded(maximumImageBytes)
            return if (bytes.exceeded) {
                plainFailure("Response requires streaming")
            } else {
                passthrough(upstream, bytes.bytes)
            }
        }
        if (!upstream.headers.firstValue("Content-Encoding").isIdentityEncoding()) return placeholderUnknown(upstream)
        val bounded = upstream.body.readBounded(maximumImageBytes)
        if (bounded.exceeded) return placeholderUnknown(upstream)
        val transformed =
            transformer.transform(
                contentType = upstream.headers.firstValue("Content-Type").orEmpty(),
                candidateBytes = bounded.bytes,
            )
        val outputContentType =
            when (transformed.decision) {
                ChromePhotosResourceDecision.Block,
                ChromePhotosResourceDecision.Unknown,
                -> PlaceholderContentType
                else -> upstream.headers.firstValue("Content-Type") ?: PlaceholderContentType
            }
        val outputStatus =
            if (transformed.decision in setOf(ChromePhotosResourceDecision.Block, ChromePhotosResourceDecision.Unknown)) {
                200
            } else {
                upstream.statusCode
            }
        return ChromePhotosSanitizedResponse(
            statusCode = outputStatus,
            statusText = upstream.statusText,
            headers = ChromeHttpHeaderPolicy.transformedImageHeaders(upstream.headers, outputContentType),
            bytes = transformed.bytes,
            decision = transformed.decision,
            cacheHit = transformed.cacheHit,
            contentHash = transformed.contentHash,
            inputBytes = bounded.bytes.size,
            decisionResult = transformed.decisionResult,
        )
    }

    fun isImage(upstream: ChromePhotosUpstreamResponse): Boolean =
        upstream.headers.firstValue("Content-Type").isImageContentType()

    fun sanitizeRedirect(upstream: ChromePhotosUpstreamResponse): ChromePhotosSanitizedResponse {
        val location = upstream.headers.firstValue("Location")?.takeIf { isAllowedRedirect(it) }
        return if (location != null) {
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
        } else {
            plainFailure("Redirect blocked")
        }
    }

    internal fun isAllowedRedirect(location: String): Boolean {
        if (location.startsWith('/') && !location.startsWith("//")) return true
        val uri = runCatching { URI(location) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.userInfo != null) return false
        if (uri.port !in setOf(-1, ChromePhotosRealUpstream.HttpsPort)) return false
        val host = uri.host ?: return false
        return host == ChromePhotosDataPlaneLabContract.FixtureHost || destinationAuthority.isSyntacticallyPublicHost(host)
    }

    private fun passthroughWithoutBody(upstream: ChromePhotosUpstreamResponse) =
        ChromePhotosSanitizedResponse(
            statusCode = upstream.statusCode,
            statusText = upstream.statusText,
            headers = upstream.headers,
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

    private fun placeholderUnknown(upstream: ChromePhotosUpstreamResponse) =
        ChromePhotosSanitizedResponse(
            statusCode = upstream.statusCode,
            statusText = upstream.statusText,
            headers = ChromeHttpHeaderPolicy.transformedImageHeaders(upstream.headers, PlaceholderContentType),
            bytes = placeholderBytes,
            decision = ChromePhotosResourceDecision.Unknown,
            cacheHit = false,
            contentHash = null,
            inputBytes = 0,
        )

    private fun plainFailure(message: String): ChromePhotosSanitizedResponse {
        val bytes = message.toByteArray(Charsets.UTF_8)
        return ChromePhotosSanitizedResponse(
            statusCode = 502,
            statusText = "Bad Gateway",
            headers = listOf(ChromeHttpHeader("Content-Type", "text/plain; charset=utf-8")),
            bytes = bytes,
            decision = ChromePhotosResourceDecision.Passthrough,
            cacheHit = false,
            contentHash = null,
            inputBytes = 0,
        )
    }

    private fun String?.isImageContentType(): Boolean =
        this?.lowercase()?.substringBefore(';')?.trim()?.startsWith("image/") == true

    private fun String?.isIdentityEncoding(): Boolean = isNullOrBlank() || equals("identity", ignoreCase = true)

    private companion object {
        const val PlaceholderContentType = "image/png"
        const val DefaultMaximumImageBytes = ChromePhotosRealUpstream.DefaultMaximumBodyBytes
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
        statusCode != 304

internal fun List<ChromeHttpHeader>.firstValue(name: String): String? =
    firstOrNull { it.name.equals(name, ignoreCase = true) }?.value
