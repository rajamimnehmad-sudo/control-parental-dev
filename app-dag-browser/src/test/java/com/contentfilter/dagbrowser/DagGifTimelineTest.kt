package com.contentfilter.dagbrowser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DagGifTimelineTest {
    @Test
    fun `every GIF frame receives its own sample`() {
        val result = assertIs<DagGifTimelineResult.Animated>(parseGif(5, 10, 2))

        assertEquals(2, result.timeline.width)
        assertEquals(2, result.timeline.height)
        assertEquals(170, result.timeline.durationMillis)
        assertEquals(
            listOf(
                DagGifFrame(sampleTimeMillis = 25, durationMillis = 50),
                DagGifFrame(sampleTimeMillis = 100, durationMillis = 100),
                DagGifFrame(sampleTimeMillis = 160, durationMillis = 20),
            ),
            result.timeline.frames,
        )
    }

    @Test
    fun `zero and sub reliable delays are normalized instead of collapsing frames`() {
        val result = assertIs<DagGifTimelineResult.Animated>(parseGif(0, 1))

        assertEquals(200, result.timeline.durationMillis)
        assertEquals(listOf(50, 150), result.timeline.frames.map(DagGifFrame::sampleTimeMillis))
    }

    @Test
    fun `single frame GIF remains on the existing static image path`() {
        assertIs<DagGifTimelineResult.StaticGif>(parseGif(5))
    }

    @Test
    fun `non GIF bytes do not enter the animated decoder`() {
        assertIs<DagGifTimelineResult.NotGif>(DagGifTimelineParser.parse(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `truncated GIF fails closed`() {
        val bytes = gifBytes(listOf(5, 5)).copyOf(20)
        val result = assertIs<DagGifTimelineResult.Rejected>(DagGifTimelineParser.parse(bytes))

        assertEquals(DagGifTimelineParser.MalformedReason, result.reason)
    }

    @Test
    fun `frame outside the logical screen fails closed`() {
        val bytes = gifBytes(listOf(5, 5), frameWidth = 3)
        val result = assertIs<DagGifTimelineResult.Rejected>(DagGifTimelineParser.parse(bytes))

        assertEquals(DagGifTimelineParser.UnsafeFrameBoundsReason, result.reason)
    }

    @Test
    fun `frame count is bounded without skipping uninspected content`() {
        val bytes = gifBytes(List(DagGifTimelineParser.MaximumFrameCount + 1) { 2 })
        val result = assertIs<DagGifTimelineResult.Rejected>(DagGifTimelineParser.parse(bytes))

        assertEquals(DagGifTimelineParser.TooManyFramesReason, result.reason)
    }

    @Test
    fun `duration is bounded without partial approval`() {
        val bytes = gifBytes(listOf(40_000, 40_000), delaysAreHundredths = true)
        val result = assertIs<DagGifTimelineResult.Rejected>(DagGifTimelineParser.parse(bytes))

        assertEquals(DagGifTimelineParser.DurationTooLongReason, result.reason)
    }

    private fun parseGif(vararg delaysHundredths: Int): DagGifTimelineResult =
        DagGifTimelineParser.parse(gifBytes(delaysHundredths.toList()))

    private fun gifBytes(
        delays: List<Int>,
        width: Int = 2,
        height: Int = 2,
        frameWidth: Int = width,
        delaysAreHundredths: Boolean = true,
    ): ByteArray {
        val output = mutableListOf<Int>()
        output += "GIF89a".map(Char::code)
        output += littleEndian(width)
        output += littleEndian(height)
        output += listOf(0x80, 0, 0)
        output += listOf(0, 0, 0, 255, 255, 255)
        for (delay in delays) {
            val delayHundredths = if (delaysAreHundredths) delay else delay / 10
            output += listOf(0x21, 0xf9, 4, 0)
            output += littleEndian(delayHundredths)
            output += listOf(0, 0)
            output += 0x2c
            output += littleEndian(0)
            output += littleEndian(0)
            output += littleEndian(frameWidth)
            output += littleEndian(height)
            output += listOf(0, 2, 2, 0x4c, 0x01, 0)
        }
        output += 0x3b
        return output.map(Int::toByte).toByteArray()
    }

    private fun littleEndian(value: Int): List<Int> = listOf(value and 0xff, value ushr 8 and 0xff)
}
