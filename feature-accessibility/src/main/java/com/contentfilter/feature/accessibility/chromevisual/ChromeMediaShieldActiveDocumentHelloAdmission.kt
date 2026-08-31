package com.contentfilter.feature.accessibility.chromevisual

import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentHandshakeCompletion
import com.contentfilter.core.domain.chrome.ChromeMediaShieldReadyClaim

/** Exact, bounded outcome of reading Chrome's current structural foreground boundary. */
internal sealed interface ChromeMediaShieldActiveDocumentContextReadResult {
    data class Found(
        val binding: ChromeMediaShieldActiveDocumentNativeBinding,
    ) : ChromeMediaShieldActiveDocumentContextReadResult

    data class Unavailable(
        val reason: String,
    ) : ChromeMediaShieldActiveDocumentContextReadResult
}

/**
 * Admits one HELLO when Chrome publishes the exact foreground WebView boundary.
 *
 * The parser-blocking bootstrap can reach native before Chrome exposes its virtual WebView root.
 * In that case the original HELLO remains pending and a real Chrome Accessibility event re-runs
 * the structural read. There is no timer, retry request, polling, or window-id-only fallback.
 * Transport cancellation is the fixed fail-closed bound.
 */
internal class ChromeMediaShieldActiveDocumentHelloAdmission(
    private val readContext: () -> ChromeMediaShieldActiveDocumentContextReadResult,
    private val claimCurrent: (ChromeMediaShieldReadyClaim) -> Boolean,
    private val onWaiting: (ChromeMediaShieldReadyClaim, String) -> Unit,
    private val onAccepted: (
        ChromeMediaShieldReadyClaim,
        ChromeMediaShieldActiveDocumentNativeBinding,
        ChromeMediaShieldActiveDocumentHandshakeCompletion,
    ) -> Unit,
    private val onRejected: (ChromeMediaShieldReadyClaim, String) -> Unit,
) : AutoCloseable {
    private var pending: Pending? = null

    fun accept(
        claim: ChromeMediaShieldReadyClaim,
        completion: ChromeMediaShieldActiveDocumentHandshakeCompletion,
    ) {
        if (!claimCurrent(claim)) {
            completion.reject()
            onRejected(claim, ClaimStale)
            return
        }
        pending?.also { stale ->
            pending = null
            stale.completion.reject()
            onRejected(stale.claim, Superseded)
        }
        when (val observed = readContext()) {
            is ChromeMediaShieldActiveDocumentContextReadResult.Found ->
                onAccepted(claim, observed.binding, completion)
            is ChromeMediaShieldActiveDocumentContextReadResult.Unavailable -> {
                pending = Pending(claim, completion)
                onWaiting(claim, observed.reason)
            }
        }
    }

    fun onChromeStructuralEvent() {
        val current = pending ?: return
        if (!claimCurrent(current.claim)) {
            pending = null
            current.completion.reject()
            onRejected(current.claim, ClaimStale)
            return
        }
        val observed = readContext() as? ChromeMediaShieldActiveDocumentContextReadResult.Found ?: return
        pending = null
        onAccepted(current.claim, observed.binding, current.completion)
    }

    fun onTransportCancelled(completion: ChromeMediaShieldActiveDocumentHandshakeCompletion): Boolean {
        val current = pending?.takeIf { it.completion === completion } ?: return false
        pending = null
        onRejected(current.claim, TransportCancelled)
        return true
    }

    fun hasCurrentClaim(): Boolean = pending?.claim?.let(claimCurrent) == true

    fun cancel(reason: String): Boolean {
        val current = pending ?: return false
        pending = null
        current.completion.reject()
        onRejected(current.claim, reason)
        return true
    }

    override fun close() {
        cancel(Closed)
    }

    private data class Pending(
        val claim: ChromeMediaShieldReadyClaim,
        val completion: ChromeMediaShieldActiveDocumentHandshakeCompletion,
    )

    private companion object {
        const val ClaimStale = "hello_claim_stale"
        const val Superseded = "hello_superseded"
        const val TransportCancelled = "handshake_transport_cancelled"
        const val Closed = "handshake_closed"
    }
}
