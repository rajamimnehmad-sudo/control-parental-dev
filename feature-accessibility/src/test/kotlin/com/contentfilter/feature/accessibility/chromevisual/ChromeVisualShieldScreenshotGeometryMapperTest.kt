package com.contentfilter.feature.accessibility.chromevisual

import kotlin.test.Test
import kotlin.test.assertEquals

class ChromeVisualShieldScreenshotGeometryMapperTest {
    @Test
    fun `landscape window bitmap removes lateral navigation inset without scaling content`() {
        val mapped =
            ChromeVisualShieldScreenshotGeometryMapper.toFrame(
                region = ChromeVisualRegion("region", 421, 232, 2052, 548),
                viewport = ChromeVisualViewport(0, 0, 2408, 1080),
                frameWidth = 2342,
                frameHeight = 1080,
                navigationInsets = ChromeVisualShieldNavigationInsets(left = 66, right = 0, bottom = 0),
            )

        assertEquals(ChromeVisualRegion("region", 355, 232, 1986, 548), mapped)
    }

    @Test
    fun `portrait full window bitmap retains viewport mapping when navigation area is present`() {
        val mapped =
            ChromeVisualShieldScreenshotGeometryMapper.toFrame(
                region = ChromeVisualRegion("region", 166, 564, 914, 1278),
                viewport = ChromeVisualViewport(0, 0, 1080, 2408),
                frameWidth = 1080,
                frameHeight = 2408,
                navigationInsets = ChromeVisualShieldNavigationInsets(left = 0, right = 0, bottom = 42),
            )

        assertEquals(ChromeVisualRegion("region", 166, 564, 914, 1278), mapped)
    }
}
