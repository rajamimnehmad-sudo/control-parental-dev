package com.contentfilter.user.chromedataplane

import com.glosh.visual.GloshiaImageContract
import com.glosh.visual.GloshiaPreparedImage
import kotlin.math.abs

internal enum class ChromeGifTemporalDecision {
    Analyze,
    Skip,
    Reject,
}

/** Cheap 16x16 signatures keep stable GIF frames off the model while retaining a strict cap. */
internal class ChromeGifTemporalSelector {
    private var expectedFrameIndex = 0
    private var previousTimeMillis = -1
    private var lastAnalysisTimeMillis = -1
    private var lastAnalyzedSignature: IntArray? = null

    fun select(
        frameIndex: Int,
        timeMillis: Int,
        image: GloshiaPreparedImage,
    ): ChromeGifTemporalDecision {
        if (
            frameIndex != expectedFrameIndex ||
            timeMillis <= previousTimeMillis ||
            !GloshiaImageContract.isValid(image)
        ) {
            return ChromeGifTemporalDecision.Reject
        }
        expectedFrameIndex += 1
        previousTimeMillis = timeMillis
        val signature = signature(image)
        val previousSignature = lastAnalyzedSignature
        val cadenceReached = lastAnalysisTimeMillis < 0 || timeMillis - lastAnalysisTimeMillis >= CadenceMillis
        val visualChange = previousSignature != null && materiallyChanged(previousSignature, signature)
        return if (previousSignature == null || cadenceReached || visualChange) {
            lastAnalysisTimeMillis = timeMillis
            lastAnalyzedSignature = signature
            ChromeGifTemporalDecision.Analyze
        } else {
            ChromeGifTemporalDecision.Skip
        }
    }

    private fun signature(image: GloshiaPreparedImage): IntArray {
        val output = IntArray(GridSize * GridSize)
        var outputIndex = 0
        for (gridY in 0 until GridSize) {
            val top = gridY * image.height / GridSize
            val bottom = (gridY + 1) * image.height / GridSize
            for (gridX in 0 until GridSize) {
                val left = gridX * image.width / GridSize
                val right = (gridX + 1) * image.width / GridSize
                var red = 0L
                var green = 0L
                var blue = 0L
                for (y in top until bottom) {
                    for (x in left until right) {
                        val source = (y * image.width + x) * GloshiaImageContract.RgbChannelCount
                        red += image.rgb888[source].toInt() and 0xff
                        green += image.rgb888[source + 1].toInt() and 0xff
                        blue += image.rgb888[source + 2].toInt() and 0xff
                    }
                }
                val pixels = (right - left) * (bottom - top)
                output[outputIndex++] =
                    ((red / pixels).toInt() shl 16) or
                    ((green / pixels).toInt() shl 8) or
                    (blue / pixels).toInt()
            }
        }
        return output
    }

    private fun materiallyChanged(
        previous: IntArray,
        current: IntArray,
    ): Boolean {
        var totalChannelDifference = 0L
        var changedSamples = 0
        var stronglyChangedSamples = 0
        for (index in previous.indices) {
            val previousColor = previous[index]
            val currentColor = current[index]
            val red = channelDifference(previousColor ushr 16, currentColor ushr 16)
            val green = channelDifference(previousColor ushr 8, currentColor ushr 8)
            val blue = channelDifference(previousColor, currentColor)
            totalChannelDifference += red + green + blue
            val maximumDifference = maxOf(red, green, blue)
            if (maximumDifference >= ChangedChannelThreshold) changedSamples += 1
            if (maximumDifference >= StrongChannelThreshold) stronglyChangedSamples += 1
        }
        val meanChannelDifference =
            totalChannelDifference.toDouble() / (previous.size * GloshiaImageContract.RgbChannelCount)
        return meanChannelDifference >= MeanChannelThreshold ||
            changedSamples >= ChangedSampleThreshold ||
            stronglyChangedSamples >= StrongSampleThreshold
    }

    private fun channelDifference(
        first: Int,
        second: Int,
    ): Int {
        return abs((first and 0xff) - (second and 0xff))
    }

    companion object {
        const val CadenceMillis = 500
        private const val GridSize = 16
        private const val MeanChannelThreshold = 12.0
        private const val ChangedChannelThreshold = 32
        private const val StrongChannelThreshold = 96
        private const val ChangedSampleThreshold = 16
        private const val StrongSampleThreshold = 4
    }
}
