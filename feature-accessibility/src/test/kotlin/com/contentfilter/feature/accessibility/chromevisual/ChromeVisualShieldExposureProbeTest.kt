package com.contentfilter.feature.accessibility.chromevisual

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromeVisualShieldExposureProbeTest {
    @Test
    fun `recognizes either ordering of fixture sentinel red and black`() {
        val red = ChromeVisualShieldRgb(220, 20, 48)
        val black = ChromeVisualShieldRgb(3, 3, 3)

        assertTrue(ChromeVisualShieldExposureProbe.isSentinelPair(red, black))
        assertTrue(ChromeVisualShieldExposureProbe.isSentinelPair(black, red))
    }

    @Test
    fun `neutral protected surface does not match sentinel`() {
        val neutral = ChromeVisualShieldRgb(28, 32, 40)
        val label = ChromeVisualShieldRgb(235, 235, 235)

        assertFalse(ChromeVisualShieldExposureProbe.isSentinelPair(neutral, label))
    }

    @Test
    fun `near red without adjacent black is not sufficient`() {
        val red = ChromeVisualShieldRgb(220, 20, 48)
        val white = ChromeVisualShieldRgb(255, 255, 255)

        assertFalse(ChromeVisualShieldExposureProbe.isSentinelPair(red, white))
    }
}
