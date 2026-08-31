package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentAuthorityRegistry
import com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentIdentity
import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.atomic.AtomicLong

internal data class ChromeMediaShieldTransformedDocument(
    val bytes: ByteArray,
    val headers: List<ChromeHttpHeader>,
    val identity: ChromeMediaShieldDocumentIdentity,
    val bootstrapSha256: String,
)

internal data class ChromeMediaShieldDocumentMetrics(
    val transformed: Long = 0L,
    val failClosed: Long = 0L,
    val outstanding: Long = 0L,
)

internal sealed interface ChromeMediaShieldDocumentResult {
    data class Transformed(
        val document: ChromeMediaShieldTransformedDocument,
    ) : ChromeMediaShieldDocumentResult

    data class FailClosed(
        val reason: String,
        val bytes: ByteArray,
        val headers: List<ChromeHttpHeader>,
    ) : ChromeMediaShieldDocumentResult
}

/** Product-shaped DEV transformer for H19. Its bootstrap runs before every original page token. */
internal class ChromeMediaShieldDocumentTransformer(
    private val sessionId: String,
    private val policyEpoch: Long,
    private val documentSelfShieldEnabled: Boolean = false,
    private val cspPolicy: ChromeMediaShieldCspPolicy = ChromeMediaShieldCspPolicy(),
    private val randomBytes: (Int) -> ByteArray = ::secureRandomBytes,
) {
    private val transformed = AtomicLong()
    private val failClosed = AtomicLong()
    private val outstanding = AtomicLong()

    init {
        require(sessionId.isNotBlank())
        require(policyEpoch > 0L)
    }

    fun transform(
        sourceBytes: ByteArray,
        sourceHeaders: List<ChromeHttpHeader>,
        disposition: ChromeMediaShieldDocumentDisposition.Transform,
    ): ChromeMediaShieldDocumentResult {
        outstanding.incrementAndGet()
        var issuedIdentity: ChromeMediaShieldDocumentIdentity? = null
        var delivered = false
        return try {
            if (sourceBytes.size > MaximumDocumentBytes) return failClosed("document_too_large")
            if (sourceBytes.containsNul()) return failClosed("document_contains_nul")
            val source = sourceBytes.toString(Charsets.ISO_8859_1)
            val insertion =
                ChromeMediaShieldHtmlPrefixParser.insertionPoint(source, MaximumInsertionPrefixBytes)
                    ?: return failClosed("document_insertion_unsafe")
            val scriptNonce = randomToken()
            val styleNonce = randomToken()
            val readyToken = randomToken()
            val topLevel = disposition.kind == ChromeMediaShieldDocumentKind.TopLevel
            val identity =
                ChromeMediaShieldDocumentAuthorityRegistry.issue(
                    sessionId = sessionId,
                    epoch = policyEpoch,
                    readyToken = readyToken,
                    topLevel = topLevel,
                ) ?: return failClosed("document_authority_issue_failed")
            issuedIdentity = identity
            val script =
                ChromeMediaShieldBootstrap.script(
                    readyToken = readyToken,
                    styleNonce = styleNonce,
                    topLevel = topLevel,
                    selfShieldIdentity = identity.takeIf { documentSelfShieldEnabled },
                )
            val failClosedInstaller = ChromeMediaShieldBootstrap.parserBarrierFailClosedInstallerScript()
            val parserTail =
                if (documentSelfShieldEnabled) {
                    ""
                } else if (topLevel) {
                    val guardScript = ChromeMediaShieldBootstrap.parserBarrierGuardScript()
                    "<script nonce=\"$scriptNonce\" src=\"${ChromePhotosDataPlaneLabContract.MediaShieldParserBarrierUrl}\" " +
                        "referrerpolicy=\"no-referrer\"></script><script nonce=\"$scriptNonce\">" +
                        "$guardScript</script>"
                } else {
                    "<script nonce=\"$scriptNonce\">${ChromeMediaShieldBootstrap.subdocumentGuardScript()}</script>"
                }
            val injection =
                (
                    if (topLevel || documentSelfShieldEnabled) {
                        "<style id=\"${ChromeMediaShieldBootstrap.CurtainStyleElementId}\" nonce=\"$styleNonce\">" +
                            ChromeMediaShieldBootstrap.curtainCss +
                            "</style>"
                    } else {
                        ""
                    }
                ) +
                    "<style id=\"${ChromeMediaShieldBootstrap.StyleElementId}\" nonce=\"$styleNonce\">" +
                    ChromeMediaShieldBootstrap.css + "</style>" +
                    "<script nonce=\"$scriptNonce\">$failClosedInstaller</script>" +
                    "<script nonce=\"$scriptNonce\">$script</script>$parserTail"
            val injected =
                source.substring(0, insertion.offset) +
                    insertion.prefix +
                    injection +
                    insertion.suffix +
                    source.substring(insertion.offset)
            val output =
                ChromeMediaShieldStaticMarkupNeutralizer.neutralize(
                    source = injected,
                    metaCspRewriter = cspPolicy::rewriteMetaPolicy,
                )
            transformed.incrementAndGet()
            delivered = true
            ChromeMediaShieldDocumentResult.Transformed(
                ChromeMediaShieldTransformedDocument(
                    bytes = output.toByteArray(Charsets.ISO_8859_1),
                    headers =
                        cspPolicy.apply(
                            sourceHeaders = sourceHeaders,
                            scriptNonce = scriptNonce,
                            styleNonce = styleNonce,
                            charset = disposition.charset,
                        ),
                    identity = identity,
                    bootstrapSha256 =
                        sha256(
                            (failClosedInstaller + script + parserTail)
                                .toByteArray(Charsets.US_ASCII),
                        ),
                ),
            )
        } catch (_: Throwable) {
            failClosed("document_transform_exception")
        } finally {
            if (!delivered) issuedIdentity?.let(ChromeMediaShieldDocumentAuthorityRegistry::revokeIssued)
            outstanding.decrementAndGet()
        }
    }

    fun metrics(): ChromeMediaShieldDocumentMetrics =
        ChromeMediaShieldDocumentMetrics(
            transformed = transformed.get(),
            failClosed = failClosed.get(),
            outstanding = outstanding.get(),
        )

    private fun failClosed(reason: String): ChromeMediaShieldDocumentResult.FailClosed {
        failClosed.incrementAndGet()
        val bytes =
            "<!doctype html><html><head><meta charset=\"utf-8\"><title>Glosh</title></head>" +
                "<body><p>Contenido visual no autorizado.</p></body></html>"
        return ChromeMediaShieldDocumentResult.FailClosed(
            reason = reason,
            bytes = bytes.toByteArray(Charsets.UTF_8),
            headers =
                listOf(
                    ChromeHttpHeader("Content-Type", "text/html; charset=utf-8"),
                    ChromeHttpHeader("Content-Security-Policy", "default-src 'none'"),
                    ChromeHttpHeader("Cache-Control", "no-store"),
                    ChromeHttpHeader("X-Content-Type-Options", "nosniff"),
                ),
        )
    }

    private fun randomToken(): String {
        val bytes = randomBytes(TokenBytes)
        return try {
            Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    private fun ByteArray.containsNul(): Boolean = any { it == 0.toByte() }

    private companion object {
        const val TokenBytes = 16
        const val MaximumDocumentBytes = 2 * 1024 * 1024
        const val MaximumInsertionPrefixBytes = 32 * 1024

        fun secureRandomBytes(size: Int): ByteArray = ByteArray(size).also(SecureRandom()::nextBytes)
    }
}

internal class ChromeMediaShieldDocumentAuthority(
    private val admission: ChromeMediaShieldDocumentAdmission,
    private val transformer: ChromeMediaShieldDocumentTransformer,
) {
    private val admissionFailClosed = AtomicLong()

    fun normalizeUpstreamRequest(request: ChromePhotosProxyRequest): ChromePhotosProxyRequest =
        admission.normalizeUpstreamRequest(request)

    fun governs(request: ChromePhotosProxyRequest): Boolean = admission.governs(request)

    fun requiresBufferedDecision(
        request: ChromePhotosProxyRequest,
        response: ChromePhotosUpstreamResponse,
    ): Boolean = admission.requiresBufferedDecision(request, response)

    fun processBuffered(
        request: ChromePhotosProxyRequest,
        response: ChromePhotosUpstreamResponse,
        bytes: ByteArray,
        bodyExceeded: Boolean = false,
    ): ChromeMediaShieldDocumentResult? =
        when (val disposition = admission.disposition(request, response)) {
            is ChromeMediaShieldDocumentDisposition.Transform ->
                if (bodyExceeded) {
                    failClosed("document_too_large")
                } else {
                    transformer.transform(bytes, response.headers, disposition)
                }
            is ChromeMediaShieldDocumentDisposition.FailClosed ->
                failClosed(disposition.reason)
            ChromeMediaShieldDocumentDisposition.NotDocument -> null
        }

    fun metrics(): ChromeMediaShieldDocumentMetrics =
        transformer.metrics().let { metrics ->
            metrics.copy(failClosed = metrics.failClosed + admissionFailClosed.get())
        }

    private fun failClosed(reason: String): ChromeMediaShieldDocumentResult.FailClosed {
        admissionFailClosed.incrementAndGet()
        return ChromeMediaShieldDocumentResult.FailClosed(
            reason = reason,
            bytes = FailClosedDocumentBytes,
            headers = FailClosedDocumentHeaders,
        )
    }

    private companion object {
        val FailClosedDocumentBytes =
            "<!doctype html><html><body></body></html>".toByteArray()
        val FailClosedDocumentHeaders =
            listOf(
                ChromeHttpHeader("Content-Type", "text/html; charset=utf-8"),
                ChromeHttpHeader("Content-Security-Policy", "default-src 'none'"),
                ChromeHttpHeader("Cache-Control", "no-store"),
                ChromeHttpHeader("X-Content-Type-Options", "nosniff"),
            )
    }
}
