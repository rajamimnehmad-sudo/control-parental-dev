package com.contentfilter.dagbrowser

import android.graphics.Bitmap
import com.glosh.visual.AndroidGloshiaImagePreprocessor
import com.glosh.visual.GloshiaImageContract
import com.glosh.visual.GloshiaImageCropPlan
import com.glosh.visual.GloshiaImageFitPlan
import com.glosh.visual.GloshiaImageFitPlanner
import com.glosh.visual.GloshiaImagePreprocessResult
import com.glosh.visual.GloshiaPreparedImage
import com.glosh.visual.GloshiaRegionalCropPlanner
import com.glosh.visual.GloshiaUncertainRegionalCropper

internal typealias DagImageFitPlan = GloshiaImageFitPlan
internal typealias DagPreparedImage = GloshiaPreparedImage
internal typealias DagImageCropPlan = GloshiaImageCropPlan

internal object DagImageFitPlanner {
    fun plan(
        sourceWidth: Int,
        sourceHeight: Int,
        targetSize: Int = DagImageDecodeContract.TargetSize,
    ): DagImageFitPlan? = GloshiaImageFitPlanner.plan(sourceWidth, sourceHeight, targetSize)
}

internal object DagUncertainRegionalCropper {
    fun quadrantViews(image: DagPreparedImage): List<DagPreparedImage> =
        GloshiaUncertainRegionalCropper.quadrantViews(image)
}

internal object DagRegionalCropPlanner {
    fun plan(
        sourceWidth: Int,
        sourceHeight: Int,
        allowStandardAspect: Boolean = false,
    ): List<DagImageCropPlan> = GloshiaRegionalCropPlanner.plan(sourceWidth, sourceHeight, allowStandardAspect)

    fun decodeSize(
        sourceWidth: Int,
        sourceHeight: Int,
        allowStandardAspect: Boolean = false,
    ): Pair<Int, Int>? = GloshiaRegionalCropPlanner.decodeSize(sourceWidth, sourceHeight, allowStandardAspect)
}

internal sealed interface DagImagePreprocessResult {
    data class Ready(
        val image: DagPreparedImage,
        val regionalImages: List<DagPreparedImage> = emptyList(),
        val sourceBounds: DagImageBounds? = null,
    ) : DagImagePreprocessResult

    data class Rejected(val reason: String) : DagImagePreprocessResult
}

internal fun interface DagImagePreprocessor {
    fun prepare(bytes: ByteArray): DagImagePreprocessResult
}

internal object AndroidDagImagePreprocessor : DagImagePreprocessor {
    fun prepareVideoCapturedRaster(bitmap: Bitmap): DagPreparedImage? =
        AndroidGloshiaImagePreprocessor.prepareVideoCapturedRaster(
            bitmap,
            DagVideoLabCapturePlan.DefaultMaxLongEdge,
        )

    fun prepareCapturedRaster(bitmap: Bitmap): DagPreparedImage? =
        AndroidGloshiaImagePreprocessor.prepareCapturedRaster(bitmap)

    override fun prepare(bytes: ByteArray): DagImagePreprocessResult =
        when (val result = AndroidGloshiaImagePreprocessor.prepare(bytes)) {
            is GloshiaImagePreprocessResult.Ready ->
                DagImagePreprocessResult.Ready(
                    image = result.image,
                    regionalImages = result.regionalImages,
                    sourceBounds =
                        result.sourceBounds?.let {
                            DagImageBounds(it.width, it.height, it.mimeType)
                        },
                )
            is GloshiaImagePreprocessResult.Rejected ->
                DagImagePreprocessResult.Rejected(result.reason)
        }

    const val AnimatedImageReason = AndroidGloshiaImagePreprocessor.AnimatedImageReason
    const val DecodeFailedReason = AndroidGloshiaImagePreprocessor.DecodeFailedReason
}

internal object DagImageDecodeContract {
    val SupportedMimeTypes = GloshiaImageContract.SupportedMimeTypes
    const val TargetSize = GloshiaImageContract.TargetSize
    const val RgbChannelCount = GloshiaImageContract.RgbChannelCount
    const val MaxDimension = GloshiaImageContract.MaxDimension
    const val MaxPixels = GloshiaImageContract.MaxPixels
    const val PreparedByteCount = GloshiaImageContract.PreparedByteCount

    fun hasSafeDimensions(
        width: Int,
        height: Int,
    ): Boolean = GloshiaImageContract.hasSafeDimensions(width, height)

    fun isValid(image: DagPreparedImage): Boolean = GloshiaImageContract.isValid(image)
}
