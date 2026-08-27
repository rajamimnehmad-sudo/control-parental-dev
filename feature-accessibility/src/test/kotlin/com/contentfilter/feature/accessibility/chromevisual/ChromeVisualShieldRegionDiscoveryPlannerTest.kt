package com.contentfilter.feature.accessibility.chromevisual

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChromeVisualShieldRegionDiscoveryPlannerTest {
    private val planner = ChromeVisualShieldRegionDiscoveryPlanner()

    @Test
    fun `centered portrait and landscape islands are complete and bounded`() {
        listOf(120 to 200, 240 to 100).forEach { (width, height) ->
            val oracle = Rect(width / 4, height / 5, width * 3 / 4, height * 4 / 5)
            val raster = fixture(width, height, listOf(oracle))

            val complete = assertIs<ChromeVisualShieldRegionDiscoveryResult.Complete>(discover(raster))

            assertOneToOne(complete, listOf(oracle))
        }
    }

    @Test
    fun `off center islands remain independent of position`() {
        val left = Rect(5, 30, 55, 110)
        val right = Rect(145, 30, 195, 110)

        assertOneToOne(
            assertIs<ChromeVisualShieldRegionDiscoveryResult.Complete>(discover(fixture(220, 140, listOf(left)))),
            listOf(left),
        )
        assertOneToOne(
            assertIs<ChromeVisualShieldRegionDiscoveryResult.Complete>(discover(fixture(220, 140, listOf(right)))),
            listOf(right),
        )
    }

    @Test
    fun `two separate regions are not merged and order is deterministic`() {
        val oracles = listOf(Rect(15, 25, 85, 135), Rect(135, 25, 205, 135))
        val raster = fixture(220, 160, oracles)

        val first = assertIs<ChromeVisualShieldRegionDiscoveryResult.Complete>(discover(raster, sequence = 9))
        val second = assertIs<ChromeVisualShieldRegionDiscoveryResult.Complete>(discover(raster, sequence = 9))

        assertOneToOne(first, oracles)
        assertEquals(first.regions, second.regions)
        assertEquals(first.regionSetDigest, second.regionSetDigest)
    }

    @Test
    fun `thin controls are certified but never create image regions`() {
        val image = Rect(45, 35, 155, 135)
        val raster = fixture(200, 170, listOf(image), thinDetails = true)

        val complete = assertIs<ChromeVisualShieldRegionDiscoveryResult.Complete>(discover(raster))

        assertOneToOne(complete, listOf(image))
        assertTrue(complete.coverageEvidence.certifiedNonContentPixels > 0)
    }

    @Test
    fun `textured full bleed is a complete single region`() {
        val raster = fullBleed(128, 96)

        val complete = assertIs<ChromeVisualShieldRegionDiscoveryResult.Complete>(discover(raster))

        assertEquals(1, complete.regions.size)
        assertEquals(128 * 96, complete.coverageEvidence.assignedPixels)
        assertTrue(complete.coverageEvidence.basis.startsWith("full_bleed_texture"))
    }

    @Test
    fun `ambiguous gradient is unknown`() {
        val raster = gradient(180, 120)

        val unknown = assertIs<ChromeVisualShieldRegionDiscoveryResult.Unknown>(discover(raster))

        assertEquals(ChromeVisualShieldDiscoveryUnknownReason.BackgroundAmbiguous, unknown.reason)
    }

    @Test
    fun `cut island is unknown instead of false complete`() {
        val raster = fixture(160, 120, listOf(Rect(0, 25, 100, 95)))

        val unknown = assertIs<ChromeVisualShieldRegionDiscoveryResult.Unknown>(discover(raster))

        assertEquals(ChromeVisualShieldDiscoveryUnknownReason.CutComponent, unknown.reason)
    }

    @Test
    fun `component overflow is unknown`() {
        val regions =
            buildList {
                for (row in 0 until 3) {
                    for (column in 0 until 3) {
                        add(Rect(8 + column * 36, 8 + row * 36, 30 + column * 36, 30 + row * 36))
                    }
                }
            }
        val raster = fixture(120, 120, regions)

        val unknown = assertIs<ChromeVisualShieldRegionDiscoveryResult.Unknown>(discover(raster))

        assertEquals(ChromeVisualShieldDiscoveryUnknownReason.RegionOverflow, unknown.reason)
    }

    @Test
    fun `overlapping islands are unknown instead of merged complete`() {
        val raster = fixture(180, 160, listOf(Rect(20, 20, 110, 100), Rect(70, 65, 160, 145)))

        val unknown = assertIs<ChromeVisualShieldRegionDiscoveryResult.Unknown>(discover(raster))

        assertEquals(ChromeVisualShieldDiscoveryUnknownReason.OverlappingRegions, unknown.reason)
    }

    @Test
    fun `stale identity fails closed before discovery`() {
        val raster = fixture(120, 120, listOf(Rect(20, 20, 100, 100)))

        val unknown =
            assertIs<ChromeVisualShieldRegionDiscoveryResult.Unknown>(
                planner.discover(raster, identity(), 1, isIdentityCurrent = { false }),
            )

        assertEquals(ChromeVisualShieldDiscoveryUnknownReason.StaleIdentity, unknown.reason)
    }

    @Test
    fun `cancelled discovery is unknown`() {
        val raster = fixture(120, 120, listOf(Rect(20, 20, 100, 100)))

        val unknown =
            assertIs<ChromeVisualShieldRegionDiscoveryResult.Unknown>(
                planner.discover(raster, identity(), 1, isCancelled = { true }),
            )

        assertEquals(ChromeVisualShieldDiscoveryUnknownReason.Cancelled, unknown.reason)
    }

    private fun discover(
        raster: ChromeVisualShieldDiscoveryRaster,
        sequence: Long = 1,
    ) = planner.discover(raster, identity(), sequence)

    private fun assertOneToOne(
        complete: ChromeVisualShieldRegionDiscoveryResult.Complete,
        oracles: List<Rect>,
    ) {
        assertEquals(oracles.size, complete.regions.size)
        complete.regions.zip(oracles).forEach { (region, oracle) ->
            val intersection =
                maxOf(0, minOf(region.bounds.right, oracle.right) - maxOf(region.bounds.left, oracle.left)) *
                    maxOf(0, minOf(region.bounds.bottom, oracle.bottom) - maxOf(region.bounds.top, oracle.top))
            assertTrue(intersection.toDouble() / oracle.area >= 0.98)
            assertTrue(region.bounds.width * region.bounds.height <= oracle.area * 1.5)
        }
    }

    private fun fixture(
        width: Int,
        height: Int,
        regions: List<Rect>,
        thinDetails: Boolean = false,
    ): ChromeVisualShieldDiscoveryRaster {
        val pixels = IntArray(width * height) { Background }
        regions.forEachIndexed { index, rect ->
            for (y in rect.top until rect.bottom) {
                for (x in rect.left until rect.right) {
                    val edge = x == rect.left || x == rect.right - 1 || y == rect.top || y == rect.bottom - 1
                    pixels[y * width + x] =
                        if (edge) Border else texturedPixel(x, y, index)
                }
            }
        }
        if (thinDetails) {
            for (x in 8 until 28) pixels[12 * width + x] = Border
            for (y in 145 until 150) pixels[y * width + 175] = Border
        }
        return ChromeVisualShieldDiscoveryRaster(width, height, pixels)
    }

    private fun fullBleed(
        width: Int,
        height: Int,
    ) = ChromeVisualShieldDiscoveryRaster(
        width,
        height,
        IntArray(width * height) { index -> texturedPixel(index % width, index / width, 3) },
    )

    private fun gradient(
        width: Int,
        height: Int,
    ) = ChromeVisualShieldDiscoveryRaster(
        width,
        height,
        IntArray(width * height) { index ->
            val x = index % width
            argb(30 + x * 180 / width, 35 + x * 170 / width, 40 + x * 160 / width)
        },
    )

    private fun texturedPixel(
        x: Int,
        y: Int,
        seed: Int,
    ): Int =
        if ((x / 5 + y / 5 + seed) % 2 == 0) {
            argb(210, 45 + seed * 20, 70)
        } else {
            argb(15, 30, 45 + seed * 25)
        }

    private fun identity() =
        ChromeVisualShieldIdentity(
            protectionSessionId = 3,
            windowId = 5,
            contentEpoch = 7,
            viewport = ChromeVisualViewport(0, 0, 240, 400),
            viewportEpoch = 11,
            captureSequence = 13,
            regionId = "fixture",
            regionSequence = 17,
            region = ChromeVisualRegion("fixture", 0, 0, 240, 400),
        )

    private data class Rect(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        val area: Int get() = (right - left) * (bottom - top)
    }

    private companion object {
        val Background = argb(32, 36, 40)
        val Border = argb(245, 245, 245)

        fun argb(
            red: Int,
            green: Int,
            blue: Int,
        ): Int = -0x1000000 or (red shl 16) or (green shl 8) or blue
    }
}
