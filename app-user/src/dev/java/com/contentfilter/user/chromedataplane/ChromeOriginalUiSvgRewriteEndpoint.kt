package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentAuthorityRegistry
import com.contentfilter.core.domain.chrome.ChromeMediaShieldSelfReadyIdentity
import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

internal class ChromeOriginalUiSvgRewriteEndpoint(
    private val authority: ChromeOriginalUiSvgAuthority,
) {
    fun handle(request: ChromePhotosProxyRequest): ChromePhotosSanitizedResponse? {
        if (request.target != ChromePhotosDataPlaneLabContract.OriginalUiSvgRewritePath) return null
        if (request.method != "POST" || request.body.size > MaximumBodyBytes || !request.hasPlainTextContentType()) {
            return failure(400)
        }
        val body =
            runCatching {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(request.body))
                    .toString()
            }.getOrElse { return failure(400) }
        val newline = body.indexOf('\n')
        if (newline <= 0) return failure(400)
        val fields = body.substring(0, newline).split('|')
        if (fields.size != 8 || fields[0] != "v1" || fields[1] != "SVG_REWRITE") return failure(403)
        val token = fields[2]
        val identity =
            ChromeMediaShieldSelfReadyIdentity(
                protectionSessionId = fields[3],
                policyEpoch = fields[4].toLongOrNull() ?: return failure(403),
                navigationSequence = fields[5].toLongOrNull() ?: return failure(403),
                documentSequence = fields[6].toLongOrNull() ?: return failure(403),
                lifecycleSequence = 1L,
                topLevel =
                    when (fields[7]) {
                        "T" -> true
                        "S" -> false
                        else -> return failure(403)
                    },
            )
        if (!ChromeMediaShieldDocumentAuthorityRegistry.validatesClaimedSelfReady(token, identity)) return failure(403)
        val css = body.substring(newline + 1)
        val rewritten = authority.cssRewriter.rewrite(css).css.toByteArray(Charsets.UTF_8)
        return ChromePhotosSanitizedResponse(
            statusCode = 200,
            statusText = "OK",
            headers =
                listOf(
                    ChromeHttpHeader("Content-Type", "text/plain; charset=utf-8"),
                    ChromeHttpHeader("Cache-Control", "no-store"),
                    ChromeHttpHeader("X-Content-Type-Options", "nosniff"),
                ),
            bytes = rewritten,
            decision = ChromePhotosResourceDecision.Passthrough,
            cacheHit = false,
            contentHash = null,
            inputBytes = request.body.size,
        )
    }

    private fun ChromePhotosProxyRequest.hasPlainTextContentType(): Boolean =
        headerValues("Content-Type").singleOrNull()?.substringBefore(';')?.trim()?.equals("text/plain", true) == true

    private fun failure(code: Int): ChromePhotosSanitizedResponse =
        ChromePhotosSanitizedResponse(
            statusCode = code,
            statusText = "Rejected",
            headers =
                listOf(
                    ChromeHttpHeader("Content-Type", "text/plain; charset=utf-8"),
                    ChromeHttpHeader("Cache-Control", "no-store"),
                ),
            bytes = ByteArray(0),
            decision = ChromePhotosResourceDecision.Passthrough,
            cacheHit = false,
            contentHash = null,
            inputBytes = 0,
        )

    private companion object {
        const val MaximumBodyBytes = 512 * 1024
    }
}
