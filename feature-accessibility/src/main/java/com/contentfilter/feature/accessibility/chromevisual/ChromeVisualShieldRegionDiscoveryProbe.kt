package com.contentfilter.feature.accessibility.chromevisual

import com.glosh.visual.GloshiaVisualDecision

internal data class ChromeVisualShieldRegionDiscoveryProbeRequest(
    val scenarioId: String,
    val sourceSha256s: List<String>,
    val renderContract: String,
) {
    fun isValid(): Boolean =
        scenarioId.matches(Regex("[a-z0-9-]{1,48}")) &&
            sourceSha256s.isNotEmpty() &&
            sourceSha256s.size <= MaximumSources &&
            sourceSha256s.all { it.matches(Regex("[0-9a-f]{64}")) } &&
            sourceSha256s.distinct().size == sourceSha256s.size &&
            renderContract.matches(Regex("[a-z0-9-]{1,80}"))

    private companion object {
        const val MaximumSources = 8
    }
}

internal data class ChromeVisualShieldRegionDecision(
    val region: ChromeVisualShieldDiscoveredRegion,
    val decision: GloshiaVisualDecision,
)

internal data class ChromeVisualShieldRegionDiscoveryDelivery(
    val work: ChromeVisualShieldWork,
    val searchEnvelope: ChromeVisualRegion,
    val cropEvidence: ChromeVisualShieldCropEvidence,
    val discovery: ChromeVisualShieldRegionDiscoveryResult,
    val decisions: List<ChromeVisualShieldRegionDecision>,
)

internal enum class ChromeVisualShieldRegionDiscoveryAuthorityResult {
    CompleteObserved,
    UnknownObserved,
    StaleDropped,
    IdentityMismatchRejected,
}

/** R2A is diagnostic only. Completing current processing never releases the protected surface. */
internal class ChromeVisualShieldRegionDiscoveryAuthority(
    private val identityGate: ChromeVisualShieldIdentityGate,
    private val metrics: ChromeVisualShieldR1Metrics,
) {
    fun observe(delivery: ChromeVisualShieldRegionDiscoveryDelivery): ChromeVisualShieldRegionDiscoveryAuthorityResult {
        val expected = delivery.work.identity
        if (delivery.decisions.any { it.decision.candidateId != it.region.id }) {
            metrics.onIdentityMismatchRejected()
            identityGate.failClosed(expected)
            return ChromeVisualShieldRegionDiscoveryAuthorityResult.IdentityMismatchRejected
        }
        if (identityGate.completeProcessing(expected) is ChromeVisualShieldResult.Stale) {
            metrics.onStaleInferenceDropped()
            return ChromeVisualShieldRegionDiscoveryAuthorityResult.StaleDropped
        }
        return when (delivery.discovery) {
            is ChromeVisualShieldRegionDiscoveryResult.Complete ->
                ChromeVisualShieldRegionDiscoveryAuthorityResult.CompleteObserved
            is ChromeVisualShieldRegionDiscoveryResult.Unknown ->
                ChromeVisualShieldRegionDiscoveryAuthorityResult.UnknownObserved
        }
    }
}

internal data class ChromeVisualShieldRegionDiscoveryObservation(
    val request: ChromeVisualShieldRegionDiscoveryProbeRequest,
    val identity: ChromeVisualShieldIdentity,
    val searchEnvelope: ChromeVisualRegion,
    val crop: ChromeVisualShieldCropEvidence,
    val discovery: ChromeVisualShieldRegionDiscoveryResult,
    val decisions: List<ChromeVisualShieldRegionDecision>,
    val authorityResult: ChromeVisualShieldRegionDiscoveryAuthorityResult,
    val oracleMatch: Boolean?,
    val oracleVerification: ChromeVisualShieldRegionDiscoveryOracleVerifier.Verification?,
)
