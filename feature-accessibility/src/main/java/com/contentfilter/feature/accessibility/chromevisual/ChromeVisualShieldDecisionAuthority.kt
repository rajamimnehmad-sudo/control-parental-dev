package com.contentfilter.feature.accessibility.chromevisual

import android.os.SystemClock

internal enum class ChromeVisualShieldDecisionResult {
    SafeReleased,
    BlockProtected,
    FailClosed,
    StaleDropped,
    IdentityMismatchRejected,
    ReleaseRejected,
}

/** The only R1 boundary allowed to turn a GloshIA decision into a surface release. */
internal class ChromeVisualShieldDecisionAuthority(
    private val identityGate: ChromeVisualShieldIdentityGate,
    private val metrics: ChromeVisualShieldR1Metrics,
    private val releaseSurface: () -> Unit,
    private val monotonicNanos: () -> Long = SystemClock::elapsedRealtimeNanos,
) {
    fun apply(
        expectedCycleIdentity: ChromeVisualShieldIdentity,
        decision: ChromeVisualShieldGloshiaDecision,
    ): ChromeVisualShieldDecisionResult {
        if (expectedCycleIdentity != decision.identity) {
            metrics.onIdentityMismatchRejected()
            metrics.onReleaseRejected()
            identityGate.failClosed(expectedCycleIdentity)
            return ChromeVisualShieldDecisionResult.IdentityMismatchRejected
        }

        if (identityGate.completeProcessing(decision.identity) is ChromeVisualShieldResult.Stale) {
            metrics.onStaleInferenceDropped()
            metrics.onReleaseRejected()
            return ChromeVisualShieldDecisionResult.StaleDropped
        }

        return when (decision) {
            is ChromeVisualShieldGloshiaDecision.Safe -> applyCurrentSafe(decision.identity)
            is ChromeVisualShieldGloshiaDecision.Block -> {
                metrics.onBlockCurrent()
                ChromeVisualShieldDecisionResult.BlockProtected
            }
            is ChromeVisualShieldGloshiaDecision.FailClosed -> {
                metrics.onFailClosedCurrent()
                ChromeVisualShieldDecisionResult.FailClosed
            }
        }
    }

    private fun applyCurrentSafe(identity: ChromeVisualShieldIdentity): ChromeVisualShieldDecisionResult {
        metrics.onSafeCurrent()
        metrics.onSafeDecisionAccepted(monotonicNanos())
        if (!identityGate.releaseForExplicitLabGate(identity.toContext())) {
            metrics.onReleaseRejected()
            identityGate.failClosed(null)
            return ChromeVisualShieldDecisionResult.ReleaseRejected
        }
        releaseSurface()
        metrics.onReleaseCurrent(monotonicNanos())
        return ChromeVisualShieldDecisionResult.SafeReleased
    }
}

internal fun ChromeVisualShieldIdentity.toContext() =
    ChromeVisualShieldContext(
        protectionSessionId = protectionSessionId,
        windowId = windowId,
        contentEpoch = contentEpoch,
        viewport = viewport,
        viewportEpoch = viewportEpoch,
        regionId = regionId,
        regionSequence = regionSequence,
        region = region,
    )
