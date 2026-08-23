package com.contentfilter.user.chromedataplane

import java.net.URI

internal data class ChromePhotosSanitizedResponse(
    val statusCode: Int,
    val statusText: String,
    val contentType: String?,
    val location: String?,
    val bytes: ByteArray,
    val decision: ChromePhotosResourceDecision,
    val cacheHit: Boolean,
    val contentHash: String?,
    val decisionResult: ChromePhotoDecisionResult? = null,
)

internal class ChromePhotosRealResponseSanitizer(
    private val transformer: ChromePhotosResourceTransformer,
    private val allowlist: ChromePhotosHostAllowlist,
    private val placeholderBytes: ByteArray,
) {
    fun sanitize(
        requestMethod: String,
        upstream: ChromePhotosUpstreamResponse,
    ): ChromePhotosSanitizedResponse {
        if (upstream.statusCode in ChromePhotosRealUpstream.RedirectCodes) {
            return sanitizeRedirect(upstream)
        }
        if (requestMethod == ChromePhotosProxyRequest.Head) {
            return ChromePhotosSanitizedResponse(
                statusCode = upstream.statusCode,
                statusText = upstream.statusText,
                contentType = upstream.contentType,
                location = null,
                bytes = ByteArray(0),
                decision = ChromePhotosResourceDecision.Passthrough,
                cacheHit = false,
                contentHash = null,
            )
        }

        val isImage = upstream.contentType.isImageContentType()
        if (upstream.bodyTooLarge) {
            return if (isImage) placeholderUnknown() else plainFailure("Response too large")
        }
        if (isImage && !upstream.contentEncoding.isIdentityEncoding()) {
            return placeholderUnknown()
        }
        val transformed =
            transformer.transform(
                contentType = upstream.contentType.orEmpty(),
                candidateBytes = upstream.body,
            )
        val outputContentType =
            when (transformed.decision) {
                ChromePhotosResourceDecision.Block,
                ChromePhotosResourceDecision.Unknown,
                -> PlaceholderContentType
                else -> upstream.contentType
            }
        return ChromePhotosSanitizedResponse(
            statusCode = upstream.statusCode,
            statusText = upstream.statusText,
            contentType = outputContentType,
            location = null,
            bytes = transformed.bytes,
            decision = transformed.decision,
            cacheHit = transformed.cacheHit,
            contentHash = transformed.contentHash,
            decisionResult = transformed.decisionResult,
        )
    }

    private fun sanitizeRedirect(upstream: ChromePhotosUpstreamResponse): ChromePhotosSanitizedResponse {
        val location = upstream.location?.takeIf { isAllowedRedirect(upstream.host, it) }
        return if (location != null) {
            ChromePhotosSanitizedResponse(
                statusCode = upstream.statusCode,
                statusText = upstream.statusText,
                contentType = null,
                location = location,
                bytes = ByteArray(0),
                decision = ChromePhotosResourceDecision.Passthrough,
                cacheHit = false,
                contentHash = null,
            )
        } else {
            plainFailure("Redirect blocked")
        }
    }

    internal fun isAllowedRedirect(
        sourceHost: String,
        location: String,
    ): Boolean {
        if (location.startsWith('/') && !location.startsWith("//")) return true
        val uri = runCatching { URI(location) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.userInfo != null) return false
        if (uri.port !in setOf(-1, ChromePhotosRealUpstream.HttpsPort)) return false
        val destination = uri.host ?: return false
        return allowlist.isAllowed(sourceHost) && allowlist.isAllowed(destination)
    }

    private fun placeholderUnknown() =
        ChromePhotosSanitizedResponse(
            statusCode = 200,
            statusText = "OK",
            contentType = PlaceholderContentType,
            location = null,
            bytes = placeholderBytes,
            decision = ChromePhotosResourceDecision.Unknown,
            cacheHit = false,
            contentHash = null,
        )

    private fun plainFailure(message: String): ChromePhotosSanitizedResponse {
        val bytes = message.toByteArray(Charsets.UTF_8)
        return ChromePhotosSanitizedResponse(
            statusCode = 502,
            statusText = "Bad Gateway",
            contentType = "text/plain; charset=utf-8",
            location = null,
            bytes = bytes,
            decision = ChromePhotosResourceDecision.Passthrough,
            cacheHit = false,
            contentHash = null,
        )
    }

    private fun String?.isImageContentType(): Boolean =
        this?.lowercase()?.substringBefore(';')?.trim()?.startsWith("image/") == true

    private fun String?.isIdentityEncoding(): Boolean = isNullOrBlank() || equals("identity", ignoreCase = true)

    private companion object {
        const val PlaceholderContentType = "image/png"
    }
}
