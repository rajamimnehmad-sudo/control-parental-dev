package com.contentfilter.user.chromedataplane

import kotlin.math.min

internal data class ChromeVisualShieldContainGeometry(
    val left: Double,
    val top: Double,
    val width: Double,
    val height: Double,
)

/** Pure mirror of the Canvas `contain` contract used by the DEV fixture. */
internal object ChromeVisualShieldContainContract {
    const val Version = "canvas-contain-neutral-v1"
    const val NeutralBackground = "#7f7f7f"

    fun geometry(
        sourceWidth: Int,
        sourceHeight: Int,
        canvasWidth: Int,
        canvasHeight: Int,
    ): ChromeVisualShieldContainGeometry? {
        if (sourceWidth <= 0 || sourceHeight <= 0 || canvasWidth <= 0 || canvasHeight <= 0) return null
        val scale = min(canvasWidth.toDouble() / sourceWidth, canvasHeight.toDouble() / sourceHeight)
        val width = sourceWidth * scale
        val height = sourceHeight * scale
        return ChromeVisualShieldContainGeometry(
            left = (canvasWidth - width) / 2.0,
            top = (canvasHeight - height) / 2.0,
            width = width,
            height = height,
        )
    }
}
