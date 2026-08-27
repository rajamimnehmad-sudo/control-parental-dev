package com.contentfilter.feature.accessibility.chromevisual

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChromeVisualShieldRegionDiscoveryEpochBindingTest {
    @Test
    fun `E21 attestation never authorizes E22 capture planner or inference`() {
        val fixture = Fixture()
        val binding21 = fixture.attestCurrent()

        val capture21 = assertNotNull(fixture.schedule())
        assertEquals(binding21.contentEpoch, capture21.contentEpoch)
        assertEquals(binding21.regionSequence, capture21.regionSequence)

        fixture.invalidate(ChromeVisualShieldInvalidation.Navigation)

        assertNull(fixture.schedule())
        assertEquals(1, fixture.captureCount)
        assertEquals(0, fixture.plannerCount)
        assertEquals(0, fixture.inferenceCount)
        assertEquals(ChromeVisualShieldPhase.Protected, fixture.gate.snapshot().phase)
        assertEquals(
            ChromeVisualShieldRegionDiscoveryGenerationOutcome.Invalidated,
            fixture.lab.awaitGeneration(binding21, 0),
        )
    }

    @Test
    fun `fresh attestation authorizes only the exact current generation`() {
        val fixture = Fixture()
        fixture.attestCurrent()
        fixture.invalidate(ChromeVisualShieldInvalidation.Navigation)
        val binding22 = fixture.attestCurrent()

        val capture22 = assertNotNull(fixture.schedule())

        assertTrue(binding22.matches(capture22))
        assertEquals(binding22, fixture.lastMode?.binding)
        assertEquals(1, fixture.captureCount)
    }

    @Test
    fun `stale oracle never crosses content or region epoch`() {
        val fixture = Fixture()
        val staleBinding = fixture.attestCurrent()
        val staleOracle = fixture.oracle(staleBinding)
        fixture.invalidate(ChromeVisualShieldInvalidation.Scroll)
        val current = assertNotNull(fixture.gate.snapshot().context)

        assertFalse(fixture.lab.hasCurrentBinding(current))
        assertFalse(fixture.lab.recordAttestation(staleBinding, fixture.identity(current), staleOracle))
        assertNull(fixture.lab.workModeFor(fixture.identity(current)))
    }

    @Test
    fun `viewport region session and window changes each require a new binding`() {
        val fixture = Fixture()
        fixture.attestCurrent()

        fixture.invalidate(ChromeVisualShieldInvalidation.Viewport, viewport = ChromeVisualViewport(0, 0, 101, 200))
        assertFalse(fixture.lab.hasCurrentBinding(assertNotNull(fixture.gate.snapshot().context)))
        fixture.attestCurrent()

        fixture.invalidate(ChromeVisualShieldInvalidation.Scroll)
        assertFalse(fixture.lab.hasCurrentBinding(assertNotNull(fixture.gate.snapshot().context)))

        fixture.lab.clear()
        fixture.gate.stop()
        fixture.gate.start(8, fixture.viewport, fixture.contract)
        fixture.lab.begin(fixture.request)
        assertFalse(fixture.lab.hasCurrentBinding(assertNotNull(fixture.gate.snapshot().context)))

        fixture.gate.invalidate(9, fixture.viewport, fixture.contract, ChromeVisualShieldInvalidation.WindowReplaced)
        fixture.lab.invalidate()
        assertFalse(fixture.lab.hasCurrentBinding(assertNotNull(fixture.gate.snapshot().context)))
    }

    @Test
    fun `stop invalidates binding and wakes generation barrier without polling`() {
        val fixture = Fixture()
        val binding = fixture.attestCurrent()

        fixture.lab.clear()
        fixture.gate.stop()

        assertEquals(
            ChromeVisualShieldRegionDiscoveryGenerationOutcome.Stopped,
            fixture.lab.awaitGeneration(binding, 0),
        )
        assertNull(fixture.schedule())
        assertEquals(0, fixture.captureCount)
    }

    @Test
    fun `binding rejects every generation identity mismatch independently`() {
        val fixture = Fixture()
        val binding = fixture.attestCurrent()
        val generation = binding.generation()

        assertFalse(binding.matches(generation.copy(protectionSessionId = generation.protectionSessionId + 1)))
        assertFalse(binding.matches(generation.copy(windowId = generation.windowId + 1)))
        assertFalse(binding.matches(generation.copy(contentEpoch = generation.contentEpoch + 1)))
        assertFalse(binding.matches(generation.copy(viewportEpoch = generation.viewportEpoch + 1)))
        assertFalse(binding.matches(generation.copy(regionSequence = generation.regionSequence + 1)))
        assertFalse(binding.matches(generation.copy(renderIdentityToken = "stale")))
        assertTrue(binding.matches(generation))
    }

    private class Fixture {
        val viewport = ChromeVisualViewport(0, 0, 100, 200)
        val contract = ChromeVisualShieldRegionContract("fixture", 0, 0, 10_000, 10_000, "signed")
        val request = ChromeVisualShieldRegionDiscoveryProbeRequest(Scenario, listOf(SourceSha), RenderContract)
        val gate = ChromeVisualShieldIdentityGate()
        val lab =
            ChromeVisualShieldRegionDiscoveryLab(
                ChromeVisualShieldRegionDiscoveryAuthority(
                    gate,
                    ChromeVisualShieldR1Metrics(),
                ),
            )
        var captureCount = 0
        var plannerCount = 0
        var inferenceCount = 0
        var lastMode: ChromeVisualShieldWorkMode.RegionDiscoveryProbe? = null

        init {
            gate.start(7, viewport, contract)
            lab.begin(request)
        }

        fun attestCurrent(): ChromeVisualShieldRegionDiscoveryRenderBinding {
            val context = assertNotNull(gate.snapshot().context)
            val binding = context.toRegionDiscoveryBinding(RenderKey)
            assertTrue(lab.recordRenderBinding(binding, context))
            assertTrue(lab.recordAttestation(binding, identity(context), oracle(binding)))
            return binding
        }

        fun invalidate(
            reason: ChromeVisualShieldInvalidation,
            viewport: ChromeVisualViewport = this.viewport,
        ) {
            lab.invalidate()
            gate.invalidate(7, viewport, contract, reason)
        }

        fun schedule(): ChromeVisualShieldIdentity? {
            val context = gate.snapshot().context ?: return null
            if (!lab.hasCurrentBinding(context)) return null
            val identity = gate.beginCapture() ?: return null
            val mode = lab.workModeFor(identity)
            if (mode == null) {
                gate.failClosed(identity)
                return null
            }
            captureCount += 1
            lastMode = mode
            return identity
        }

        fun oracle(binding: ChromeVisualShieldRegionDiscoveryRenderBinding) =
            ChromeVisualShieldRegionDiscoveryOracle(
                renderIdentityToken = binding.renderIdentityToken,
                scenarioId = Scenario,
                renderContract = RenderContract,
                canvasWidth = 80,
                canvasHeight = 100,
                carrierCss = ChromeVisualShieldLabRect(10.0, 20.0, 80.0, 100.0),
                visualViewportCss = ChromeVisualShieldLabRect(0.0, 0.0, 100.0, 200.0),
                visualViewportScale = 1.0,
                devicePixelRatio = 1.0,
                expectComplete = true,
                regions =
                    listOf(
                        ChromeVisualShieldRegionDiscoveryOracleRegion(
                            oracleId = "safe",
                            sourceSha256 = SourceSha,
                            sourceWidth = 80,
                            sourceHeight = 100,
                            drawCanvas = ChromeVisualShieldLabRect(0.0, 0.0, 80.0, 100.0),
                        ),
                    ),
            )

        fun identity(context: ChromeVisualShieldContext) =
            ChromeVisualShieldIdentity(
                protectionSessionId = context.protectionSessionId,
                windowId = context.windowId,
                contentEpoch = context.contentEpoch,
                viewport = context.viewport,
                viewportEpoch = context.viewportEpoch,
                captureSequence = 1,
                regionId = context.regionId,
                regionSequence = context.regionSequence,
                region = context.region,
            )
    }

    private companion object {
        const val Scenario = "centered-safe"
        const val RenderContract = "canvas-content-islands-v1"
        const val SourceSha = "1a1a90fad0006ebc3eb4e9f89824f988729200b7723e99a49ac82f3ec65524c9"
        const val RenderKey = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}
