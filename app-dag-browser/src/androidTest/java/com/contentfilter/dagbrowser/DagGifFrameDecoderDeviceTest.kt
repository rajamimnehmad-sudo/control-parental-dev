package com.contentfilter.dagbrowser

import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DagGifFrameDecoderDeviceTest {
    @Test
    fun androidDecoderRendersEveryPlannedGifFrameIntoTheModelContract() {
        val bytes = animatedGifBytes(frameCount = 2)
        val parsed = DagGifTimelineParser.parse(bytes)
        assertTrue(parsed is DagGifTimelineResult.Animated)
        val timeline = (parsed as DagGifTimelineResult.Animated).timeline
        var renderedFrames = 0

        val result =
            AndroidDagGifFrameDecoder.decode(bytes, timeline) { _, frame ->
                assertTrue(DagImageDecodeContract.isValid(frame))
                renderedFrames += 1
                true
            }

        assertEquals(DagGifFrameDecodeResult.Completed, result)
        assertEquals(2, renderedFrames)
    }

    @Test
    fun sixtyFrameGifDecodesAndRunsThroughTheRealModelWithinTheBound() {
        val bytes = animatedGifBytes(frameCount = 60)
        val parsed = DagGifTimelineParser.parse(bytes)
        assertTrue(parsed is DagGifTimelineResult.Animated)
        val timeline = (parsed as DagGifTimelineResult.Animated).timeline
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val analyzer = DagOnDeviceImageAnalyzer.create(context)
        var classifications = 0
        var decodedFrames = 0
        val selector = DagTemporalFrameSelector()
        val startedAt = SystemClock.elapsedRealtime()
        val result =
            try {
                AndroidDagGifFrameDecoder.decode(bytes, timeline) { timelineFrame, frame ->
                    val decision =
                        selector.select(decodedFrames, timelineFrame.sampleTimeMillis, frame)
                    decodedFrames += 1
                    if (decision == DagTemporalFrameDecision.Analyze) {
                        if (analyzer.analyze(frame) is DagImageAnalysisResult.Classified) {
                            classifications += 1
                        }
                    }
                    true
                }
            } finally {
                (analyzer as? AutoCloseable)?.close()
            }
        val elapsedMillis = SystemClock.elapsedRealtime() - startedAt

        Log.i(
            LogTag,
            "decoded=$decodedFrames inferences=$classifications elapsed_ms=$elapsedMillis",
        )
        assertEquals(DagGifFrameDecodeResult.Completed, result)
        assertEquals(60, decodedFrames)
        assertEquals(6, classifications)
        assertTrue("GIF analysis exceeded 2 seconds: $elapsedMillis ms", elapsedMillis < 2_000)
    }

    private fun animatedGifBytes(frameCount: Int): ByteArray =
        buildString {
            append("47494638396101000100800000000000ffffff")
            repeat(frameCount) {
                append("21f90400050000002c00000000010001000002014c00")
            }
            append("3b")
        }
            .chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()

    private companion object {
        const val LogTag = "DagGifDeviceTest"
    }
}
