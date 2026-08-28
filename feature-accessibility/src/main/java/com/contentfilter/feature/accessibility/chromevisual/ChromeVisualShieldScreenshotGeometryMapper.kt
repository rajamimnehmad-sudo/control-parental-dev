package com.contentfilter.feature.accessibility.chromevisual

import kotlin.math.abs

/** Maps screen coordinates into the actual window bitmap returned by Android. */
internal object ChromeVisualShieldScreenshotGeometryMapper {
    fun toFrame(
        region: ChromeVisualRegion,
        viewport: ChromeVisualViewport,
        frameWidth: Int,
        frameHeight: Int,
        navigationInsets: ChromeVisualShieldNavigationInsets,
    ): ChromeVisualRegion? {
        if (!navigationInsets.isValid()) return null
        val availableViewport =
            ChromeVisualViewport(
                left = viewport.left + navigationInsets.left,
                top = viewport.top,
                right = viewport.right - navigationInsets.right,
                bottom = viewport.bottom - navigationInsets.bottom,
            )
        val sourceViewport =
            if (
                availableViewport.width > 0 &&
                availableViewport.height > 0 &&
                abs(frameWidth - availableViewport.width) <= DimensionTolerancePixels &&
                abs(frameHeight - availableViewport.height) <= DimensionTolerancePixels
            ) {
                availableViewport
            } else {
                viewport
            }
        return ChromeVisualGeometryMapper.toFrame(region, sourceViewport, frameWidth, frameHeight)
    }

    private const val DimensionTolerancePixels = 2
}
