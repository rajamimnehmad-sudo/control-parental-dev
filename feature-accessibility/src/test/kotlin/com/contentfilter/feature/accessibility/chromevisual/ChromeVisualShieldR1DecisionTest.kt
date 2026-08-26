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
    fun `safe from portrait is stale after viewport rotates to landscape`() {
        val harness = Harness()
        val portraitSafe = harness.processingIdentity()
        val landscape = ChromeVisualViewport(0, 0, 2200, 1080)
        harness.gate.invalidate(7, landscape, contract(), ChromeVisualShieldInvalidation.Viewport)

        val result = harness.authority.apply(portraitSafe, safe(portraitSafe))

        assertEquals(ChromeVisualShieldDecisionResult.StaleDropped, result)
        assertEquals(0, harness.releases)
        assertEquals(ChromeVisualShieldPhase.Protected, harness.gate.snapshot().phase)
        assertEquals(landscape, harness.gate.snapshot().context!!.viewport)
    }

    @Test
    fun `new viewport releases only after opaque redraw current attestation and current safe`() {
        val harness = Harness()
        val oldSafe = harness.processingIdentity()
        val landscape = ChromeVisualViewport(0, 0, 2200, 1080)
        val protected =
            harness.gate.invalidate(7, landscape, contract(), ChromeVisualShieldInvalidation.Rotation)!!
        val context = protected.context!!
        val renderGate = ChromeVisualShieldViewportRenderGate()
        renderGate.requireCurrentRender(context)
        renderGate.recordOpaqueCommit(context)

        assertTrue(!renderGate.consumeCapturePermission(context))
        assertEquals(
            ChromeVisualShieldDecisionResult.StaleDropped,
            harness.authority.apply(oldSafe, safe(oldSafe)),
        )
        assertEquals(0, harness.releases)
        assertTrue(renderGate.recordAttestation(context.renderIdentityToken(), context))
        assertTrue(renderGate.consumeCapturePermission(context))
        val current = harness.processingIdentity()

        assertEquals(ChromeVisualShieldDecisionResult.SafeReleased, harness.authority.apply(current, safe(current)))
        assertEquals(1, harness.releases)
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
    fun `block remains protected before and after viewport rotation`() {
        val harness = Harness()
        val portrait = harness.processingIdentity()
        assertEquals(
            ChromeVisualShieldDecisionResult.BlockProtected,
            harness.authority.apply(portrait, block(portrait)),
        )
        harness.gate.invalidate(
            7,
            ChromeVisualViewport(0, 0, 2200, 1080),
            contract(),
            ChromeVisualShieldInvalidation.Rotation,
        )
        val landscape = harness.processingIdentity()

        assertEquals(
            ChromeVisualShieldDecisionResult.BlockProtected,
            harness.authority.apply(landscape, block(landscape)),
        )
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

    @Test
    fun `render probe observes safe but never crosses release authority`() {
        val harness = Harness()
        val identity = harness.processingIdentity()

        val result = harness.probe.observe(identity, safe(identity))

        assertEquals(ChromeVisualShieldRenderProbeResult.SafeObserved, result)
        assertEquals(0, harness.releases)
        assertEquals(0, harness.metrics.snapshot().releaseCurrent)
        assertEquals(ChromeVisualShieldPhase.Protected, harness.gate.snapshot().phase)
    }

    @Test
    fun `render probe block and failure remain protected`() {
        listOf(
            ChromeVisualShieldGloshiaDecision.Block(
                identity(),
                GloshiaVisualPolicyContract.ModelFilterReason,
                0.9f,
            ),
            ChromeVisualShieldGloshiaDecision.FailClosed(
                identity(),
                GloshiaVisualPolicyContract.AnalyzerUnavailableReason,
            ),
        ).forEachIndexed { index, candidate ->
            val harness = Harness()
            val expected = harness.processingIdentity()
            val decision =
                when (candidate) {
                    is ChromeVisualShieldGloshiaDecision.Block -> candidate.copy(identity = expected)
                    is ChromeVisualShieldGloshiaDecision.FailClosed -> candidate.copy(identity = expected)
                    is ChromeVisualShieldGloshiaDecision.Safe -> error("not used")
                }

            val result = harness.probe.observe(expected, decision)
            val expectedResult =
                if (index == 0) {
                    ChromeVisualShieldRenderProbeResult.BlockObserved
                } else {
                    ChromeVisualShieldRenderProbeResult.FailClosedObserved
                }

            assertEquals(expectedResult, result)
            assertEquals(0, harness.releases)
            assertEquals(ChromeVisualShieldPhase.Protected, harness.gate.snapshot().phase)
        }
    }

    @Test
    fun `render probe stale and identity mismatch can never release`() {
        val staleHarness = Harness()
        val stale = staleHarness.processingIdentity()
        staleHarness.gate.invalidate(7, viewport(), contract(), ChromeVisualShieldInvalidation.Scroll)
        assertEquals(
            ChromeVisualShieldRenderProbeResult.StaleDropped,
            staleHarness.probe.observe(stale, safe(stale)),
        )

        val mismatchHarness = Harness()
        val expected = mismatchHarness.processingIdentity()
        assertEquals(
            ChromeVisualShieldRenderProbeResult.IdentityMismatchRejected,
            mismatchHarness.probe.observe(expected, safe(expected.copy(regionSequence = 999))),
        )
        assertEquals(0, staleHarness.releases)
        assertEquals(0, mismatchHarness.releases)
        assertEquals(ChromeVisualShieldPhase.Protected, staleHarness.gate.snapshot().phase)
        assertEquals(ChromeVisualShieldPhase.Protected, mismatchHarness.gate.snapshot().phase)
    }

    @Test
    fun `render probe safe is stale across viewport boundary and never releases`() {
        val harness = Harness()
        val portrait = harness.processingIdentity()
        harness.gate.invalidate(
            7,
            ChromeVisualViewport(0, 0, 2200, 1080),
            contract(),
            ChromeVisualShieldInvalidation.Rotation,
        )

        assertEquals(
            ChromeVisualShieldRenderProbeResult.StaleDropped,
            harness.probe.observe(portrait, safe(portrait)),
        )
        assertEquals(0, harness.releases)
        assertEquals(ChromeVisualShieldPhase.Protected, harness.gate.snapshot().phase)
    }

    @Test
    fun `render probe request is strict diagnostic metadata`() {
        assertTrue(
            ChromeVisualShieldRenderProbeRequest(
                "flickr-01",
                "a".repeat(64),
                "canvas-contain-neutral-v1",
            ).isValid(),
        )
        assertTrue(!ChromeVisualShieldRenderProbeRequest("../bad", "x", "").isValid())
    }

    @Test
    fun `render probe controller is unavailable outside api34 dev lab builds`() {
        assertTrue(ChromeVisualShieldLabAvailability.isEnabled(34, "com.contentfilter.user.dev", true))
        assertTrue(!ChromeVisualShieldLabAvailability.isEnabled(33, "com.contentfilter.user.dev", true))
        assertTrue(!ChromeVisualShieldLabAvailability.isEnabled(34, "com.contentfilter.user", true))
        assertTrue(!ChromeVisualShieldLabAvailability.isEnabled(34, "com.contentfilter.user.dev", false))
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
        val probe = ChromeVisualShieldRenderProbeAuthority(gate, metrics)

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

        fun block(identity: ChromeVisualShieldIdentity) =
            ChromeVisualShieldGloshiaDecision.Block(
                identity,
                GloshiaVisualPolicyContract.ModelFilterReason,
                0.9f,
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
