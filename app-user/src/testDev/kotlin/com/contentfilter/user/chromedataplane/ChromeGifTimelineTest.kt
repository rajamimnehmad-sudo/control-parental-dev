package com.contentfilter.user.chromedataplane

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ChromeGifTimelineTest {
    @Test
    fun `bounded parser retains every animated frame and normalizes timing`() {
        val result = assertIs<ChromeGifTimelineResult.Animated>(ChromeGifTimelineParser.parse(animatedGif()))

        assertEquals(1, result.timeline.width)
        assertEquals(1, result.timeline.height)
        assertEquals(2, result.timeline.frames.size)
        assertEquals(150, result.timeline.durationMillis)
        assertEquals(50, result.timeline.frames[0].sampleTimeMillis)
        assertEquals(125, result.timeline.frames[1].sampleTimeMillis)
    }

    @Test
    fun `malformed animation never becomes an authorized timeline`() {
        val malformed = animatedGif().copyOfRange(0, animatedGif().lastIndex)

        val result = assertIs<ChromeGifTimelineResult.Rejected>(ChromeGifTimelineParser.parse(malformed))

        assertEquals(ChromeGifTimelineParser.MalformedReason, result.reason)
    }

    private fun animatedGif() =
        byteArrayOf(
            *"GIF89a".toByteArray(),
            1, 0, 1, 0, 0x80.toByte(), 0, 0,
            0, 0, 0, 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
            0x2c, 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 1, 0x44, 0,
            0x21, 0xf9.toByte(), 4, 0, 5, 0, 0, 0,
            0x2c, 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 1, 0x4c, 0,
            0x3b,
        )
}
