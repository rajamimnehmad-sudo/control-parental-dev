package com.contentfilter.feature.accessibility.chromevisual

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ChromeVisualShieldRasterProvenanceTest {
    @Test
    fun `attested epoch mismatch has precedence over raster evidence`() {
        val result =
            ChromeVisualShieldRasterProvenanceClassifier.classify(
                signals(
                    attestedContentEpoch = 10,
                    captureContentEpoch = 11,
                    crop = fingerprint(marker = 100, card = 100),
                    expectedCardPresent = true,
                    drawForeign = 0.9,
                ),
            )

        assertEquals(ChromeVisualShieldRasterRootCause.EPOCH_MISMATCH, result.cause)
    }

    @Test
    fun `surface lattice without expected carrier content identifies protected surface`() {
        val result =
            ChromeVisualShieldRasterProvenanceClassifier.classify(
                signals(crop = fingerprint(marker = 32)),
            )

        assertEquals(ChromeVisualShieldRasterRootCause.PROTECTED_SURFACE_CAPTURED, result.cause)
    }

    @Test
    fun `neutral carrier without draw identifies canvas pre draw`() {
        val result =
            ChromeVisualShieldRasterProvenanceClassifier.classify(
                signals(
                    crop = fingerprint(canvasFraction = 0.995),
                    carrierAligned = true,
                    drawForeign = 0.0,
                ),
            )

        assertEquals(ChromeVisualShieldRasterRootCause.CANVAS_PRE_DRAW, result.cause)
    }

    @Test
    fun `matching card outside search identifies mapping shift and preserves delta`() {
        val delta = ChromeVisualShieldRasterMappingDelta(90.0, -14.0, 1.0, 1.0)
        val result =
            ChromeVisualShieldRasterProvenanceClassifier.classify(
                signals(
                    full = fingerprint(card = 200),
                    crop = fingerprint(),
                    matchingCardOutside = true,
                    mappingDelta = delta,
                ),
            )

        assertEquals(ChromeVisualShieldRasterRootCause.MAPPING_SHIFT, result.cause)
        assertEquals(delta, result.mappingDelta)
    }

    @Test
    fun `card and draw at expected geometry identify expected content`() {
        val result =
            ChromeVisualShieldRasterProvenanceClassifier.classify(
                signals(
                    crop = fingerprint(card = 200),
                    expectedCardPresent = true,
                    drawForeign = 0.72,
                ),
            )

        assertEquals(ChromeVisualShieldRasterRootCause.EXPECTED_CONTENT_PRESENT, result.cause)
    }

    @Test
    fun `contradictory lattice and expected content remains unknown`() {
        val result =
            ChromeVisualShieldRasterProvenanceClassifier.classify(
                signals(
                    crop = fingerprint(marker = 40, card = 200),
                    expectedCardPresent = true,
                    drawForeign = 0.72,
                ),
            )

        assertEquals(ChromeVisualShieldRasterRootCause.UNKNOWN, result.cause)
    }

    @Test
    fun `fingerprint observer cannot alter planner input or result`() {
        val width = 100
        val height = 120
        val pixels = IntArray(width * height) { 0xff202428.toInt() }
        for (y in 30 until 90) {
            for (x in 20 until 80) pixels[y * width + x] = 0xfff5f5f5.toInt()
        }
        val original = pixels.copyOf()
        val identity = identity(width, height)
        val planner = ChromeVisualShieldRegionDiscoveryPlanner()
        val disconnected = planner.discover(ChromeVisualShieldDiscoveryRaster(width, height, pixels), identity, 1)

        ChromeVisualShieldRasterFingerprintFactory.create(width, height, pixels)
        val connected = planner.discover(ChromeVisualShieldDiscoveryRaster(width, height, pixels), identity, 1)

        assertContentEquals(original, pixels)
        assertEquals(disconnected, connected)
    }

    @Test
    fun `known neutral colors remain distinct under fixed tolerance`() {
        val pixels = intArrayOf(0xff202428.toInt(), 0xff202124.toInt())

        val result = ChromeVisualShieldRasterFingerprintFactory.create(2, 1, pixels)

        assertEquals(1, result.color(ChromeVisualShieldRasterProvenanceClassifier.CanvasNeutral).count)
        assertEquals(1, result.color(ChromeVisualShieldRasterProvenanceClassifier.SurfaceNeutral).count)
    }

    private fun signals(
        attestedContentEpoch: Long = 10,
        attestedRegionSequence: Long = 20,
        captureContentEpoch: Long = 10,
        captureRegionSequence: Long = 20,
        full: ChromeVisualShieldRasterFingerprint = fingerprint(),
        crop: ChromeVisualShieldRasterFingerprint = fingerprint(),
        carrierAligned: Boolean = false,
        expectedCardPresent: Boolean = false,
        drawForeign: Double = 0.0,
        matchingCardOutside: Boolean = false,
        mappingDelta: ChromeVisualShieldRasterMappingDelta? = null,
    ) = ChromeVisualShieldRasterProvenanceSignals(
        attestedContentEpoch,
        attestedRegionSequence,
        captureContentEpoch,
        captureRegionSequence,
        full,
        crop,
        carrierAligned,
        expectedCardPresent,
        drawForeign,
        matchingCardOutside,
        mappingDelta,
    )

    private fun fingerprint(
        canvasFraction: Double = 0.0,
        card: Int = 0,
        marker: Int = 0,
    ) = ChromeVisualShieldRasterFingerprint(
        width = 100,
        height = 100,
        colors =
            listOf(
                color(ChromeVisualShieldRasterProvenanceClassifier.CanvasNeutral, (canvasFraction * 10_000).toInt()),
                color(ChromeVisualShieldRasterProvenanceClassifier.Card, card),
                color(ChromeVisualShieldRasterProvenanceClassifier.Body, 0),
                color(ChromeVisualShieldRasterProvenanceClassifier.SurfaceNeutral, 0),
                color(ChromeVisualShieldRasterProvenanceClassifier.SurfaceMarker, marker),
            ),
        cardClusters = emptyList(),
        samples = emptyList(),
    )

    private fun color(
        name: String,
        count: Int,
    ) = ChromeVisualShieldRasterColorEvidence(name, count, count / 10_000.0, null)

    private fun identity(
        width: Int,
        height: Int,
    ) = ChromeVisualShieldIdentity(
        protectionSessionId = 1,
        windowId = 2,
        contentEpoch = 3,
        viewport = ChromeVisualViewport(0, 0, width, height),
        viewportEpoch = 4,
        captureSequence = 5,
        regionId = "fixture",
        regionSequence = 6,
        region = ChromeVisualRegion("fixture", 0, 0, width, height),
    )
}
