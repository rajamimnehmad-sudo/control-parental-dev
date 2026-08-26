package com.contentfilter.feature.accessibility.chromevisual

import com.glosh.visual.GloshiaVisualAction
import com.glosh.visual.GloshiaVisualDecision
import com.glosh.visual.GloshiaVisualPolicyContract
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChromeVisualShieldR1DecisionTest {
    @Test
    fun `model allow is the only decision mapped to safe`() {
        val mapped =
            ChromeVisualShieldGloshiaDecisionPolicy.map(
                identity(),
                GloshiaVisualDecision(
                    candidateId = "fixture",
                    action = GloshiaVisualAction.Allow,
                    reason = GloshiaVisualPolicyContract.ModelAllowReason,
                    filterProbability = 0.1f,
                ),
            )

        assertIs<ChromeVisualShieldGloshiaDecision.Safe>(mapped)
    }

    @Test
    fun `model filter maps to block`() {
        val mapped =
            ChromeVisualShieldGloshiaDecisionPolicy.map(
                identity(),
                GloshiaVisualDecision(
                    candidateId = "fixture",
                    action = GloshiaVisualAction.Block,
                    reason = GloshiaVisualPolicyContract.ModelFilterReason,
                    filterProbability = 0.9f,
                ),
            )

        assertIs<ChromeVisualShieldGloshiaDecision.Block>(mapped)
    }

    @Test
    fun `unavailable and unexpected allow reasons fail closed`() {
        val unavailable =
            ChromeVisualShieldGloshiaDecisionPolicy.map(
                identity(),
                GloshiaVisualDecision(
                    candidateId = "fixture",
                    action = GloshiaVisualAction.Block,
                    reason = GloshiaVisualPolicyContract.AnalyzerUnavailableReason,
                ),
            )
        val unexpectedAllow =
            ChromeVisualShieldGloshiaDecisionPolicy.map(
                identity(),
                GloshiaVisualDecision(
                    candidateId = "fixture",
                    action = GloshiaVisualAction.Allow,
                    reason = GloshiaVisualPolicyContract.AnalysisExpiredReason,
                ),
            )

        assertIs<ChromeVisualShieldGloshiaDecision.FailClosed>(unavailable)
        assertIs<ChromeVisualShieldGloshiaDecision.FailClosed>(unexpectedAllow)
    }

    @Test
    fun `coalescer accepts only exact eligible duplicates while work is active`() {
        val viewport = ChromeVisualViewport(0, 0, 1080, 2200)
        val first = ChromeVisualShieldEventFingerprint(2048, 100L, 1, 7, viewport)
        val changedTime = first.copy(eventTime = 101L)
        val coalescer = ChromeVisualShieldEventCoalescer()

        assertFalse(coalescer.shouldCoalesce(first, ChromeVisualShieldPhase.CapturePending, eligible = true))
        assertTrue(coalescer.shouldCoalesce(first, ChromeVisualShieldPhase.CapturePending, eligible = true))
        assertFalse(coalescer.shouldCoalesce(changedTime, ChromeVisualShieldPhase.Processing, eligible = true))
        assertFalse(coalescer.shouldCoalesce(changedTime, ChromeVisualShieldPhase.Protected, eligible = true))
        assertFalse(coalescer.shouldCoalesce(changedTime, ChromeVisualShieldPhase.Processing, eligible = false))
    }

    private fun identity() =
        ChromeVisualShieldIdentity(
            protectionSessionId = 1,
            windowId = 7,
            contentEpoch = 3,
            viewport = ChromeVisualViewport(0, 0, 1080, 2200),
            viewportEpoch = 2,
            captureSequence = 5,
            regionId = "fixture",
            regionSequence = 4,
            region = ChromeVisualRegion("fixture", 100, 100, 900, 900),
        )
}
