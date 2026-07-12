package com.contentfilter.feature.accessibility.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebImageOverlayPlannerTest {
    @Test
    fun `strict fallback covers content while preserving top browser chrome`() {
        val plan =
            WebImageOverlayPlanner.plan(
                imagesBlocked = true,
                rootBounds = Screen,
                addressBarBounds = OverlayBounds(0, 40, 1080, 160),
                visualBounds = emptyList(),
                individualCoverageReliable = false,
                fallbackTopInsetPx = 96,
                fallbackBottomInsetPx = 56,
            )

        assertEquals(WebImageOverlayCoverage.FullContent, plan.coverage)
        assertEquals(listOf(OverlayBounds(0, 160, 1080, 1864)), plan.bounds)
    }

    @Test
    fun `reliable trees use clipped placeholders for detected images`() {
        val plan =
            WebImageOverlayPlanner.plan(
                imagesBlocked = true,
                rootBounds = Screen,
                addressBarBounds = OverlayBounds(0, 40, 1080, 160),
                visualBounds =
                    listOf(
                        OverlayBounds(40, 200, 500, 600),
                        OverlayBounds(40, 20, 80, 80),
                    ),
                individualCoverageReliable = true,
                fallbackTopInsetPx = 96,
                fallbackBottomInsetPx = 56,
            )

        assertEquals(WebImageOverlayCoverage.Regions, plan.coverage)
        assertEquals(listOf(OverlayBounds(40, 200, 500, 600)), plan.bounds)
    }

    @Test
    fun `scroll produces updated placeholder bounds`() {
        fun plan(top: Int) =
            WebImageOverlayPlanner.plan(
                imagesBlocked = true,
                rootBounds = Screen,
                addressBarBounds = OverlayBounds(0, 40, 1080, 160),
                visualBounds = listOf(OverlayBounds(40, top, 500, top + 300)),
                individualCoverageReliable = true,
                fallbackTopInsetPx = 96,
                fallbackBottomInsetPx = 56,
            )

        assertFalse(plan(400) == plan(240))
    }

    @Test
    fun `turning images off removes every overlay plan`() {
        val plan =
            WebImageOverlayPlanner.plan(
                imagesBlocked = false,
                rootBounds = Screen,
                addressBarBounds = null,
                visualBounds = listOf(Screen),
                individualCoverageReliable = false,
                fallbackTopInsetPx = 96,
                fallbackBottomInsetPx = 56,
            )

        assertEquals(WebImageOverlayPlan.None, plan)
    }

    @Test
    fun `visual classifier uses class and stable view id without visible text`() {
        assertTrue(WebVisualNodeClassifier.isVisualNode("android.widget.ImageView", null))
        assertTrue(WebVisualNodeClassifier.isVisualNode("android.view.View", "page_thumbnail"))
        assertFalse(WebVisualNodeClassifier.isVisualNode("android.widget.TextView", "result_title"))
    }

    private companion object {
        val Screen = OverlayBounds(0, 0, 1080, 1920)
    }
}
