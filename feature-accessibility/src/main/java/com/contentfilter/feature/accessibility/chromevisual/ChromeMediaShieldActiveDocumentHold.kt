package com.contentfilter.feature.accessibility.chromevisual

import com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentAuthorityRegistry

internal enum class ChromeMediaShieldActiveDocumentHoldPhase {
    Idle,
    Armed,
    Reached,
}

internal data class ChromeMediaShieldActiveDocumentHoldSnapshot(
    val phase: ChromeMediaShieldActiveDocumentHoldPhase,
    val caseId: String = "",
    val stage: String = "",
    val nonceDigest: String = "",
    val sequence: Long = 0L,
)

/** One-shot diagnostic hold. It can delay work but can never manufacture authority. */
internal class ChromeMediaShieldActiveDocumentHold {
    private var sequence = 0L
    private var snapshot = ChromeMediaShieldActiveDocumentHoldSnapshot(ChromeMediaShieldActiveDocumentHoldPhase.Idle)
    private var continuation: ((Boolean) -> Unit)? = null

    fun arm(
        caseId: String,
        stage: String,
        nonce: String,
    ): Boolean {
        if (!caseId.isAllowedCase() || stage !in AllowedStages || !nonce.matches(NoncePattern)) return false
        cancel()
        sequence += 1L
        snapshot =
            ChromeMediaShieldActiveDocumentHoldSnapshot(
                phase = ChromeMediaShieldActiveDocumentHoldPhase.Armed,
                caseId = caseId,
                stage = stage,
                nonceDigest = digest(nonce),
                sequence = sequence,
            )
        return true
    }

    fun reach(
        stage: String,
        continuation: (Boolean) -> Unit,
    ): ChromeMediaShieldActiveDocumentHoldSnapshot? {
        if (snapshot.phase != ChromeMediaShieldActiveDocumentHoldPhase.Armed || snapshot.stage != stage) return null
        this.continuation = continuation
        snapshot = snapshot.copy(phase = ChromeMediaShieldActiveDocumentHoldPhase.Reached)
        return snapshot
    }

    fun release(
        caseId: String,
        stage: String,
        nonce: String,
    ): ChromeMediaShieldActiveDocumentHoldSnapshot? = complete(caseId, stage, nonce, proceed = true)

    fun cancel(
        caseId: String,
        stage: String,
        nonce: String,
    ): ChromeMediaShieldActiveDocumentHoldSnapshot? = complete(caseId, stage, nonce, proceed = false)

    fun cancel(): ChromeMediaShieldActiveDocumentHoldSnapshot? {
        if (snapshot.phase == ChromeMediaShieldActiveDocumentHoldPhase.Idle) return null
        val previous = snapshot
        val callback = continuation
        continuation = null
        snapshot = ChromeMediaShieldActiveDocumentHoldSnapshot(ChromeMediaShieldActiveDocumentHoldPhase.Idle)
        callback?.invoke(false)
        return previous
    }

    /**
     * Fail-close the held continuation while preserving the same diagnostic
     * capability for the immediately superseding document attempt.
     *
     * This is DEV observability only: it cannot proceed either attempt and it
     * retains no document authority. The next attempt must independently reach
     * the exact configured stage before the runner may release or cancel it.
     */
    fun transferToSupersedingAttempt(): ChromeMediaShieldActiveDocumentHoldSnapshot? {
        if (snapshot.phase == ChromeMediaShieldActiveDocumentHoldPhase.Idle) return null
        val previous = snapshot
        val callback = continuation
        continuation = null
        snapshot = snapshot.copy(phase = ChromeMediaShieldActiveDocumentHoldPhase.Armed)
        callback?.invoke(false)
        return previous
    }

    fun snapshot(): ChromeMediaShieldActiveDocumentHoldSnapshot = snapshot

    private fun complete(
        caseId: String,
        stage: String,
        nonce: String,
        proceed: Boolean,
    ): ChromeMediaShieldActiveDocumentHoldSnapshot? {
        if (
            snapshot.phase == ChromeMediaShieldActiveDocumentHoldPhase.Idle ||
            snapshot.caseId != caseId ||
            snapshot.stage != stage ||
            !nonce.matches(NoncePattern) ||
            snapshot.nonceDigest != digest(nonce)
        ) {
            return null
        }
        val previous = snapshot
        val callback = continuation
        if (proceed && (snapshot.phase != ChromeMediaShieldActiveDocumentHoldPhase.Reached || callback == null)) {
            return null
        }
        continuation = null
        snapshot = ChromeMediaShieldActiveDocumentHoldSnapshot(ChromeMediaShieldActiveDocumentHoldPhase.Idle)
        callback?.invoke(proceed)
        return previous
    }

    private fun String.isAllowedCase(): Boolean = this in AllowedCases

    private fun digest(value: String): String = ChromeMediaShieldDocumentAuthorityRegistry.digestReadyToken(value)

    companion object {
        const val PresentPrecommit = "present_precommit"
        const val PresentPostcommit = "present_postcommit"
        private val AllowedStages =
            setOf("hello_accepted", "challenge_issued", "proof_accepted", PresentPrecommit, PresentPostcommit)
        private val AllowedCases =
            setOf(
                "cold_foreground_release",
                "background_tab_no_release",
                "foreground_a_background_b",
                "switch_during_hello",
                "switch_during_challenge",
                "switch_during_prove_present",
                "rapid_tab_switching",
                "reload",
                "back_forward_bfcache",
                "app_background_foreground",
                "omnibox_focus",
                "form_focus",
                "portrait_landscape",
                "process_restart",
                "stale_replay_token_reuse",
                "root_window_replacement",
            )
        private val NoncePattern = Regex("[0-9a-f]{32}")
    }
}
