package com.contentfilter.feature.accessibility.chromevisual

import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentChallenge
import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentHandshakeCompletion
import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentRequest
import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentTransportCancellationRegistration

/**
 * One-shot DEV probe for replaying exactly one already-consumed PRESENT request.
 *
 * The raw capability remains process-local and is never exposed by diagnostics. The caller must
 * supply the exact still-released attempt sequence before the request can re-enter the normal
 * active-document boundary. Navigation, STOP, or any owner mismatch drops it without dispatch.
 */
internal class ChromeMediaShieldActiveDocumentReplayProbe {
    private var candidate: Candidate? = null
    private var rejectedReplays = 0L

    fun rememberConsumedPresent(
        attemptSequence: Long,
        request: ChromeMediaShieldActiveDocumentRequest.Present,
    ) {
        require(attemptSequence > 0L)
        candidate = Candidate(attemptSequence, request)
    }

    fun replay(
        currentReleasedAttemptSequence: Long?,
        dispatch: (
            ChromeMediaShieldActiveDocumentRequest.Present,
            ChromeMediaShieldActiveDocumentGuardedCompletion,
        ) -> Unit,
    ): ChromeMediaShieldActiveDocumentReplayResult {
        val retained = candidate ?: return ChromeMediaShieldActiveDocumentReplayResult.Absent
        candidate = null
        if (retained.attemptSequence != currentReleasedAttemptSequence) {
            return ChromeMediaShieldActiveDocumentReplayResult.Stale
        }
        val completion = ReplayCompletion()
        runCatching {
            dispatch(
                retained.request,
                ChromeMediaShieldActiveDocumentGuardedCompletion(completion),
            )
        }.onFailure {
            return ChromeMediaShieldActiveDocumentReplayResult.FailClosed
        }
        return completion.result().also { result ->
            if (result == ChromeMediaShieldActiveDocumentReplayResult.Rejected && rejectedReplays < Long.MAX_VALUE) {
                rejectedReplays += 1L
            }
        }
    }

    fun clear(attemptSequence: Long? = null) {
        if (attemptSequence == null || candidate?.attemptSequence == attemptSequence) candidate = null
    }

    fun hasCandidate(): Boolean = candidate != null

    fun rejectedReplayCount(): Long = rejectedReplays

    private class Candidate(
        val attemptSequence: Long,
        val request: ChromeMediaShieldActiveDocumentRequest.Present,
    ) {
        override fun toString(): String = "ChromeMediaShieldActiveDocumentReplayCandidate(redacted)"
    }

    private class ReplayCompletion : ChromeMediaShieldActiveDocumentHandshakeCompletion {
        private var rejected = false
        private var acceptedUnexpectedly = false

        override fun onTransportCancelled(
            callback: () -> Unit,
        ): ChromeMediaShieldActiveDocumentTransportCancellationRegistration =
            ChromeMediaShieldActiveDocumentTransportCancellationRegistration.Registered

        override fun issueChallenge(challenge: ChromeMediaShieldActiveDocumentChallenge): Boolean =
            unexpectedAcceptance()

        override fun acceptProof(): Boolean = unexpectedAcceptance()

        override fun acceptPresentation(): Boolean = unexpectedAcceptance()

        override fun acceptRevocation(): Boolean = unexpectedAcceptance()

        override fun reject(): Boolean {
            if (rejected || acceptedUnexpectedly) return false
            rejected = true
            return true
        }

        fun result(): ChromeMediaShieldActiveDocumentReplayResult =
            when {
                rejected -> ChromeMediaShieldActiveDocumentReplayResult.Rejected
                else -> ChromeMediaShieldActiveDocumentReplayResult.FailClosed
            }

        private fun unexpectedAcceptance(): Boolean {
            acceptedUnexpectedly = true
            return false
        }
    }
}

internal enum class ChromeMediaShieldActiveDocumentReplayResult(
    val protocolResult: String,
) {
    Absent("result=active_document_replay_absent"),
    Stale("result=active_document_replay_stale"),
    Rejected("result=active_document_replay_rejected"),
    FailClosed("result=active_document_replay_fail_closed"),
}
