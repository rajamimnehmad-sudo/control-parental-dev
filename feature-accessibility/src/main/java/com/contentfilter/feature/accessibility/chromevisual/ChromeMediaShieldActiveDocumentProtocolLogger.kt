package com.contentfilter.feature.accessibility.chromevisual

import android.util.Log
import com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentAuthorityRegistry
import com.contentfilter.core.domain.chrome.ChromeMediaShieldReadyClaim

/**
 * Bounded protocol observability for the active-document handshake.
 *
 * This helper never decides authority. The coordinator supplies already-computed currentness and
 * metrics so logging cannot become an alternate release boundary.
 */
internal class ChromeMediaShieldActiveDocumentProtocolLogger(
    private val contextCurrent: (ChromeMediaShieldActiveDocumentAttempt) -> Boolean,
    private val surfaceEpoch: () -> Long,
    private val metrics: (String) -> String,
    private val caseId: () -> String,
    private val isClosed: () -> Boolean,
) {
    private var eventSequence = 0L

    fun log(
        phase: String,
        current: ChromeMediaShieldActiveDocumentAttempt,
        reason: String = "healthy",
    ) {
        val sequence = nextSequence()
        val challengeDigest = current.challenge?.encoded?.let(::digest).orEmpty()
        val reasonField =
            reason.takeIf { it != "healthy" }
                ?.let { "reason=${canonicalReason(it)} " }
                .orEmpty()
        Log.i(
            LogTag,
            "protocol=active_document_v3 phase=$phase caseId=${caseId()} eventSequence=$sequence $reasonField" +
                "policyEpoch=${current.claim.identity.policyEpoch} navigationSequence=${current.claim.identity.navigationSequence} " +
                "documentSequence=${current.claim.identity.documentSequence} lifecycle=${current.claim.lifecycleSequence} " +
                "windowId=${current.binding.windowId} surfaceEpoch=${current.surface?.epoch ?: 0L} " +
                "sessionDigest=${digest(current.claim.identity.protectionSessionId).take(DigestPrefixLength)} " +
                "tokenDigest=${current.claim.identity.tokenDigest.take(DigestPrefixLength)} " +
                "challengeDigest=${challengeDigest.take(DigestPrefixLength)} " +
                "rootDigest=${current.binding.nativeRootDigest.take(DigestPrefixLength)} " +
                "rootBinding=${current.binding.nativeRootBindingKind.logValue()} " +
                "current=${contextCurrent(current)} rawPresented=false " + metrics(phase),
        )
    }

    fun logRejected(
        phase: String,
        claim: ChromeMediaShieldReadyClaim,
        reason: String,
    ) {
        val sequence = nextSequence()
        Log.i(
            LogTag,
            "protocol=active_document_v3 phase=$phase caseId=${caseId()} eventSequence=$sequence " +
                "reason=${canonicalReason(reason)} " +
                "policyEpoch=${claim.identity.policyEpoch} navigationSequence=${claim.identity.navigationSequence} " +
                "documentSequence=${claim.identity.documentSequence} lifecycle=${claim.lifecycleSequence} " +
                "windowId=-1 surfaceEpoch=${surfaceEpoch()} current=false rawPresented=false " +
                "sessionDigest=${digest(claim.identity.protectionSessionId).take(DigestPrefixLength)} " +
                "tokenDigest=${claim.identity.tokenDigest.take(DigestPrefixLength)} " +
                "rootDigest=${ChromeMediaShieldActiveDocumentProtocolDiagnostics.zeroDigest.take(DigestPrefixLength)} " +
                "rootBinding=absent " +
                metrics(phase),
        )
    }

    fun logHold(
        phase: String,
        snapshot: ChromeMediaShieldActiveDocumentHoldSnapshot,
        reason: String = "healthy",
    ) {
        val sequence = nextSequence()
        val reasonField = reason.takeIf { it != "healthy" }?.let { "reason=$it " }.orEmpty()
        Log.i(
            LogTag,
            "protocol=active_document_v3 phase=$phase caseId=${snapshot.caseId} eventSequence=$sequence $reasonField" +
                "holdStage=${snapshot.stage} holdDigest=${snapshot.nonceDigest.take(DigestPrefixLength)} " +
                metrics(phase),
        )
    }

    private fun nextSequence(): Long {
        eventSequence = if (eventSequence == Long.MAX_VALUE) eventSequence else eventSequence + 1L
        return eventSequence
    }

    private fun canonicalReason(reason: String): String =
        ChromeMediaShieldActiveDocumentProtocolDiagnostics.canonicalReason(reason, isClosed())

    private fun digest(value: String): String = ChromeMediaShieldDocumentAuthorityRegistry.digestReadyToken(value)

    private fun ChromeMediaShieldNativeRootBindingKind.logValue(): String =
        when (this) {
            ChromeMediaShieldNativeRootBindingKind.PlatformUniqueId -> "platform_unique_id"
            ChromeMediaShieldNativeRootBindingKind.RetainedNode -> "retained_node"
        }
}
