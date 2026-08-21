package com.contentfilter.feature.accessibility.chromevisual

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromeVisualProbeGateTest {
    @Test
    fun `accepts stable content beneath overlay`() {
        val before = sample(0xff204060.toInt(), 0xff406080.toInt())
        val after = sample(0xff214161.toInt(), 0xff426282.toInt())

        assertTrue(ChromeVisualProbeGate.decide(before, after).passed)
    }

    @Test
    fun `rejects screenshot that contains overlay color`() {
        val before = sample(0xff204060.toInt(), 0xff406080.toInt())
        val after = ProbePixelSample(2, 2, IntArray(4) { 0xffb40050.toInt() })

        assertFalse(ChromeVisualProbeGate.decide(before, after).passed)
    }

    @Test
    fun `rejects unrelated content`() {
        val before = solidSample(0xff000000.toInt())
        val after = solidSample(0xffffffff.toInt())

        assertFalse(ChromeVisualProbeGate.decide(before, after).passed)
    }

    @Test
    fun `rejects mismatched samples`() {
        val before = ProbePixelSample(2, 2, IntArray(4))
        val after = ProbePixelSample(1, 2, IntArray(2))

        assertFalse(ChromeVisualProbeGate.decide(before, after).passed)
    }

    private fun sample(
        first: Int,
        second: Int,
    ): ProbePixelSample = ProbePixelSample(2, 2, intArrayOf(first, second, first, second))

    private fun solidSample(color: Int): ProbePixelSample = ProbePixelSample(2, 2, IntArray(4) { color })
}
