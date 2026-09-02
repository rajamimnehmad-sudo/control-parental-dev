package com.contentfilter.user.chromedataplane

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

internal data class ChromeOriginalUiSvgMetrics(
    val registered: Int = 0,
    val registryBytes: Int = 0,
    val cssRewritten: Long = 0,
    val cssRejected: Long = 0,
    val networkAccepted: Long = 0,
    val networkRejected: Long = 0,
    val assetServed: Long = 0,
    val assetRejected: Long = 0,
)

internal class ChromeOriginalUiSvgAuthority(
    private val placeholderBytes: ByteArray,
    private val validator: ChromeOriginalUiSvgValidator = ChromeOriginalUiSvgValidator(),
    private val registry: ChromeOriginalUiSvgRegistry = ChromeOriginalUiSvgRegistry(validator),
) : AutoCloseable {
    val cssRewriter = ChromeCssSvgRewriter(registry)
    private val cssRewritten = AtomicLong()
    private val cssRejected = AtomicLong()
    private val networkAccepted = AtomicLong()
    private val networkRejected = AtomicLong()
    private val assetServed = AtomicLong()
    private val assetRejected = AtomicLong()

    fun normalizeUpstreamRequest(request: ChromePhotosProxyRequest): ChromePhotosProxyRequest {
        if (!request.isStylesheetIntent() && !request.isSvgImageIntent()) return request
        val headers =
            request.headers.filterNot { it.name.lowercase(Locale.US) in RemovedConditionalHeaders } +
                ChromeHttpHeader("Accept-Encoding", "identity")
        return request.copy(headers = headers)
    }

    fun processStylesheet(
        request: ChromePhotosProxyRequest,
        response: ChromePhotosUpstreamResponse,
    ): ChromePhotosSanitizedResponse? {
        if (!request.isStylesheetIntent()) return null
        if (request.method == ChromePhotosProxyRequest.Head) return null
        if (response.statusCode != 200 || !response.headers.hasIdentityContentEncoding()) return null
        val contentTypes = response.headers.filter { it.name.equals("Content-Type", true) }.map { it.value }
        if (contentTypes.size != 1 || contentTypes.single().substringBefore(';').trim().lowercase(Locale.US) != CssMimeType) return null
        val bounded = response.body.readBounded(MaximumCssBytes)
        if (bounded.exceeded) return failClosedStylesheet("css_too_large")
        val charset = contentTypes.single().cssCharsetOrNull() ?: return failClosedStylesheet("css_charset")
        val source =
            runCatching {
                charset.newDecoder().onMalformedInput(
                    CodingErrorAction.REPORT,
                ).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bounded.bytes)).toString()
            }.getOrElse { return failClosedStylesheet("css_decode") }
        val result = cssRewriter.rewrite(source)
        cssRewritten.addAndGet(result.rewritten.toLong())
        cssRejected.addAndGet(result.rejected.toLong())
        val bytes = result.css.toByteArray(charset)
        val changed = result.rewritten > 0
        return ChromePhotosSanitizedResponse(
            statusCode = 200,
            statusText = response.statusText,
            headers =
                if (changed) {
                    transformedHeaders(
                        response.headers,
                        "text/css; charset=${charset.name()}",
                    )
                } else {
                    ChromeHttpHeaderPolicy.downstreamResponseHeaders(response.headers)
                },
            bytes = bytes,
            decision = ChromePhotosResourceDecision.Passthrough,
            cacheHit = false,
            contentHash = null,
            inputBytes = bounded.bytes.size,
        )
    }

    fun processNetworkSvg(
        request: ChromePhotosProxyRequest,
        response: ChromePhotosUpstreamResponse,
    ): ChromePhotosSanitizedResponse? {
        val contentTypes = response.headers.filter { it.name.equals("Content-Type", true) }.map { it.value }
        if (contentTypes.size != 1 || contentTypes.single().trim().lowercase(Locale.US) != SvgMimeType) return null
        if (request.method == ChromePhotosProxyRequest.Head) {
            return if (response.statusCode == 200) {
                ChromePhotosSanitizedResponse(
                    response.statusCode,
                    response.statusText,
                    transformedHeaders(response.headers, SvgMimeType),
                    ByteArray(0),
                    ChromePhotosResourceDecision.Passthrough,
                    false,
                    null,
                    0,
                )
            } else {
                null
            }
        }
        if (response.statusCode != 200 || !response.headers.hasIdentityContentEncoding()) {
            return failClosedSvg(
                "svg_response",
            )
        }
        val bounded = response.body.readBounded(MaximumSvgBytes)
        if (bounded.exceeded) return failClosedSvg("svg_too_large")
        return when (validator.validate(bounded.bytes, SvgMimeType)) {
            is ChromeOriginalUiSvgValidation.Valid -> {
                networkAccepted.incrementAndGet()
                ChromePhotosSanitizedResponse(
                    statusCode = 200,
                    statusText = response.statusText,
                    headers = transformedHeaders(response.headers, SvgMimeType),
                    bytes = bounded.bytes,
                    decision = ChromePhotosResourceDecision.Passthrough,
                    cacheHit = false,
                    contentHash = sha256(bounded.bytes),
                    inputBytes = bounded.bytes.size,
                    observedBodyDigest = sha256(bounded.bytes),
                )
            }
            is ChromeOriginalUiSvgValidation.Invalid -> failClosedSvg("svg_validation")
        }
    }

    fun serveAsset(request: ChromePhotosProxyRequest): ChromePhotosSanitizedResponse {
        if (request.method !in setOf("GET", ChromePhotosProxyRequest.Head) || request.body.isNotEmpty()) {
            assetRejected.incrementAndGet()
            return plainFailure(405, "Method Not Allowed")
        }
        val asset = registry.resolve(request.target)
        if (asset == null) {
            assetRejected.incrementAndGet()
            return plainFailure(404, "Not Found")
        }
        assetServed.incrementAndGet()
        return ChromePhotosSanitizedResponse(
            statusCode = 200,
            statusText = "OK",
            headers =
                listOf(
                    ChromeHttpHeader("Content-Type", SvgMimeType),
                    ChromeHttpHeader("Cache-Control", "no-store"),
                    ChromeHttpHeader("X-Content-Type-Options", "nosniff"),
                    ChromeHttpHeader("Cross-Origin-Resource-Policy", "cross-origin"),
                    ChromeHttpHeader("Access-Control-Allow-Origin", "*"),
                ),
            bytes = if (request.method == ChromePhotosProxyRequest.Head) ByteArray(0) else asset.bytes,
            decision = ChromePhotosResourceDecision.Passthrough,
            cacheHit = false,
            contentHash = asset.digest,
            inputBytes = asset.bytes.size,
        )
    }

    fun metrics(): ChromeOriginalUiSvgMetrics =
        ChromeOriginalUiSvgMetrics(
            registered = registry.size(),
            registryBytes = registry.bytes(),
            cssRewritten = cssRewritten.get(),
            cssRejected = cssRejected.get(),
            networkAccepted = networkAccepted.get(),
            networkRejected = networkRejected.get(),
            assetServed = assetServed.get(),
            assetRejected = assetRejected.get(),
        )

    override fun close() = registry.close()

    private fun failClosedStylesheet(reason: String): ChromePhotosSanitizedResponse {
        cssRejected.incrementAndGet()
        return ChromePhotosSanitizedResponse(
            statusCode = 200,
            statusText = "OK",
            headers =
                listOf(
                    ChromeHttpHeader("Content-Type", CssContentType),
                    ChromeHttpHeader("Cache-Control", "no-store"),
                ),
            bytes = "/* Glosh fail-closed: $reason */".toByteArray(),
            decision = ChromePhotosResourceDecision.Passthrough,
            cacheHit = false,
            contentHash = null,
            inputBytes = 0,
        )
    }

    private fun failClosedSvg(reason: String): ChromePhotosSanitizedResponse {
        networkRejected.incrementAndGet()
        return ChromePhotosSanitizedResponse(
            statusCode = 200,
            statusText = "OK",
            headers =
                listOf(
                    ChromeHttpHeader("Content-Type", PlaceholderMimeType),
                    ChromeHttpHeader("Cache-Control", "no-store"),
                    ChromeHttpHeader("X-Content-Type-Options", "nosniff"),
                ),
            bytes = placeholderBytes,
            decision = ChromePhotosResourceDecision.Unknown,
            cacheHit = false,
            contentHash = null,
            inputBytes = 0,
            decisionResult =
                ChromePhotoDecisionResult(
                    ChromePhotoDecision.Unknown,
                    reason,
                    ChromePhotoDecisionSource.Error,
                ),
        )
    }

    private fun transformedHeaders(
        source: List<ChromeHttpHeader>,
        contentType: String,
    ): List<ChromeHttpHeader> =
        ChromeHttpHeaderPolicy.downstreamResponseHeaders(source).filterNot {
            it.name.lowercase(Locale.US) in InvalidatedEntityHeaders
        } +
            listOf(
                ChromeHttpHeader("Content-Type", contentType),
                ChromeHttpHeader("Cache-Control", "no-store"),
                ChromeHttpHeader("X-Content-Type-Options", "nosniff"),
            )

    private fun plainFailure(
        code: Int,
        text: String,
    ): ChromePhotosSanitizedResponse =
        ChromePhotosSanitizedResponse(
            code,
            text,
            listOf(
                ChromeHttpHeader("Content-Type", "text/plain; charset=utf-8"),
                ChromeHttpHeader("Cache-Control", "no-store"),
                ChromeHttpHeader("X-Content-Type-Options", "nosniff"),
            ),
            text.toByteArray(),
            ChromePhotosResourceDecision.Passthrough,
            false,
            null,
            0,
        )

    private fun ChromePhotosProxyRequest.isStylesheetIntent(): Boolean =
        method in setOf("GET", ChromePhotosProxyRequest.Head) && headerValues("Sec-Fetch-Dest").map {
            it.trim().lowercase(Locale.US)
        }.distinct() == listOf("style")

    private fun ChromePhotosProxyRequest.isSvgImageIntent(): Boolean =
        method in setOf("GET", ChromePhotosProxyRequest.Head) &&
            headerValues("Sec-Fetch-Dest").any {
                it.trim().equals("image", true)
            }

    private fun String.cssCharsetOrNull(): Charset? {
        val value =
            split(';').drop(1).map(String::trim).firstOrNull { it.startsWith("charset=", true) }
                ?.substringAfter('=')?.trim()?.trim('"', '\'') ?: "UTF-8"
        return value.lowercase(Locale.US).takeIf {
            it in AllowedCssCharsets
        }?.let { runCatching { Charset.forName(it) }.getOrNull() }
    }

    private companion object {
        const val SvgMimeType = "image/svg+xml"
        const val CssMimeType = "text/css"
        const val CssContentType = "text/css; charset=utf-8"
        const val PlaceholderMimeType = "image/png"
        const val MaximumSvgBytes = 96 * 1024
        const val MaximumCssBytes = 2 * 1024 * 1024
        val RemovedConditionalHeaders =
            setOf("accept-encoding", "range", "if-range", "if-none-match", "if-modified-since")
        val AllowedCssCharsets = setOf("utf-8", "us-ascii", "iso-8859-1", "windows-1252")
        val InvalidatedEntityHeaders =
            setOf("content-type", "content-encoding", "content-length", "content-range", "etag", "last-modified", "content-md5", "content-digest", "repr-digest", "digest", "accept-ranges", "vary", "cache-control", "expires", "x-content-type-options")
    }
}
