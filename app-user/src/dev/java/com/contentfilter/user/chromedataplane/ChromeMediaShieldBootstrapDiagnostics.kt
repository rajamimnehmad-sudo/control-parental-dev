package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentAuthorityRegistry
import com.contentfilter.core.domain.chrome.ChromeMediaShieldSelfReadyIdentity
import java.util.LinkedHashMap

internal data class ChromeMediaShieldBootstrapDiagnosticMetrics(
    val accepted: Long = 0L,
    val rejected: Long = 0L,
    val lastStage: String = "none",
    val lastReason: String = "none",
    val outstanding: Int = 0,
)

/** Bounded, one-shot DEV observer. It validates an issued document but grants no authority. */
internal class ChromeMediaShieldBootstrapDiagnostics {
    private val recordedByTokenDigest = LinkedHashMap<String, Pair<String, String>>()
    private var accepted = 0L
    private var rejected = 0L
    private var lastStage = "none"
    private var lastReason = "none"

    @Synchronized
    fun record(
        token: String,
        identity: ChromeMediaShieldSelfReadyIdentity,
        stage: String,
        reason: String,
    ): Boolean {
        val validCode = stage.matches(CodePattern) && reason.matches(CodePattern)
        if (!validCode || !ChromeMediaShieldDocumentAuthorityRegistry.validatesUnclaimedSelfReady(token, identity)) {
            rejected += 1L
            return false
        }
        val digest = ChromeMediaShieldDocumentAuthorityRegistry.digestReadyToken(token)
        if (digest in recordedByTokenDigest) {
            rejected += 1L
            return false
        }
        while (recordedByTokenDigest.size >= MaximumDocuments) {
            recordedByTokenDigest.remove(recordedByTokenDigest.keys.first())
        }
        recordedByTokenDigest[digest] = stage to reason
        accepted += 1L
        lastStage = stage
        lastReason = reason
        return true
    }

    @Synchronized
    fun metrics(): ChromeMediaShieldBootstrapDiagnosticMetrics =
        ChromeMediaShieldBootstrapDiagnosticMetrics(
            accepted = accepted,
            rejected = rejected,
            lastStage = lastStage,
            lastReason = lastReason,
            outstanding = recordedByTokenDigest.size,
        )

    private companion object {
        const val MaximumDocuments = 64
        val CodePattern = Regex("[A-Z][A-Z0-9_]{0,47}")
    }
}
