package com.contentfilter.user.chromedataplane

import java.util.Locale

internal class ChromeMediaResponseSanitizer(
    private val authority: ChromeMediaContentAuthority,
    private val placeholderBytes: ByteArray,
    private val maximumMediaBytes: Int = ChromePhotosRealUpstream.DefaultMaximumBodyBytes * 4,
) {
    init {
        require(placeholderBytes.isNotEmpty())
        require(maximumMediaBytes > 0)
    }

    fun sanitize(
        requestMethod: String,
        request: ChromePhotosProxyRequest,
        candidate: ChromeMediaContentInspection.Candidate,
    ): ChromePhotosSanitizedResponse {
        val upstream = candidate.response
        if (!responseMayHaveBody(requestMethod, upstream.statusCode)) {
            return passthroughWithoutBody(upstream)
        }
        return authority.withBodyAdmission(
            onRejected = {
                val decision = ChromeMediaPayloadDecision.Unknown(BodyAdmissionReason)
                authority.record(decision)
                placeholderUnknown(upstream, decision.reason)
            },
        ) {
            val bounded = upstream.body.readBounded(maximumMediaBytes)
            if (bounded.exceeded) {
                val decision = ChromeMediaPayloadDecision.Unknown(MediaTooLargeReason)
                authority.record(decision)
                return@withBodyAdmission placeholderUnknown(upstream, decision.reason)
            }
            val decision = authority.inspectPayload(candidate, bounded.bytes)
            authority.record(decision)
            when (decision) {
                ChromeMediaPayloadDecision.Safe -> safeResponse(request, candidate, bounded.bytes)
                ChromeMediaPayloadDecision.Block -> placeholderBlocked(upstream, bounded.bytes)
                is ChromeMediaPayloadDecision.Unknown -> placeholderUnknown(upstream, decision.reason, bounded.bytes)
            }
        }
    }

    private fun safeResponse(
        request: ChromePhotosProxyRequest,
        candidate: ChromeMediaContentInspection.Candidate,
        bytes: ByteArray,
    ): ChromePhotosSanitizedResponse {
        val upstream = candidate.response
        val mime = candidate.declaredMimeType ?: return placeholderUnknown(upstream, MissingMimeReason, bytes)
        val entityHeaders = transformedMediaHeaders(upstream.headers, mime)
        val parsedRange = ChromeHttpRange.parse(request.firstHeader("Range"))
        if (parsedRange is ChromeHttpRange.ParseResult.Invalid) {
            return placeholderUnknown(upstream, parsedRange.reason, bytes)
        }
        val range = (parsedRange as ChromeHttpRange.ParseResult.Parsed).range
        if (range == null) {
            return ChromePhotosSanitizedResponse(
                statusCode = 200,
                statusText = "OK",
                headers = entityHeaders,
                bytes = bytes,
                decision = ChromePhotosResourceDecision.Safe,
                cacheHit = false,
                contentHash = sha256(bytes),
                inputBytes = bytes.size,
            )
        }
        val resolved = range.resolve(bytes.size.toLong())
        if (resolved is ChromeHttpRange.Resolution.Unsatisfiable) {
            return ChromePhotosSanitizedResponse(
                statusCode = 416,
                statusText = "Range Not Satisfiable",
                headers =
                    entityHeaders.filterNot { it.name.equals("Content-Type", true) } +
                        ChromeHttpHeader("Content-Range", "bytes */${bytes.size}") +
                        ChromeHttpHeader("Accept-Ranges", "bytes"),
                bytes = ByteArray(0),
                decision = ChromePhotosResourceDecision.Safe,
                cacheHit = false,
                contentHash = sha256(bytes),
                inputBytes = bytes.size,
            )
        }
        resolved as ChromeHttpRange.Resolution.Satisfied
        if (!ifRangeMatches(request.firstHeader("If-Range"), upstream.headers)) {
            return ChromePhotosSanitizedResponse(
                statusCode = 200,
                statusText = "OK",
                headers = entityHeaders,
                bytes = bytes,
                decision = ChromePhotosResourceDecision.Safe,
                cacheHit = false,
                contentHash = sha256(bytes),
                inputBytes = bytes.size,
            )
        }
        val rangedBytes = bytes.copyOfRange(resolved.start.toInt(), resolved.endInclusive.toInt() + 1)
        return ChromePhotosSanitizedResponse(
            statusCode = 206,
            statusText = "Partial Content",
            headers =
                entityHeaders +
                    ChromeHttpHeader("Content-Range", "bytes ${resolved.start}-${resolved.endInclusive}/${bytes.size}") +
                    ChromeHttpHeader("Accept-Ranges", "bytes"),
            bytes = rangedBytes,
            decision = ChromePhotosResourceDecision.Safe,
            cacheHit = false,
            contentHash = sha256(bytes),
            inputBytes = bytes.size,
        )
    }

    private fun ifRangeMatches(
        ifRange: String?,
        headers: List<ChromeHttpHeader>,
    ): Boolean {
        if (ifRange.isNullOrBlank()) return true
        val value = ifRange.trim()
        return headers.any { header ->
            (header.name.equals("ETag", true) || header.name.equals("Last-Modified", true)) &&
                header.value.trim() == value
        }
    }

    private fun transformedMediaHeaders(
        headers: List<ChromeHttpHeader>,
        contentType: String,
    ): List<ChromeHttpHeader> =
        ChromeHttpHeaderPolicy.downstreamResponseHeaders(headers).filterNot { header ->
            header.name.lowercase(Locale.US) in InvalidatedMediaEntityHeaders
        } +
            listOf(
                ChromeHttpHeader("Content-Type", contentType),
                ChromeHttpHeader("Cache-Control", "no-store"),
            )

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

    private fun placeholderBlocked(
        upstream: ChromePhotosUpstreamResponse,
        bytes: ByteArray,
    ) = ChromePhotosSanitizedResponse(
        statusCode = 200,
        statusText = "OK",
        headers = transformedMediaHeaders(upstream.headers, PlaceholderMime),
        bytes = placeholderBytes,
        decision = ChromePhotosResourceDecision.Block,
        cacheHit = false,
        contentHash = null,
        inputBytes = bytes.size,
        observedBodyDigest = sha256(bytes),
    )

    private fun placeholderUnknown(
        upstream: ChromePhotosUpstreamResponse,
        reason: String,
        bytes: ByteArray = ByteArray(0),
    ) = ChromePhotosSanitizedResponse(
        statusCode = 200,
        statusText = "OK",
        headers = transformedMediaHeaders(upstream.headers, PlaceholderMime),
        bytes = placeholderBytes,
        decision = ChromePhotosResourceDecision.Unknown,
        cacheHit = false,
        contentHash = null,
        inputBytes = bytes.size,
        observedBodyDigest = bytes.takeIf { it.isNotEmpty() }?.let(::sha256),
        decisionResult =
            ChromePhotoDecisionResult(
                decision = ChromePhotoDecision.Unknown,
                reason = reason,
                source = ChromePhotoDecisionSource.Error,
            ),
    )

    private companion object {
        const val PlaceholderMime = "image/png"
        const val BodyAdmissionReason = "media_body_admission_interrupted"
        const val MediaTooLargeReason = "media_byte_limit"
        const val MissingMimeReason = "media_mime_missing"
        val InvalidatedMediaEntityHeaders =
            setOf(
                "content-type",
                "content-encoding",
                "content-length",
                "content-range",
                "accept-ranges",
                "etag",
                "last-modified",
                "content-md5",
                "content-digest",
                "digest",
                "cache-control",
                "expires",
                "vary",
            )
    }
}

internal class ChromeHttpRange private constructor(
    private val start: Long?,
    private val endInclusive: Long?,
) {
    sealed interface ParseResult {
        data class Parsed(val range: ChromeHttpRange?) : ParseResult

        data class Invalid(val reason: String) : ParseResult
    }

    sealed interface Resolution {
        data class Satisfied(val start: Long, val endInclusive: Long) : Resolution

        data object Unsatisfiable : Resolution
    }

    fun resolve(totalLength: Long): Resolution {
        if (totalLength <= 0L) return Resolution.Unsatisfiable
        return when {
            start != null && start >= totalLength -> Resolution.Unsatisfiable
            start != null -> Resolution.Satisfied(start, minOf(endInclusive ?: totalLength - 1L, totalLength - 1L))
            endInclusive == null || endInclusive <= 0L -> Resolution.Unsatisfiable
            else -> Resolution.Satisfied(maxOf(0L, totalLength - endInclusive), totalLength - 1L)
        }
    }

    companion object {
        fun parse(value: String?): ParseResult =
            when {
                value == null -> ParseResult.Parsed(null)
                value.count { it == ',' } != 0 -> ParseResult.Invalid("media_multiple_ranges_unsupported")
                !value.trim().startsWith("bytes=", true) -> ParseResult.Invalid("media_range_unit_unsupported")
                else -> {
                    val spec = value.trim().substringAfter('=', "").trim()
                    val separator = spec.indexOf('-')
                    if (separator < 0 || spec.indexOf('-', separator + 1) >= 0) {
                        ParseResult.Invalid("media_range_malformed")
                    } else {
                        val left = spec.substring(0, separator).trim()
                        val right = spec.substring(separator + 1).trim()
                        val start = left.toLongOrNull()
                        val end = right.toLongOrNull()
                        when {
                            left.isNotEmpty() && start == null -> ParseResult.Invalid("media_range_start_invalid")
                            right.isNotEmpty() && end == null -> ParseResult.Invalid("media_range_end_invalid")
                            left.isEmpty() && right.isEmpty() -> ParseResult.Invalid("media_range_empty")
                            start != null && end != null && end < start -> ParseResult.Invalid("media_range_reversed")
                            else -> ParseResult.Parsed(ChromeHttpRange(start, end))
                        }
                    }
                }
            }
    }
}
