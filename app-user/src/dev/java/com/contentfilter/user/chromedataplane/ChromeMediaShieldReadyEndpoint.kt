package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentAuthorityRegistry
import com.contentfilter.core.domain.chrome.ChromeMediaShieldReadyClaimResult
import com.contentfilter.core.domain.chrome.ChromeMediaShieldReadyHandshakeBridge
import com.contentfilter.core.domain.chrome.ChromeMediaShieldReadyHandshakeResult
import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicLong

internal data class ChromeMediaShieldReadyEndpointMetrics(
    val requests: Long = 0L,
    val preflights: Long = 0L,
    val accepted: Long = 0L,
    val rejected: Long = 0L,
)

/** Fixed-origin, capability-authenticated DEV endpoint. It never forwards READY traffic upstream. */
internal class ChromeMediaShieldReadyEndpoint {
    private val requests = AtomicLong()
    private val preflights = AtomicLong()
    private val accepted = AtomicLong()
    private val rejected = AtomicLong()

    fun handle(request: ChromePhotosProxyRequest): ChromePhotosSanitizedResponse? {
        if (request.target != ChromePhotosDataPlaneLabContract.MediaShieldReadyPath) return null
        requests.incrementAndGet()
        val body = request.body
        return try {
            val origin = request.exactReadyOriginOrNull() ?: return reject("ready_origin_invalid")
            if (origin == NullOrigin) return reject("ready_origin_not_network")
            if (request.method == OptionsMethod) return preflight(request, origin)
            if (request.method != PostMethod) return reject("ready_method_invalid", statusCode = 405)
            if (!request.hasExactReadyContentType()) return reject("ready_content_type_invalid")
            if (!request.hasReadyFetchMetadata()) return reject("ready_fetch_metadata_invalid")
            val parsed = body.parseReadyBody() ?: return reject("ready_body_invalid")
            when (
                val claim =
                    ChromeMediaShieldDocumentAuthorityRegistry.claimTopLevelReady(
                        parsed.token,
                        parsed.lifecycleSequence,
                    )
            ) {
                is ChromeMediaShieldReadyClaimResult.Invalid -> reject(claim.reason)
                is ChromeMediaShieldReadyClaimResult.Claimed -> {
                    when (ChromeMediaShieldReadyHandshakeBridge.awaitCurrentPresentation(claim.claim)) {
                        ChromeMediaShieldReadyHandshakeResult.Accepted -> accept(origin)
                        ChromeMediaShieldReadyHandshakeResult.Rejected -> reject("ready_presentation_rejected")
                        ChromeMediaShieldReadyHandshakeResult.Unavailable -> reject("ready_presentation_unavailable")
                        ChromeMediaShieldReadyHandshakeResult.TimedOut -> reject("ready_presentation_timeout")
                        ChromeMediaShieldReadyHandshakeResult.Interrupted -> reject("ready_presentation_interrupted")
                    }
                }
            }
        } finally {
            body.fill(0)
        }
    }

    fun metrics(): ChromeMediaShieldReadyEndpointMetrics =
        ChromeMediaShieldReadyEndpointMetrics(
            requests = requests.get(),
            preflights = preflights.get(),
            accepted = accepted.get(),
            rejected = rejected.get(),
        )

    private fun accept(origin: String): ChromePhotosSanitizedResponse {
        accepted.incrementAndGet()
        return response(
            statusCode = 204,
            statusText = "No Content",
            headers =
                BaseHeaders +
                    ChromeHttpHeader("Access-Control-Allow-Origin", origin) +
                    ChromeHttpHeader("Vary", "Origin"),
        )
    }

    private fun preflight(
        request: ChromePhotosProxyRequest,
        origin: String,
    ): ChromePhotosSanitizedResponse {
        if (
            request.body.isNotEmpty() ||
            !request.hasReadyFetchMetadata() ||
            request.headerValues("Access-Control-Request-Method").singleOrNull()
                ?.equals(PostMethod, ignoreCase = true) != true ||
            !request.hasExactPreflightHeaders()
        ) {
            return reject("ready_preflight_invalid")
        }
        preflights.incrementAndGet()
        val privateNetworkRequested =
            request.headerValues("Access-Control-Request-Private-Network").singleOrNull()
                ?.equals("true", ignoreCase = true) == true
        return response(
            statusCode = 204,
            statusText = "No Content",
            headers =
                BaseHeaders +
                    ChromeHttpHeader("Access-Control-Allow-Origin", origin) +
                    ChromeHttpHeader("Access-Control-Allow-Methods", PostMethod) +
                    ChromeHttpHeader("Access-Control-Allow-Headers", "Content-Type") +
                    ChromeHttpHeader("Access-Control-Max-Age", "0") +
                    (
                        if (privateNetworkRequested) {
                            listOf(ChromeHttpHeader("Access-Control-Allow-Private-Network", "true"))
                        } else {
                            emptyList()
                        }
                    ) +
                    ChromeHttpHeader("Vary", "Origin"),
        )
    }

    private fun reject(
        reason: String,
        statusCode: Int = 503,
    ): ChromePhotosSanitizedResponse {
        rejected.incrementAndGet()
        val bytes = reason.toByteArray(StandardCharsets.US_ASCII)
        return response(
            statusCode = statusCode,
            statusText = if (statusCode == 405) "Method Not Allowed" else "Service Unavailable",
            headers = BaseHeaders + ChromeHttpHeader("Content-Type", "text/plain; charset=us-ascii"),
            bytes = bytes,
        )
    }

    private fun response(
        statusCode: Int,
        statusText: String,
        headers: List<ChromeHttpHeader>,
        bytes: ByteArray = ByteArray(0),
    ) = ChromePhotosSanitizedResponse(
        statusCode = statusCode,
        statusText = statusText,
        headers = headers,
        bytes = bytes,
        decision = ChromePhotosResourceDecision.Passthrough,
        cacheHit = false,
        contentHash = null,
        inputBytes = 0,
    )

    private data class ParsedReadyBody(
        val token: String,
        val lifecycleSequence: Long,
    )

    private companion object {
        const val PostMethod = "POST"
        const val OptionsMethod = "OPTIONS"
        const val NullOrigin = "null"
        const val ReadyContentType = "text/plain;charset=UTF-8"
        const val MaximumReadyBodyBytes = 96
        val BaseHeaders =
            listOf(
                ChromeHttpHeader("Cache-Control", "no-store"),
                ChromeHttpHeader("Cross-Origin-Resource-Policy", "cross-origin"),
                ChromeHttpHeader("X-Content-Type-Options", "nosniff"),
            )

        fun ChromePhotosProxyRequest.exactReadyOriginOrNull(): String? {
            val values = headerValues("Origin")
            if (values.size != 1) return null
            val origin = values.single()
            if (origin == NullOrigin) return origin
            val uri = runCatching { URI(origin) }.getOrNull() ?: return null
            if (
                uri.isOpaque ||
                uri.scheme?.lowercase() !in setOf("http", "https") ||
                uri.host.isNullOrBlank() ||
                uri.rawUserInfo != null ||
                uri.rawPath?.isNotEmpty() == true ||
                uri.rawQuery != null ||
                uri.rawFragment != null
            ) {
                return null
            }
            return origin
        }

        fun ChromePhotosProxyRequest.hasExactReadyContentType(): Boolean {
            val values = headerValues("Content-Type")
            if (values.size != 1) return false
            return values.single().filterNot(Char::isWhitespace).equals(ReadyContentType, ignoreCase = true)
        }

        fun ChromePhotosProxyRequest.hasReadyFetchMetadata(): Boolean =
            headerValues("Sec-Fetch-Mode").singleOrNull()?.equals("cors", ignoreCase = true) == true &&
                headerValues("Sec-Fetch-Dest").singleOrNull()?.equals("empty", ignoreCase = true) == true

        fun ChromePhotosProxyRequest.hasExactPreflightHeaders(): Boolean {
            val values = headerValues("Access-Control-Request-Headers")
            if (values.isEmpty()) return true
            if (values.size != 1) return false
            val names = values.single().split(',').map(String::trim).filter(String::isNotEmpty)
            return names.size == 1 && names.single().equals("content-type", ignoreCase = true)
        }

        fun ByteArray.parseReadyBody(): ParsedReadyBody? {
            if (isEmpty() || size > MaximumReadyBodyBytes || any { it.toInt() !in 0x20..0x7e }) return null
            val parts = toString(StandardCharsets.US_ASCII).split('|')
            if (parts.size != 3 || parts[0] != "v1") return null
            val token = parts[1]
            val lifecycle = parts[2].toLongOrNull()?.takeIf { it > 0L } ?: return null
            return ParsedReadyBody(token, lifecycle)
        }
    }
}
