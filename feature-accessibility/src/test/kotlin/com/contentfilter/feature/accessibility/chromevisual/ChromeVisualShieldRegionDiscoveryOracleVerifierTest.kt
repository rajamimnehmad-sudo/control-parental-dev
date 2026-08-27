package com.contentfilter.feature.accessibility.chromevisual

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromeVisualShieldRegionDiscoveryOracleVerifierTest {
    @Test
    fun `complete requires one-to-one region coverage`() {
        val identity = identity()
        val expected = region("discovery-1", 20, 30, 80, 90)

        assertTrue(
            ChromeVisualShieldRegionDiscoveryOracleVerifier.matches(
                identity,
                identity.region,
                crop(),
                request(),
                oracle(identity, expectComplete = true),
                complete(expected),
            ),
        )
        assertFalse(
            ChromeVisualShieldRegionDiscoveryOracleVerifier.matches(
                identity,
                identity.region,
                crop(),
                request(),
                oracle(identity, expectComplete = true),
                complete(region("discovery-1", 5, 10, 95, 110)),
            ),
        )
    }

    @Test
    fun `expected unknown never accepts false complete`() {
        val identity = identity()
        val expectedUnknown = oracle(identity, expectComplete = false)
        val unknown =
            ChromeVisualShieldRegionDiscoveryResult.Unknown(
                ChromeVisualShieldDiscoveryUnknownReason.BackgroundAmbiguous,
                ChromeVisualShieldResidualEvidence(12_000, 1_000, 1_000, 1, 0, "gradient"),
            )

        assertTrue(
            ChromeVisualShieldRegionDiscoveryOracleVerifier.matches(
                identity,
                identity.region,
                crop(),
                request(),
                expectedUnknown,
                unknown,
            ),
        )
        assertFalse(
            ChromeVisualShieldRegionDiscoveryOracleVerifier.matches(
                identity,
                identity.region,
                crop(),
                request(),
                expectedUnknown,
                complete(region("discovery-1", 20, 30, 80, 90)),
            ),
        )
    }

    private fun oracle(
        identity: ChromeVisualShieldIdentity,
        expectComplete: Boolean,
    ) = ChromeVisualShieldRegionDiscoveryOracle(
        renderIdentityToken = identity.renderIdentityToken(),
        scenarioId = "centered-safe",
        renderContract = "canvas-content-islands-v1",
        canvasWidth = 100,
        canvasHeight = 100,
        carrierCss = ChromeVisualShieldLabRect(0.0, 0.0, 100.0, 100.0),
        visualViewportCss = ChromeVisualShieldLabRect(0.0, 0.0, 100.0, 120.0),
        visualViewportScale = 1.0,
        devicePixelRatio = 1.0,
        expectComplete = expectComplete,
        regions =
            listOf(
                ChromeVisualShieldRegionDiscoveryOracleRegion(
                    oracleId = "oracle-safe",
                    sourceSha256 = "1".repeat(64),
                    sourceWidth = 60,
                    sourceHeight = 60,
                    drawCanvas = ChromeVisualShieldLabRect(20.0, 30.0, 60.0, 60.0),
                ),
            ),
    )

    private fun request() =
        ChromeVisualShieldRegionDiscoveryProbeRequest(
            scenarioId = "centered-safe",
            sourceSha256s = listOf("1".repeat(64)),
            renderContract = "canvas-content-islands-v1",
        )

    private fun complete(region: ChromeVisualShieldDiscoveredRegion) =
        ChromeVisualShieldRegionDiscoveryResult.Complete(
            regions = listOf(region),
            discoverySequence = 1,
            regionSetDigest = "2".repeat(64),
            coverageEvidence = ChromeVisualShieldCoverageEvidence(12_000, 8_400, 0, 3_600, 0, 0, "test"),
        )

    private fun region(
        id: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) = ChromeVisualShieldDiscoveredRegion(
        id = id,
        bounds = ChromeVisualRegion(id, left, top, right, bottom),
        visualSignature = "3".repeat(64),
        assignedPixels = (right - left) * (bottom - top),
    )

    private fun crop() = ChromeVisualShieldCropEvidence(100, 120, "4".repeat(64))

    private fun identity() =
        ChromeVisualShieldIdentity(
            protectionSessionId = 1,
            windowId = 2,
            contentEpoch = 3,
            viewport = ChromeVisualViewport(0, 0, 100, 120),
            viewportEpoch = 4,
            captureSequence = 5,
            regionId = "fixture",
            regionSequence = 6,
            region = ChromeVisualRegion("fixture", 0, 0, 100, 120),
        )
}
