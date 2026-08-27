package com.contentfilter.feature.accessibility.chromevisual

import com.glosh.visual.GloshiaImageContract
import com.glosh.visual.GloshiaPreparedImage
import com.glosh.visual.GloshiaVisualAction
import com.glosh.visual.GloshiaVisualAnalysisResult
import com.glosh.visual.GloshiaVisualAnalyzer
import com.glosh.visual.GloshiaVisualDecisionBasis
import com.glosh.visual.GloshiaVisualPolicyContract
import kotlin.test.Test
import kotlin.test.assertEquals

class ChromeVisualShieldNormalizedRasterProbeTest {
    @Test
    fun `normalized safe uses the real policy and one model inference`() {
        val harness = Harness(0.1f)

        val decision = harness.decide()

        assertEquals(GloshiaVisualAction.Allow, decision.action)
        assertEquals(GloshiaVisualPolicyContract.ModelAllowReason, decision.reason)
        assertEquals(0.1f, decision.filterProbability)
        assertEquals(GloshiaVisualDecisionBasis.None, decision.basis)
        assertEquals(1, harness.calls)
    }

    @Test
    fun `normalized block uses the real policy and one model inference`() {
        val harness = Harness(0.75f)

        val decision = harness.decide()

        assertEquals(GloshiaVisualAction.Block, decision.action)
        assertEquals(GloshiaVisualPolicyContract.ModelFilterReason, decision.reason)
        assertEquals(0.75f, decision.filterProbability)
        assertEquals(GloshiaVisualDecisionBasis.FullThreshold, decision.basis)
        assertEquals(1, harness.calls)
    }

    @Test
    fun `uncertain policy replay uses the identical prepared raster without another inference`() {
        val harness = Harness(0.35f)

        val decision = harness.decide()

        assertEquals(GloshiaVisualAction.Allow, decision.action)
        assertEquals(GloshiaVisualPolicyContract.ModelAllowReason, decision.reason)
        assertEquals(0.35f, decision.filterProbability)
        assertEquals(2, decision.preparedImageCount)
        assertEquals(1, decision.regionalImageCount)
        assertEquals(1, harness.calls)
    }

    private class Harness(
        probability: Float,
    ) {
        private val prepared =
            GloshiaPreparedImage(
                width = GloshiaImageContract.TargetSize,
                height = GloshiaImageContract.TargetSize,
                rgb888 = ByteArray(GloshiaImageContract.PreparedByteCount),
            )
        private val analyzer =
            GloshiaVisualAnalyzer {
                calls += 1
                GloshiaVisualAnalysisResult.Classified(probability)
            }
        var calls = 0
            private set

        fun decide() =
            ChromeVisualShieldNormalizedRasterPolicyProbe.decide(
                candidateId = "fixture",
                preparedImage = prepared,
                analyzer = analyzer,
                canContinue = { true },
            )
    }
}
