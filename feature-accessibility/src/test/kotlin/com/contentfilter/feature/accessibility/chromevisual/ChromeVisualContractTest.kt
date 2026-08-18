package com.contentfilter.feature.accessibility.chromevisual

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromeVisualContractTest {
    @Test
    fun `node planner keeps bounded image regions and removes duplicates`() {
        val regions =
            ChromeVisualRegionPlanner.fromNodes(
                candidates =
                    listOf(
                        candidate("android.widget.Image", "first", 40, 200, 440, 500),
                        candidate("android.view.View", "duplicate", 40, 200, 440, 500, described = true),
                        candidate("android.widget.Button", "button", 20, 100, 100, 160),
                        candidate("android.widget.Image", "small", 10, 300, 30, 320),
                    ),
                windowWidth = 1_080,
                windowHeight = 2_400,
                minimumEdge = 48,
            )

        assertEquals(listOf("first"), regions.map(ChromeVisualRegion::id))
    }

    @Test
    fun `fallback covers unknown baseline and later stays bounded to changed tiles`() {
        val tiles = ChromeVisualRegionPlanner.fallbackTiles(1_000, 2_000, 200)
        val current = tiles.associate { it.id to it.id.hashCode().toLong() }

        assertEquals(
            tiles,
            ChromeVisualRegionPlanner.changedFallbackTiles(
                1_000,
                2_000,
                200,
                emptyMap(),
                current,
            ),
        )
        val previous =
            current.toMutableMap().apply {
                this["tile_1_0"] = -1L
                this["tile_3_1"] = -1L
            }
        val changed =
            ChromeVisualRegionPlanner.changedFallbackTiles(
                1_000,
                2_000,
                200,
                previous,
                current,
            )

        assertEquals(listOf("tile_1_0", "tile_3_1"), changed.map(ChromeVisualRegion::id))
        assertTrue(changed.size <= 4)
        assertEquals(8, tiles.size)
        assertEquals(1_000L * 1_800L, tiles.sumOf(ChromeVisualRegion::area))
    }

    @Test
    fun `old capture can never modify a new content epoch`() {
        val gate = ChromeVisualIdentityGate()
        gate.invalidate(windowId = 7)
        val capture = gate.nextCapture()
        val old = ChromeVisualIdentity(7, capture.first, capture.second, "image", 42L)

        assertTrue(gate.isCurrent(old))
        gate.invalidate(windowId = 7)
        assertFalse(gate.isCurrent(old))
        val next = gate.nextCapture()
        assertTrue(gate.isCurrent(ChromeVisualIdentity(7, next.first, next.second, "image", 43L)))
    }

    private fun candidate(
        className: String,
        id: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        described: Boolean = false,
    ) = ChromeVisualNodeCandidate(
        className = className,
        hasDescription = described,
        childCount = 0,
        region = ChromeVisualRegion(id, left, top, right, bottom),
    )
}
