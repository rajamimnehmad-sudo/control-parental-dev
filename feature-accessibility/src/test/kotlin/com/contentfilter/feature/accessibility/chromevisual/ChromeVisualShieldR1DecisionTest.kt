package com.contentfilter.feature.accessibility.chromevisual

import com.glosh.visual.GloshiaVisualAction
import com.glosh.visual.GloshiaVisualDecision
import com.glosh.visual.GloshiaVisualPolicyContract
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChromeVisualShieldR1DecisionTest {
    @Test
    fun `current safe crosses productive authority and releases exactly once`() {
        val harness = Harness(clockValues = ArrayDeque(listOf(100L, 200L)))
        val identity = harness.processingIdentity()

        val result = harness.authority.apply(identity, safe(identity))

        assertEquals(ChromeVisualShieldDecisionResult.SafeReleased, result)
        assertEquals(1, harness.releases)
        assertEquals(ChromeVisualShieldPhase.LabReleased, harness.gate.snapshot().phase)
        assertEquals(1, harness.metrics.snapshot().releaseCurrent)
    }

    @Test
    fun `stale safe crosses same authority and cannot release newer epoch`() {
        val harness = Harness()
        val stale = harness.processingIdentity()
        val before = harness.gate.snapshot().context!!
        harness.gate.invalidate(7, viewport(), contract(), ChromeVisualShieldInvalidation.Scroll)

        val result = harness.authority.apply(stale, safe(stale))

        assertEquals(ChromeVisualShieldDecisionResult.StaleDropped, result)
        assertEquals(0, harness.releases)
        assertEquals(ChromeVisualShieldPhase.Protected, harness.gate.snapshot().phase)
        assertTrue(harness.gate.snapshot().context!!.contentEpoch > before.contentEpoch)
        assertEquals(1, harness.metrics.snapshot().staleInferenceDropped)
    }

    @Test
    fun `embedded decision identity mismatch is rejected and fails closed`() {
        val harness = Harness()
        val expected = harness.processingIdentity()
        val mismatched = expected.copy(regionSequence = expected.regionSequence + 1)

        val result = harness.authority.apply(expected, safe(mismatched))

        assertEquals(ChromeVisualShieldDecisionResult.IdentityMismatchRejected, result)
        assertEquals(0, harness.releases)
        assertEquals(ChromeVisualShieldPhase.Protected, harness.gate.snapshot().phase)
        assertEquals(1, harness.metrics.snapshot().identityMismatchRejected)
        assertEquals(1, harness.metrics.snapshot().releaseRejected)
    }

    @Test
    fun `current block remains protected and never releases`() {
        val harness = Harness()
        val identity = harness.processingIdentity()

        val result =
            harness.authority.apply(
                identity,
                ChromeVisualShieldGloshiaDecision.Block(
                    identity,
                    GloshiaVisualPolicyContract.ModelFilterReason,
                    0.9f,
                ),
            )

        assertEquals(ChromeVisualShieldDecisionResult.BlockProtected, result)
        assertEquals(0, harness.releases)
        assertEquals(ChromeVisualShieldPhase.Protected, harness.gate.snapshot().phase)
    }

    @Test
    fun `controlled analyzer failure is one shot and authority keeps protection`() {
        val harness = Harness()
        val identity = harness.processingIdentity()
        val fault = ChromeVisualShieldAnalyzerFault()
        assertTrue(fault.armOnce())

        val failed = ChromeVisualShieldAnalyzerExecution.decide(identity, fault) { allowDecision() }
        val result = harness.authority.apply(identity, failed)

        assertIs<ChromeVisualShieldGloshiaDecision.FailClosed>(failed)
        assertEquals(ChromeVisualShieldDecisionResult.FailClosed, result)
        assertEquals(0, harness.releases)
        assertEquals(ChromeVisualShieldPhase.Protected, harness.gate.snapshot().phase)
        assertIs<ChromeVisualShieldGloshiaDecision.Safe>(
            ChromeVisualShieldAnalyzerExecution.decide(identity, fault) { allowDecision() },
        )
    }

    @Test
    fun `safe and release timestamps are separate and ordered`() {
        val harness = Harness(clockValues = ArrayDeque(listOf(10L, 25L)))
        val identity = harness.processingIdentity()

        harness.authority.apply(identity, safe(identity))

        val snapshot = harness.metrics.snapshot()
        assertEquals(10L, snapshot.safeDecisionAtNanos)
        assertEquals(25L, snapshot.releaseAtNanos)
        assertTrue(snapshot.safeDecisionAtNanos <= snapshot.releaseAtNanos)
    }

    @Test
    fun `inference completion without start is test visible`() {
        val metrics = ChromeVisualShieldR1Metrics()
        assertFailsWith<IllegalStateException> { metrics.onInferenceCompleted() }
    }

    @Test
    fun `inference lifecycle exposes outstanding and peak without masking underflow`() {
        val metrics = ChromeVisualShieldR1Metrics()
        metrics.onInferenceStarted()
        metrics.onInferenceStarted()
        metrics.onInferenceCompleted()
        metrics.onInferenceCompleted()

        val snapshot = metrics.snapshot()
        assertEquals(2, snapshot.inferenceStarted)
        assertEquals(2, snapshot.inferenceCompleted)
        assertEquals(0, snapshot.inferenceOutstanding)
        assertEquals(2, snapshot.inferencePeakOutstanding)
    }

    @Test
    fun `unexpected visual allow reason maps to fail closed`() {
        val mapped =
            ChromeVisualShieldGloshiaDecisionPolicy.map(
                identity(),
                GloshiaVisualDecision(
                    candidateId = "fixture",
                    action = GloshiaVisualAction.Allow,
                    reason = GloshiaVisualPolicyContract.AnalysisExpiredReason,
                ),
            )

        assertIs<ChromeVisualShieldGloshiaDecision.FailClosed>(mapped)
    }

    private class Harness(
        private val clockValues: ArrayDeque<Long> = ArrayDeque(listOf(1L, 2L)),
    ) {
        val gate = ChromeVisualShieldIdentityGate()
        val metrics = ChromeVisualShieldR1Metrics()
        var releases = 0
        val authority =
            ChromeVisualShieldDecisionAuthority(
                identityGate = gate,
                metrics = metrics,
                releaseSurface = { releases += 1 },
                monotonicNanos = { clockValues.removeFirst() },
            )

        fun processingIdentity(): ChromeVisualShieldIdentity {
            if (gate.snapshot().context == null) gate.start(7, viewport(), contract())
            val identity = checkNotNull(gate.beginCapture())
            assertEquals(ChromeVisualShieldResult.Current, gate.beginProcessing(identity))
            return identity
        }
    }

    private companion object {
        fun viewport() = ChromeVisualViewport(0, 0, 1080, 2200)

        fun contract() = ChromeVisualShieldRegionContract("fixture", 100, 100, 9000, 9000, "signed")

        fun safe(identity: ChromeVisualShieldIdentity) =
            ChromeVisualShieldGloshiaDecision.Safe(
                identity,
                GloshiaVisualPolicyContract.ModelAllowReason,
                0.1f,
            )

        fun allowDecision() =
            GloshiaVisualDecision(
                candidateId = "fixture",
                action = GloshiaVisualAction.Allow,
                reason = GloshiaVisualPolicyContract.ModelAllowReason,
                filterProbability = 0.1f,
            )

        fun identity() =
            ChromeVisualShieldIdentity(
                protectionSessionId = 1,
                windowId = 7,
                contentEpoch = 3,
                viewport = viewport(),
                viewportEpoch = 2,
                captureSequence = 5,
                regionId = "fixture",
                regionSequence = 4,
                region = ChromeVisualRegion("fixture", 100, 100, 900, 900),
            )
    }
}
