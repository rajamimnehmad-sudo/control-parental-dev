package com.contentfilter.feature.accessibility.chromevisual

internal object ChromeVisualShieldLabAvailability {
    fun isEnabled(
        sdkInt: Int,
        packageName: String,
        resourceEnabled: Boolean,
    ): Boolean = sdkInt >= 34 && packageName.endsWith(".dev") && resourceEnabled
}

internal data class ChromeVisualShieldRenderProbeRequest(
    val sampleId: String,
    val sourceSha256: String,
    val renderContract: String,
) {
    fun isValid(): Boolean =
        sampleId.matches(Regex("[a-z0-9-]{1,40}")) &&
            sourceSha256.matches(Regex("[0-9a-f]{64}")) &&
            renderContract.matches(Regex("[a-z0-9-]{1,80}"))
}

internal data class ChromeVisualShieldCropEvidence(
    val width: Int,
    val height: Int,
    val rgbaSha256: String,
)

internal enum class ChromeVisualShieldRenderProbeResult {
    SafeObserved,
    BlockObserved,
    FailClosedObserved,
    StaleDropped,
    IdentityMismatchRejected,
}

/** Diagnostic-only authority: it records R3.1 output and can never release the surface. */
internal class ChromeVisualShieldRenderProbeAuthority(
    private val identityGate: ChromeVisualShieldIdentityGate,
    private val metrics: ChromeVisualShieldR1Metrics,
) {
    fun observe(
        expectedIdentity: ChromeVisualShieldIdentity,
        decision: ChromeVisualShieldGloshiaDecision,
    ): ChromeVisualShieldRenderProbeResult {
        if (expectedIdentity != decision.identity) {
            metrics.onIdentityMismatchRejected()
            identityGate.failClosed(expectedIdentity)
            return ChromeVisualShieldRenderProbeResult.IdentityMismatchRejected
        }
        if (identityGate.completeProcessing(decision.identity) is ChromeVisualShieldResult.Stale) {
            metrics.onStaleInferenceDropped()
            return ChromeVisualShieldRenderProbeResult.StaleDropped
        }
        return when (decision) {
            is ChromeVisualShieldGloshiaDecision.Safe -> ChromeVisualShieldRenderProbeResult.SafeObserved
            is ChromeVisualShieldGloshiaDecision.Block -> ChromeVisualShieldRenderProbeResult.BlockObserved
            is ChromeVisualShieldGloshiaDecision.FailClosed ->
                ChromeVisualShieldRenderProbeResult.FailClosedObserved
        }
    }
}

internal data class ChromeVisualShieldRenderProbeObservation(
    val request: ChromeVisualShieldRenderProbeRequest,
    val identity: ChromeVisualShieldIdentity,
    val crop: ChromeVisualShieldCropEvidence,
    val result: ChromeVisualShieldRenderProbeResult,
    val action: String,
    val reason: String,
    val filterProbability: Float?,
    val inferenceCount: Long,
    val regionalEvidence: ChromeVisualShieldRegionalAnalysisEvidence?,
    val normalizedEvidence: ChromeVisualShieldNormalizedAnalysisEvidence?,
)
