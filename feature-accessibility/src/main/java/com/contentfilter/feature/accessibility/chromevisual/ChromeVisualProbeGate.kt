package com.contentfilter.feature.accessibility.chromevisual

import kotlin.math.abs

internal data class ProbePixelSample(
    val width: Int,
    val height: Int,
    val colors: IntArray,
)

internal data class ProbeUnderlayDecision(
    val passed: Boolean,
    val similarity: Double,
    val overlayColorRatio: Double,
)

internal object ChromeVisualProbeGate {
    private const val MinimumSimilarity = 0.70
    private const val MaximumOverlayColorRatio = 0.15
    private const val OverlayRed = 180
    private const val OverlayGreen = 0
    private const val OverlayBlue = 80
    private const val OverlayChannelTolerance = 20

    fun decide(
        before: ProbePixelSample,
        after: ProbePixelSample,
    ): ProbeUnderlayDecision {
        if (
            before.width <= 0 ||
            before.height <= 0 ||
            before.width != after.width ||
            before.height != after.height ||
            before.colors.isEmpty() ||
            before.colors.size != after.colors.size
        ) {
            return ProbeUnderlayDecision(false, 0.0, 1.0)
        }
        var distance = 0L
        var overlayMatches = 0
        after.colors.indices.forEach { index ->
            val beforeColor = before.colors[index]
            val afterColor = after.colors[index]
            distance += abs(beforeColor.red() - afterColor.red())
            distance += abs(beforeColor.green() - afterColor.green())
            distance += abs(beforeColor.blue() - afterColor.blue())
            if (
                abs(afterColor.red() - OverlayRed) <= OverlayChannelTolerance &&
                abs(afterColor.green() - OverlayGreen) <= OverlayChannelTolerance &&
                abs(afterColor.blue() - OverlayBlue) <= OverlayChannelTolerance
            ) {
                overlayMatches++
            }
        }
        val maximumDistance = after.colors.size.toDouble() * 255.0 * 3.0
        val similarity = (1.0 - distance / maximumDistance).coerceIn(0.0, 1.0)
        val overlayColorRatio = overlayMatches.toDouble() / after.colors.size.toDouble()
        return ProbeUnderlayDecision(
            passed = similarity >= MinimumSimilarity && overlayColorRatio <= MaximumOverlayColorRatio,
            similarity = similarity,
            overlayColorRatio = overlayColorRatio,
        )
    }

    fun clear(sample: ProbePixelSample?) {
        sample?.colors?.fill(0)
    }

    private fun Int.red(): Int = this ushr 16 and 0xff

    private fun Int.green(): Int = this ushr 8 and 0xff

    private fun Int.blue(): Int = this and 0xff
}
