package com.contentfilter.feature.accessibility.chromevisual

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChromeVisualShieldRegionDiscoveryPresentationRecoveryTest {
    @Test
    fun `recovery recaptures current binding once before replacing generation`() {
        val fixture = Fixture()
        fixture.attestCurrent()
        var recaptures = 0
        var replacements = 0
        val recovery =
            ChromeVisualShieldRegionDiscoveryPresentationRecovery(
                fixture.lab,
                fixture.gate,
                recaptureCurrent = { recaptures += 1 },
                replaceGeneration = { replacements += 1 },
                log = {},
            )
        val firstIdentity = assertNotNull(fixture.gate.beginCapture())
        val firstWork =
            ChromeVisualShieldWork(firstIdentity, "test", assertNotNull(fixture.lab.workModeFor(firstIdentity)))
        fixture.gate.failClosed(firstIdentity)
        recovery.onRejected(firstWork, rejection())
        assertEquals(1, recaptures)
        assertEquals(0, replacements)

        val secondIdentity = assertNotNull(fixture.gate.beginCapture())
        val secondWork =
            ChromeVisualShieldWork(secondIdentity, "test", assertNotNull(fixture.lab.workModeFor(secondIdentity)))
        fixture.gate.failClosed(secondIdentity)
        recovery.onRejected(secondWork, rejection())
        assertEquals(1, recaptures)
        assertEquals(1, replacements)
    }

    @Test
    fun `pre draw first recaptures current generation then replaces it statefully`() {
        val fixture = Fixture()
        val stale = fixture.attestCurrent()
        val firstIdentity = assertNotNull(fixture.gate.beginCapture())

        assertEquals(
            ChromeVisualShieldRegionDiscoveryLab.PresentationRecovery.RecaptureCurrent,
            fixture.lab.presentationRejected(stale, firstIdentity, RejectReason),
        )
        fixture.gate.failClosed(firstIdentity)
        val secondIdentity = assertNotNull(fixture.gate.beginCapture())
        assertTrue(stale.matches(secondIdentity))
        assertEquals(
            ChromeVisualShieldRegionDiscoveryLab.PresentationRecovery.ReplaceGeneration,
            fixture.lab.presentationRejected(stale, secondIdentity, RejectReason),
        )
        fixture.invalidatePresentation()
        assertEquals(
            ChromeVisualShieldRegionDiscoveryGenerationOutcome.PresentationNotReady,
            fixture.lab.awaitGeneration(stale, 0),
        )

        val fresh = fixture.attestCurrent()
        val capture = assertNotNull(fixture.gate.beginCapture())
        assertTrue(fresh.matches(capture))
        assertEquals(fresh, assertNotNull(fixture.lab.workModeFor(capture)).binding)
    }

    @Test
    fun `repeated pre draw is bounded and remains fail closed`() {
        val fixture = Fixture()
        repeat(2) {
            val binding = fixture.attestCurrent()
            val firstIdentity = assertNotNull(fixture.gate.beginCapture())
            assertEquals(
                ChromeVisualShieldRegionDiscoveryLab.PresentationRecovery.RecaptureCurrent,
                fixture.lab.presentationRejected(binding, firstIdentity, RejectReason),
            )
            fixture.gate.failClosed(firstIdentity)
            val secondIdentity = assertNotNull(fixture.gate.beginCapture())
            assertEquals(
                ChromeVisualShieldRegionDiscoveryLab.PresentationRecovery.ReplaceGeneration,
                fixture.lab.presentationRejected(binding, secondIdentity, RejectReason),
            )
            fixture.invalidatePresentation()
        }
        val terminal = fixture.attestCurrent()
        val firstTerminalIdentity = assertNotNull(fixture.gate.beginCapture())
        assertEquals(
            ChromeVisualShieldRegionDiscoveryLab.PresentationRecovery.RecaptureCurrent,
            fixture.lab.presentationRejected(terminal, firstTerminalIdentity, RejectReason),
        )
        fixture.gate.failClosed(firstTerminalIdentity)
        val terminalIdentity = assertNotNull(fixture.gate.beginCapture())

        assertEquals(
            ChromeVisualShieldRegionDiscoveryLab.PresentationRecovery.ExhaustedFailClosed,
            fixture.lab.presentationRejected(terminal, terminalIdentity, RejectReason),
        )
        assertEquals(
            ChromeVisualShieldRegionDiscoveryGenerationOutcome.PresentationFailedClosed,
            fixture.lab.awaitGeneration(terminal, 0),
        )
        assertTrue(fixture.lab.statusValue().contains("presentationExhausted=1"))
        assertTrue(fixture.lab.statusValue().contains("presentationRecaptures=3"))
    }

    @Test
    fun `stale callback and stop cannot request a later replacement`() {
        val fixture = Fixture()
        val binding = fixture.attestCurrent()
        val identity = assertNotNull(fixture.gate.beginCapture())
        fixture.gate.invalidate(7, fixture.viewport, fixture.contract, ChromeVisualShieldInvalidation.Rotation)
        fixture.lab.invalidate()

        assertEquals(
            ChromeVisualShieldRegionDiscoveryLab.PresentationRecovery.StaleDropped,
            fixture.lab.presentationRejected(binding, identity, RejectReason),
        )
        val current = fixture.attestCurrent()
        fixture.lab.clear()
        assertEquals(
            ChromeVisualShieldRegionDiscoveryGenerationOutcome.Stopped,
            fixture.lab.awaitGeneration(current, 0),
        )
    }

    private class Fixture {
        val viewport = ChromeVisualViewport(0, 0, 1000, 1000)
        val contract =
            ChromeVisualShieldRegionContract(
                "fixture",
                1500,
                2500,
                8500,
                5500,
                "signed",
                edgeInsetPixels = 4,
            )
        val gate = ChromeVisualShieldIdentityGate()
        val request = ChromeVisualShieldRegionDiscoveryProbeRequest(Scenario, listOf(SourceSha), RenderContract)
        val lab =
            ChromeVisualShieldRegionDiscoveryLab(
                ChromeVisualShieldRegionDiscoveryAuthority(gate, ChromeVisualShieldR1Metrics()),
            )

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

        fun invalidatePresentation() {
            val context = assertNotNull(gate.snapshot().context)
            gate.invalidate(7, context.viewport, contract, ChromeVisualShieldInvalidation.Navigation)
            lab.invalidate(ChromeVisualShieldRegionDiscoveryGenerationOutcome.PresentationNotReady)
        }

        private fun oracle(
            binding: ChromeVisualShieldRegionDiscoveryRenderBinding,
        ): ChromeVisualShieldRegionDiscoveryOracle {
            val proof =
                assertNotNull(
                    ChromeVisualShieldRegionDiscoveryPresentationMarkerContract.expected(binding, 700, 300),
                )
            return ChromeVisualShieldRegionDiscoveryOracle(
                renderIdentityToken = binding.renderIdentityToken,
                scenarioId = Scenario,
                renderContract = RenderContract,
                canvasWidth = 700,
                canvasHeight = 300,
                carrierCss = ChromeVisualShieldLabRect(150.0, 250.0, 700.0, 300.0),
                visualViewportCss = ChromeVisualShieldLabRect(0.0, 0.0, 1000.0, 1000.0),
                visualViewportScale = 1.0,
                devicePixelRatio = 1.0,
                presentationProof = proof,
                expectComplete = true,
                regions =
                    listOf(
                        ChromeVisualShieldRegionDiscoveryOracleRegion(
                            "safe",
                            SourceSha,
                            100,
                            100,
                            ChromeVisualShieldLabRect(200.0, 50.0, 300.0, 200.0),
                        ),
                    ),
            )
        }

        private fun identity(context: ChromeVisualShieldContext): ChromeVisualShieldIdentity =
            context.toProbeIdentity(gate.snapshot().nextCaptureSequence)
    }

    private companion object {
        fun rejection() =
            ChromeVisualShieldRegionDiscoveryPresentationResult.Rejected(
                reason = RejectReason,
                matchedSamples = 0,
                paletteSamples = 0,
                observedPaletteBounds = null,
            )

        val RejectReason = ChromeVisualShieldRegionDiscoveryPresentationRejectReason.MarkerAbsent
        const val Scenario = "centered-safe"
        const val RenderContract = "canvas-content-islands-v3"
        const val SourceSha = "1a1a90fad0006ebc3eb4e9f89824f988729200b7723e99a49ac82f3ec65524c9"
        const val RenderKey = "aabbccddaabbccddaabbccddaabbccddaabbccddaabbccddaabbccddaabbccdd"
    }
}
