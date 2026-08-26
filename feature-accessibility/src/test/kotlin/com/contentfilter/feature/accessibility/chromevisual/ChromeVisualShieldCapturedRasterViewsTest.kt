package com.contentfilter.feature.accessibility.chromevisual

import com.glosh.visual.GloshiaImageCropPlan
import com.glosh.visual.GloshiaRegionalCropPlanner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChromeVisualShieldCapturedRasterViewsTest {
    @Test
    fun `standard raster adds no regional views`() {
        assertTrue(
            ChromeVisualShieldCapturedRasterViews.planCanonicalRegions(800, 600).isEmpty(),
        )
    }

    @Test
    fun `extreme wide raster uses exact canonical plans`() {
        val actual = ChromeVisualShieldCapturedRasterViews.planCanonicalRegions(1639, 324)

        assertEquals(
            GloshiaRegionalCropPlanner.plan(1639, 324, allowStandardAspect = false),
            actual,
        )
        assertEquals(
            listOf(
                GloshiaImageCropPlan(left = 0, top = 0, width = 688, height = 324),
                GloshiaImageCropPlan(left = 475, top = 0, width = 688, height = 324),
                GloshiaImageCropPlan(left = 951, top = 0, width = 688, height = 324),
            ),
            actual,
        )
    }

    @Test
    fun `extreme tall raster uses exact canonical plans`() {
        val actual = ChromeVisualShieldCapturedRasterViews.planCanonicalRegions(324, 1639)

        assertEquals(
            GloshiaRegionalCropPlanner.plan(324, 1639, allowStandardAspect = false),
            actual,
        )
        assertEquals(
            listOf(
                GloshiaImageCropPlan(left = 0, top = 0, width = 324, height = 688),
                GloshiaImageCropPlan(left = 0, top = 475, width = 324, height = 688),
                GloshiaImageCropPlan(left = 0, top = 951, width = 324, height = 688),
            ),
            actual,
        )
    }
}
