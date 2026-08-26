package com.contentfilter.feature.accessibility.chromevisual

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import com.contentfilter.feature.accessibility.ChromeVisualGloshiaEngineProvider
import com.glosh.visual.GloshiaImageCropPlan
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

internal data class ChromeVisualShieldRegionalAnalysisEvidence(
    val cropPlans: List<GloshiaImageCropPlan>,
    val preparedImageCount: Int,
    val regionalImageCount: Int,
    val probabilities: List<Float>,
    val basis: GloshiaVisualDecisionBasis,
    val fullImageProbability: Float?,
)

internal data class ChromeVisualShieldGloshiaAnalysis(
    val decision: ChromeVisualShieldGloshiaDecision,
    val regionalEvidence: ChromeVisualShieldRegionalAnalysisEvidence?,
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
        includeCanonicalRegions: Boolean = false,
    ): ChromeVisualShieldGloshiaAnalysis {
        if (!canContinue()) {
            return ChromeVisualShieldGloshiaAnalysis(
                ChromeVisualShieldGloshiaDecision.FailClosed(
                    identity,
                    GloshiaVisualPolicyContract.AnalysisExpiredReason,
                ),
                null,
            )
        }
        val views =
            ChromeVisualShieldCapturedRasterViews.prepare(bitmap, includeCanonicalRegions)
                ?: return ChromeVisualShieldGloshiaAnalysis(
                    ChromeVisualShieldGloshiaDecision.FailClosed(
                        identity,
                        GloshiaVisualPolicyContract.DecodeFailedReason,
                    ),
                    null,
                )
        views.use { ownedViews ->
            val currentAnalyzer =
                engine()
                    ?: return ChromeVisualShieldGloshiaAnalysis(
                        ChromeVisualShieldGloshiaDecision.FailClosed(
                            identity,
                            GloshiaVisualPolicyContract.AnalyzerUnavailableReason,
                        ),
                        null,
                    )
            val probabilities = mutableListOf<Float>()
            var policyDecision: GloshiaVisualDecision? = null
            val decision =
                ChromeVisualShieldAnalyzerExecution.decide(identity, fault) {
                    GloshiaPreparedRasterPolicy.decide(
                        candidateId = identity.regionId,
                        preparedImages = ownedViews.preparedImages,
                        analyzer = currentAnalyzer,
                        canContinue = canContinue,
                        analyze = { analyzer, image ->
                            analyzer.analyze(image).also { result ->
                                if (result is GloshiaVisualAnalysisResult.Classified) {
                                    probabilities += result.filterProbability
                                }
                            }
                        },
                    ).also { policyDecision = it }
                }
            val evidence =
                policyDecision?.let { result ->
                    ChromeVisualShieldRegionalAnalysisEvidence(
                        cropPlans = ownedViews.cropPlans,
                        preparedImageCount = result.preparedImageCount,
                        regionalImageCount = result.regionalImageCount,
                        probabilities = probabilities.toList(),
                        basis = result.basis,
                        fullImageProbability = result.fullImageProbability,
                    )
                }
            return ChromeVisualShieldGloshiaAnalysis(decision, evidence)
        }
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
