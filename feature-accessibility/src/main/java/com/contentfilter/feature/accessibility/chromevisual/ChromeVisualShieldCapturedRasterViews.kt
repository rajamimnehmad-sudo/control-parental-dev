package com.contentfilter.feature.accessibility.chromevisual

import android.graphics.Bitmap
import com.glosh.visual.AndroidGloshiaImagePreprocessor
import com.glosh.visual.GloshiaImageCropPlan
import com.glosh.visual.GloshiaPreparedImage
import com.glosh.visual.GloshiaRegionalCropPlanner

/**
 * Chrome-owned adapter from one captured crop to the canonical full and regional GloshIA views.
 * It owns every prepared RGB buffer and zeroes them together when the analysis ends.
 */
internal class ChromeVisualShieldCapturedRasterViews private constructor(
    val cropPlans: List<GloshiaImageCropPlan>,
    val preparedImages: List<GloshiaPreparedImage>,
) : AutoCloseable {
    override fun close() {
        preparedImages.forEach { it.rgb888.fill(0) }
    }

    companion object {
        fun prepare(
            bitmap: Bitmap,
            includeCanonicalRegions: Boolean,
        ): ChromeVisualShieldCapturedRasterViews? {
            if (bitmap.isRecycled) return null
            val prepared = mutableListOf<GloshiaPreparedImage>()
            return try {
                val full =
                    AndroidGloshiaImagePreprocessor.prepareVideoCapturedRaster(
                        bitmap = bitmap,
                        maxLongEdge = maxOf(bitmap.width, bitmap.height),
                    ) ?: return null
                prepared += full
                val plans =
                    if (includeCanonicalRegions) {
                        planCanonicalRegions(bitmap.width, bitmap.height)
                    } else {
                        emptyList()
                    }
                plans.forEach { plan ->
                    val regional =
                        Bitmap.createBitmap(
                            bitmap,
                            plan.left,
                            plan.top,
                            plan.width,
                            plan.height,
                        )
                    try {
                        prepared +=
                            AndroidGloshiaImagePreprocessor.prepareVideoCapturedRaster(
                                bitmap = regional,
                                maxLongEdge = maxOf(regional.width, regional.height),
                            ) ?: error("canonical regional raster preparation failed")
                    } finally {
                        if (regional !== bitmap) regional.recycle()
                    }
                }
                ChromeVisualShieldCapturedRasterViews(plans, prepared.toList())
            } catch (_: Exception) {
                prepared.forEach { it.rgb888.fill(0) }
                null
            }
        }

        fun planCanonicalRegions(
            sourceWidth: Int,
            sourceHeight: Int,
        ): List<GloshiaImageCropPlan> =
            GloshiaRegionalCropPlanner.plan(
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                allowStandardAspect = false,
            )
    }
}
