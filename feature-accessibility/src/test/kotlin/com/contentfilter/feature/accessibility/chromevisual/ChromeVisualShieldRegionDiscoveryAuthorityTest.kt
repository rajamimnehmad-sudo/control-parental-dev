package com.contentfilter.feature.accessibility.chromevisual

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromeVisualShieldRegionDiscoveryAuthorityTest {
    @Test
    fun `current complete returns to protected without release authority`() {
        val gate = ChromeVisualShieldIdentityGate()
        val metrics = ChromeVisualShieldR1Metrics()
        val identity = capturingIdentity(gate)
        assertEquals(ChromeVisualShieldResult.Current, gate.beginProcessing(identity))
        val delivery = delivery(identity, complete(identity))

        val result = ChromeVisualShieldRegionDiscoveryAuthority(gate, metrics).observe(delivery)

        assertEquals(ChromeVisualShieldRegionDiscoveryAuthorityResult.CompleteObserved, result)
        assertEquals(ChromeVisualShieldPhase.Protected, gate.snapshot().phase)
        assertEquals(0, gate.snapshot().labReleaseCount)
        assertTrue(gate.snapshot().isFailClosed)
    }

    @Test
    fun `unknown remains protected and is never treated as safe`() {
        val gate = ChromeVisualShieldIdentityGate()
        val identity = capturingIdentity(gate)
        assertEquals(ChromeVisualShieldResult.Current, gate.beginProcessing(identity))
        val unknown =
            ChromeVisualShieldRegionDiscoveryResult.Unknown(
                ChromeVisualShieldDiscoveryUnknownReason.BackgroundAmbiguous,
                ChromeVisualShieldResidualEvidence(100, 40, 40, 2, 0, "ambiguous"),
            )

        val result =
            ChromeVisualShieldRegionDiscoveryAuthority(gate, ChromeVisualShieldR1Metrics())
                .observe(delivery(identity, unknown))

        assertEquals(ChromeVisualShieldRegionDiscoveryAuthorityResult.UnknownObserved, result)
        assertEquals(ChromeVisualShieldPhase.Protected, gate.snapshot().phase)
        assertEquals(0, gate.snapshot().labReleaseCount)
    }

    @Test
    fun `old complete after invalidation is dropped`() {
        val gate = ChromeVisualShieldIdentityGate()
        val identity = capturingIdentity(gate)
        assertEquals(ChromeVisualShieldResult.Current, gate.beginProcessing(identity))
        val contract = contract()
        gate.invalidate(7, viewport(), contract, ChromeVisualShieldInvalidation.Scroll)

        val result =
            ChromeVisualShieldRegionDiscoveryAuthority(gate, ChromeVisualShieldR1Metrics())
                .observe(delivery(identity, complete(identity)))

        assertEquals(ChromeVisualShieldRegionDiscoveryAuthorityResult.StaleDropped, result)
        assertEquals(ChromeVisualShieldPhase.Protected, gate.snapshot().phase)
        assertEquals(0, gate.snapshot().labReleaseCount)
        assertFalse(gate.snapshot().phase == ChromeVisualShieldPhase.LabReleased)
    }

    private fun complete(identity: ChromeVisualShieldIdentity): ChromeVisualShieldRegionDiscoveryResult.Complete =
        ChromeVisualShieldRegionDiscoveryResult.Complete(
            regions = emptyList(),
            discoverySequence = 1,
            regionSetDigest = "a".repeat(64),
            coverageEvidence = ChromeVisualShieldCoverageEvidence(100, 100, 0, 0, 0, 0, "test"),
        )

    private fun delivery(
        identity: ChromeVisualShieldIdentity,
        discovery: ChromeVisualShieldRegionDiscoveryResult,
    ) = ChromeVisualShieldRegionDiscoveryDelivery(
        work =
            ChromeVisualShieldWork(
                identity,
                "test",
                ChromeVisualShieldWorkMode.RegionDiscoveryProbe(
                    ChromeVisualShieldRegionDiscoveryProbeRequest(
                        "centered-safe",
                        listOf("1".repeat(64)),
                        "canvas-content-islands-v1",
                    ),
                ),
            ),
        searchEnvelope = identity.region,
        cropEvidence = ChromeVisualShieldCropEvidence(10, 10, "2".repeat(64)),
        discovery = discovery,
        decisions = emptyList(),
    )

    private fun capturingIdentity(gate: ChromeVisualShieldIdentityGate): ChromeVisualShieldIdentity {
        gate.start(7, viewport(), contract())
        return checkNotNull(gate.beginCapture())
    }

    private fun viewport() = ChromeVisualViewport(0, 0, 100, 200)

    private fun contract() = ChromeVisualShieldRegionContract("fixture", 1000, 1000, 9000, 9000, "signed")
}
