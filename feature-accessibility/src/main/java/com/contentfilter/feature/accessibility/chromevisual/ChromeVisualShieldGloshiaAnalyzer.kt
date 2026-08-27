package com.contentfilter.feature.accessibility.chromevisual

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import com.contentfilter.feature.accessibility.ChromeVisualGloshiaEngineProvider
import com.glosh.visual.AndroidGloshiaImagePreprocessor
import com.glosh.visual.GloshiaPreparedRasterPolicy
import com.glosh.visual.GloshiaVisualAction
import com.glosh.visual.GloshiaVisualAnalysisResult
import com.glosh.visual.GloshiaVisualAnalyzer
import com.glosh.visual.GloshiaVisualDecision
import com.glosh.visual.GloshiaVisualDecisionBasis
import com.glosh.visual.GloshiaVisualPolicyContract
import com.glosh.visual.LifecycleGloshiaVisualAnalyzer

internal sealed interface ChromeVisualShieldGloshiaDecision {
    val identity: ChromeVisualShieldIdentity
    val reason: String
    val filterProbability: Float?

    data class Safe(
        override val identity: ChromeVisualShieldIdentity,
        override val reason: String,
        override val filterProbability: Float?,
    ) : ChromeVisualShieldGloshiaDecision

    data class Block(
        override val identity: ChromeVisualShieldIdentity,
        override val reason: String,
        override val filterProbability: Float?,
    ) : ChromeVisualShieldGloshiaDecision

    data class FailClosed(
        override val identity: ChromeVisualShieldIdentity,
        override val reason: String,
        override val filterProbability: Float? = null,
    ) : ChromeVisualShieldGloshiaDecision
}

internal object ChromeVisualShieldGloshiaDecisionPolicy {
    fun map(
        identity: ChromeVisualShieldIdentity,
        decision: GloshiaVisualDecision,
    ): ChromeVisualShieldGloshiaDecision =
        when {
            decision.action == GloshiaVisualAction.Allow &&
                decision.reason == GloshiaVisualPolicyContract.ModelAllowReason ->
                ChromeVisualShieldGloshiaDecision.Safe(
                    identity = identity,
                    reason = decision.reason,
                    filterProbability = decision.filterProbability,
                )
            decision.action == GloshiaVisualAction.Block &&
                decision.reason == GloshiaVisualPolicyContract.ModelFilterReason ->
                ChromeVisualShieldGloshiaDecision.Block(
                    identity = identity,
                    reason = decision.reason,
                    filterProbability = decision.filterProbability,
                )
            else ->
                ChromeVisualShieldGloshiaDecision.FailClosed(
                    identity = identity,
                    reason = decision.reason,
                    filterProbability = decision.filterProbability,
                )
        }
}

internal data class ChromeVisualShieldNormalizedAnalysisEvidence(
    val action: GloshiaVisualAction,
    val reason: String,
    val filterProbability: Float?,
    val basis: GloshiaVisualDecisionBasis,
    val preparedImageCount: Int,
    val regionalImageCount: Int,
    val modelInferenceCount: Int,
)

/**
 * R1 adapter from the RAM-only Visual Shield crop to the existing GloshIA R3.1 policy.
 * The prepared RGB buffer is always zeroed before returning and inference is serialized.
 */
internal class ChromeVisualShieldGloshiaAnalyzer(
    private val service: AccessibilityService,
    private val fault: ChromeVisualShieldAnalyzerFault,
) : AutoCloseable {
    private val lock = Any()
    private var analyzer: LifecycleGloshiaVisualAnalyzer? = null

    suspend fun analyze(
        bitmap: Bitmap,
        identity: ChromeVisualShieldIdentity,
        canContinue: () -> Boolean,
    ): ChromeVisualShieldGloshiaDecision {
        if (!canContinue()) {
            return ChromeVisualShieldGloshiaDecision.FailClosed(
                identity,
                GloshiaVisualPolicyContract.AnalysisExpiredReason,
            )
        }
        val prepared =
            runCatching {
                AndroidGloshiaImagePreprocessor.prepareVideoCapturedRaster(
                    bitmap = bitmap,
                    maxLongEdge = maxOf(bitmap.width, bitmap.height),
                )
            }.getOrNull()
                ?: return ChromeVisualShieldGloshiaDecision.FailClosed(
                    identity,
                    GloshiaVisualPolicyContract.DecodeFailedReason,
                )
        try {
            val currentAnalyzer =
                engine()
                    ?: return ChromeVisualShieldGloshiaDecision.FailClosed(
                        identity,
                        GloshiaVisualPolicyContract.AnalyzerUnavailableReason,
                    )
            return ChromeVisualShieldAnalyzerExecution.decide(identity, fault) {
                GloshiaPreparedRasterPolicy.decide(
                    candidateId = identity.regionId,
                    preparedImages = listOf(prepared),
                    analyzer = currentAnalyzer,
                    canContinue = canContinue,
                )
            }
        } finally {
            prepared.rgb888.fill(0)
        }
    }

    fun analyzeNormalizedProbe(
        bitmap: Bitmap,
        identity: ChromeVisualShieldIdentity,
        canContinue: () -> Boolean,
    ): ChromeVisualShieldNormalizedAnalysisEvidence {
        val currentAnalyzer =
            engine()
                ?: return ChromeVisualShieldNormalizedAnalysisEvidence(
                    action = GloshiaVisualAction.Block,
                    reason = GloshiaVisualPolicyContract.AnalyzerUnavailableReason,
                    filterProbability = null,
                    basis = GloshiaVisualDecisionBasis.None,
                    preparedImageCount = 0,
                    regionalImageCount = 0,
                    modelInferenceCount = 0,
                )
        val normalized = ChromeVisualShieldNormalizedRaster.prepare(bitmap)
        val modelResults = mutableListOf<GloshiaVisualAnalysisResult>()
        val policyDecision =
            if (normalized == null) {
                GloshiaPreparedRasterPolicy.decide(
                    candidateId = identity.regionId,
                    preparedImages = emptyList(),
                    analyzer = currentAnalyzer,
                    canContinue = canContinue,
                )
            } else {
                normalized.use { owned ->
                    ChromeVisualShieldNormalizedRasterPolicyProbe.decide(
                        candidateId = identity.regionId,
                        preparedImage = owned.preparedImage,
                        analyzer = currentAnalyzer,
                        canContinue = canContinue,
                        onModelResult = modelResults::add,
                    )
                }
            }
        return ChromeVisualShieldNormalizedAnalysisEvidence(
            action = policyDecision.action,
            reason = policyDecision.reason,
            filterProbability = policyDecision.filterProbability,
            basis = policyDecision.basis,
            preparedImageCount = policyDecision.preparedImageCount,
            regionalImageCount = policyDecision.regionalImageCount,
            modelInferenceCount = modelResults.size,
        )
    }

    override fun close() {
        synchronized(lock) {
            analyzer?.close()
            analyzer = null
        }
    }

    private fun engine(): GloshiaVisualAnalyzer? =
        synchronized(lock) {
            analyzer
                ?: ChromeVisualGloshiaEngineProvider.create(service)?.let { created ->
                    LifecycleGloshiaVisualAnalyzer(created).also { analyzer = it }
                }
        }
}
