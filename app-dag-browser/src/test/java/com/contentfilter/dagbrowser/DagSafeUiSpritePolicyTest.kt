package com.contentfilter.dagbrowser

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DagSafeUiSpritePolicyTest {
    @Test
    fun `only extreme bounded PNG strips enter the sprite sanitizer`() {
        assertTrue(
            DagSafeUiSpritePolicy.isCandidate(
                DagImageBounds(6_144, 64, "image/png"),
                82 * 1_024,
            ),
        )
        assertTrue(
            DagSafeUiSpritePolicy.isCandidate(
                DagImageBounds(64, 6_144, "image/png"),
                82 * 1_024,
            ),
        )
        assertFalse(
            DagSafeUiSpritePolicy.isCandidate(
                DagImageBounds(4_096, 64, "image/png"),
                82 * 1_024,
            ),
        )
        assertFalse(
            DagSafeUiSpritePolicy.isCandidate(
                DagImageBounds(6_144, 512, "image/png"),
                82 * 1_024,
            ),
        )
        assertFalse(
            DagSafeUiSpritePolicy.isCandidate(
                DagImageBounds(6_144, 64, "image/jpeg"),
                82 * 1_024,
            ),
        )
    }

    @Test
    fun `transparent low color strip is accepted as passive UI`() {
        assertTrue(
            DagSafeUiSpritePolicy.isSafe(
                DagSafeUiSpriteMetrics(
                    width = 6_144,
                    height = 64,
                    transparentPixels = 300_000,
                    visiblePixels = 93_216,
                    quantizedColorCount = 80,
                ),
            ),
        )
    }

    @Test
    fun `opaque empty or photographic strip stays blocked`() {
        val base =
            DagSafeUiSpriteMetrics(
                width = 6_144,
                height = 64,
                transparentPixels = 300_000,
                visiblePixels = 93_216,
                quantizedColorCount = 80,
            )

        assertFalse(
            DagSafeUiSpritePolicy.isSafe(
                base.copy(transparentPixels = 0, visiblePixels = 393_216),
            ),
        )
        assertFalse(
            DagSafeUiSpritePolicy.isSafe(
                base.copy(transparentPixels = 392_000, visiblePixels = 1_216),
            ),
        )
        assertFalse(DagSafeUiSpritePolicy.isSafe(base.copy(quantizedColorCount = 257)))
    }
}
