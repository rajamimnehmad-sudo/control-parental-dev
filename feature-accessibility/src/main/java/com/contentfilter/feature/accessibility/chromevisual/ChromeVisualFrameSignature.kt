package com.contentfilter.feature.accessibility.chromevisual

import android.graphics.Bitmap

internal object ChromeVisualFrameSignature {
    fun one(
        bitmap: Bitmap,
        frameRegion: ChromeVisualRegion,
        screenRegion: ChromeVisualRegion,
    ): Long? {
        if (frameRegion.width <= 0 || frameRegion.height <= 0) return null
        var hash = FnvOffsetBasis
        hash = (hash xor screenRegion.left.toLong()) * FnvPrime
        hash = (hash xor screenRegion.top.toLong()) * FnvPrime
        hash = (hash xor screenRegion.width.toLong()) * FnvPrime
        hash = (hash xor screenRegion.height.toLong()) * FnvPrime
        repeat(SignatureRows) { row ->
            val y = frameRegion.top + ((row + 0.5) * frameRegion.height / SignatureRows).toInt()
            repeat(SignatureColumns) { column ->
                val x = frameRegion.left + ((column + 0.5) * frameRegion.width / SignatureColumns).toInt()
                hash = (hash xor bitmap.getPixel(x, y).toLong()) * FnvPrime
            }
        }
        return hash
    }

    fun all(
        bitmap: Bitmap,
        viewport: ChromeVisualViewport,
        regions: List<ChromeVisualRegion>,
    ): Map<String, Long> =
        regions.mapNotNull { region ->
            val frameRegion =
                ChromeVisualGeometryMapper.toFrame(region, viewport, bitmap.width, bitmap.height)
                    ?: return@mapNotNull null
            one(bitmap, frameRegion, region)?.let { region.id to it }
        }.toMap()

    private const val SignatureColumns = 24
    private const val SignatureRows = 16
    private const val FnvOffsetBasis = -3750763034362895579L
    private const val FnvPrime = 1099511628211L
}
