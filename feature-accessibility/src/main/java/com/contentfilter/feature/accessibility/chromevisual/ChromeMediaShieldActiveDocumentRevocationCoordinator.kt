package com.contentfilter.feature.accessibility.chromevisual

import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentHandshakeCompletion

internal data class ChromeMediaShieldActiveDocumentRevocationTerminal(
    val result: ChromeMediaShieldActiveDocumentRevocationResult,
    val transportCompleted: Boolean,
)

/**
 * Owns the protocol REVOKE boundary until the external surface is provably opaque.
 *
 * The active-document owner may drop release authority immediately, but the transport is
 * acknowledged only after the exact opaque transaction commits. A timeout or cancellation can
 * make the protocol terminal without cancelling Android's already-submitted fail-close restore;
 * any later callback is therefore deliberately inert.
 */
internal class ChromeMediaShieldActiveDocumentRevocationCoordinator(
    private val surfaceAlreadyOpaque: () -> Boolean,
    private val submitOpaque: (((Boolean) -> Unit) -> ChromePhotosProtectedSurfaceRevokeResult),
) : AutoCloseable {
    private val gate = ChromeMediaShieldActiveDocumentRevocationGate()
    private var pending: Pending? = null

    fun begin(
        generation: Long,
        completion: ChromeMediaShieldActiveDocumentHandshakeCompletion,
        onTerminal: (ChromeMediaShieldActiveDocumentRevocationTerminal) -> Unit,
    ) {
        val admission =
            gate.begin(
                generation = generation,
                alreadyOpaque = surfaceAlreadyOpaque(),
            ) { result ->
                if (pending?.completion === completion) pending = null
                val transportCompleted =
                    when (result.decision) {
                        ChromeMediaShieldActiveDocumentRevocationDecision.Accepted ->
                            completion.acceptRevocation()
                        ChromeMediaShieldActiveDocumentRevocationDecision.Rejected ->
                            completion.reject()
                    }
                onTerminal(ChromeMediaShieldActiveDocumentRevocationTerminal(result, transportCompleted))
            }
        if (admission !is ChromeMediaShieldActiveDocumentRevocationAdmission.SubmitOpaque) return

        pending = Pending(admission.token, completion)
        val result =
            submitOpaque { committed ->
                if (committed) {
                    gate.onOpaqueCommitted(admission.token)
                } else {
                    gate.onSubmissionFailed(admission.token)
                }
            }
        if (result == ChromePhotosProtectedSurfaceRevokeResult.Failed) {
            gate.onSubmissionFailed(admission.token)
        }
    }

    fun onTransportCancelled(completion: ChromeMediaShieldActiveDocumentHandshakeCompletion): Boolean {
        val current = pending?.takeIf { it.completion === completion } ?: return false
        gate.cancel(current.token)
        return true
    }

    fun cancelCurrent(): Boolean {
        val current = pending ?: return false
        gate.cancel(current.token)
        return true
    }

    fun hasPending(): Boolean = pending != null

    override fun close() {
        pending = null
        gate.close()
    }

    private data class Pending(
        val token: ChromeMediaShieldActiveDocumentRevocationToken,
        val completion: ChromeMediaShieldActiveDocumentHandshakeCompletion,
    )
}
