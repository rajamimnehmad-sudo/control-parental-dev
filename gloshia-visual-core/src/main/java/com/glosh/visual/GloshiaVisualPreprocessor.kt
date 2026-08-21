package com.glosh.visual

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class GloshiaImageFitPlan(
    val contentWidth: Int,
    val contentHeight: Int,
    val offsetX: Int,
    val offsetY: Int,
)

object GloshiaImageFitPlanner {
    fun plan(
        sourceWidth: Int,
        sourceHeight: Int,
        targetSize: Int = GloshiaImageContract.TargetSize,
    ): GloshiaImageFitPlan? {
        if (sourceWidth <= 0 || sourceHeight <= 0 || targetSize <= 0) return null
        val scale =
            min(
                targetSize.toDouble() / sourceWidth.toDouble(),
                targetSize.toDouble() / sourceHeight.toDouble(),
            )
        val contentWidth = (sourceWidth * scale).roundToInt().coerceIn(1, targetSize)
        val contentHeight = (sourceHeight * scale).roundToInt().coerceIn(1, targetSize)
        return GloshiaImageFitPlan(
            contentWidth = contentWidth,
            contentHeight = contentHeight,
            offsetX = (targetSize - contentWidth) / 2,
            offsetY = (targetSize - contentHeight) / 2,
        )
    }
}

data class GloshiaImageCropPlan(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

object GloshiaUncertainRegionalCropper {
    fun quadrantViews(image: GloshiaPreparedImage): List<GloshiaPreparedImage> {
        if (!GloshiaImageContract.isValid(image)) return emptyList()
        val cropSize =
            (GloshiaImageContract.TargetSize * CropFraction)
                .roundToInt()
                .coerceIn(1, GloshiaImageContract.TargetSize)
        val lastStart = GloshiaImageContract.TargetSize - cropSize
        return listOf(
            Pair(0, 0),
            Pair(lastStart, 0),
            Pair(0, lastStart),
            Pair(lastStart, lastStart),
        ).map { (left, top) ->
            image.cropAndScale(
                left = left,
                top = top,
                cropSize = cropSize,
            )
        }
    }

    private fun GloshiaPreparedImage.cropAndScale(
        left: Int,
        top: Int,
        cropSize: Int,
    ): GloshiaPreparedImage {
        val targetSize = GloshiaImageContract.TargetSize
        val channels = GloshiaImageContract.RgbChannelCount
        val output = ByteArray(GloshiaImageContract.PreparedByteCount)
        var outputIndex = 0
        for (targetY in 0 until targetSize) {
            val sourceY = top + (targetY * cropSize / targetSize).coerceAtMost(cropSize - 1)
            for (targetX in 0 until targetSize) {
                val sourceX = left + (targetX * cropSize / targetSize).coerceAtMost(cropSize - 1)
                val sourceIndex = (sourceY * width + sourceX) * channels
                repeat(channels) { channel ->
                    output[outputIndex++] = rgb888[sourceIndex + channel]
                }
            }
        }
        return GloshiaPreparedImage(
            width = targetSize,
            height = targetSize,
            rgb888 = output,
        )
    }

    private const val CropFraction = 0.56
}

object GloshiaRegionalCropPlanner {
    fun plan(
        sourceWidth: Int,
        sourceHeight: Int,
        allowStandardAspect: Boolean = false,
    ): List<GloshiaImageCropPlan> {
        if (sourceWidth <= 0 || sourceHeight <= 0) return emptyList()
        val longEdge = max(sourceWidth, sourceHeight)
        val shortEdge = min(sourceWidth, sourceHeight)
        if (!allowStandardAspect && longEdge.toDouble() / shortEdge.toDouble() < MinAspectRatio) {
            return emptyList()
        }
        return if (sourceWidth >= sourceHeight) {
            val cropWidth = (sourceWidth * CropFraction).roundToInt().coerceIn(1, sourceWidth)
            cropStarts(sourceWidth, cropWidth).map { left ->
                GloshiaImageCropPlan(
                    left = left,
                    top = 0,
                    width = cropWidth,
                    height = sourceHeight,
                )
            }
        } else {
            val cropHeight = (sourceHeight * CropFraction).roundToInt().coerceIn(1, sourceHeight)
            cropStarts(sourceHeight, cropHeight).map { top ->
                GloshiaImageCropPlan(
                    left = 0,
                    top = top,
                    width = sourceWidth,
                    height = cropHeight,
                )
            }
        }
    }

    fun decodeSize(
        sourceWidth: Int,
        sourceHeight: Int,
        allowStandardAspect: Boolean = false,
    ): Pair<Int, Int>? {
        if (plan(sourceWidth, sourceHeight, allowStandardAspect).isEmpty()) return null
        val scale =
            min(
                1.0,
                RegionalDecodeLongEdge.toDouble() / max(sourceWidth, sourceHeight).toDouble(),
            )
        return Pair(
            (sourceWidth * scale).roundToInt().coerceAtLeast(1),
            (sourceHeight * scale).roundToInt().coerceAtLeast(1),
        )
    }

    private fun cropStarts(
        longEdge: Int,
        cropLength: Int,
    ): List<Int> =
        listOf(
            0,
            (longEdge - cropLength) / 2,
            longEdge - cropLength,
        ).distinct()

    private const val MinAspectRatio = 2.0
    private const val CropFraction = 0.42
    private const val RegionalDecodeLongEdge = GloshiaImageContract.TargetSize * 3
}

data class GloshiaImageBounds(
    val width: Int,
    val height: Int,
    val mimeType: String,
)

sealed interface GloshiaImagePreprocessResult {
    data class Ready(
        val image: GloshiaPreparedImage,
        val regionalImages: List<GloshiaPreparedImage> = emptyList(),
        val sourceBounds: GloshiaImageBounds? = null,
    ) : GloshiaImagePreprocessResult

    data class Rejected(
        val reason: String,
    ) : GloshiaImagePreprocessResult
}

fun interface GloshiaImagePreprocessor {
    fun prepare(bytes: ByteArray): GloshiaImagePreprocessResult
}

/**
 * Produces a bounded, model-neutral RGB image without retaining or persisting source media.
 *
 * The full image is fitted inside a square instead of being cropped. Extremely wide or tall images
 * also receive three bounded regional views so a small subject does not disappear during resize.
 * Animated and partial images fail closed.
 */
object AndroidGloshiaImagePreprocessor : GloshiaImagePreprocessor {
    /**
     * Converts the bounded, aspect-preserved PixelCopy bitmap used by strict video replay into the
     * canonical R3.1 RGB tensor. The source bitmap is never resized in place or recycled here: the
     * same captured pixels remain available for native replay only after their decision is allowed.
     */
    fun prepareVideoCapturedRaster(
        bitmap: Bitmap,
        maxLongEdge: Int,
    ): GloshiaPreparedImage? {
        if (
            bitmap.isRecycled ||
            bitmap.width !in 1..maxLongEdge ||
            bitmap.height !in 1..maxLongEdge
        ) {
            return null
        }
        return runCatching { bitmap.toPreparedImage() }.getOrNull()
    }

    fun prepareCapturedRaster(bitmap: Bitmap): GloshiaPreparedImage? {
        if (
            bitmap.isRecycled ||
            bitmap.width != GloshiaImageContract.TargetSize ||
            bitmap.height != GloshiaImageContract.TargetSize
        ) {
            return null
        }
        return GloshiaPreparedImage(
            width = bitmap.width,
            height = bitmap.height,
            rgb888 = bitmap.toRgb888(),
        )
    }

    override fun prepare(bytes: ByteArray): GloshiaImagePreprocessResult {
        var decoded: Bitmap? = null
        val preparedImages = mutableListOf<GloshiaPreparedImage>()
        var returnedPreparedImages = false
        return try {
            val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes).asReadOnlyBuffer())
            var expectedDecodeSize: Pair<Int, Int>? = null
            var sourceBounds: GloshiaImageBounds? = null
            decoded =
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val size = info.size
                    when {
                        info.isAnimated -> throw RejectedHeaderException(AnimatedImageReason)
                        info.mimeType !in GloshiaImageContract.SupportedMimeTypes ->
                            throw RejectedHeaderException(UnsupportedImageReason)
                        !GloshiaImageContract.hasSafeDimensions(size.width, size.height) ->
                            throw RejectedHeaderException(UnsafeDimensionsReason)
                    }
                    sourceBounds =
                        GloshiaImageBounds(
                            width = size.width,
                            height = size.height,
                            mimeType = info.mimeType,
                        )
                    val fullImagePlan =
                        GloshiaImageFitPlanner.plan(size.width, size.height)
                            ?: throw RejectedHeaderException(
                                UnsafeDimensionsReason,
                            )
                    expectedDecodeSize =
                        GloshiaRegionalCropPlanner.decodeSize(
                            size.width,
                            size.height,
                            allowStandardAspect = false,
                        )
                            ?: Pair(fullImagePlan.contentWidth, fullImagePlan.contentHeight)
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
                    decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                    decoder.setTargetSize(
                        requireNotNull(expectedDecodeSize).first,
                        requireNotNull(expectedDecodeSize).second,
                    )
                    decoder.setOnPartialImageListener { false }
                }

            val decodeSize =
                expectedDecodeSize ?: return GloshiaImagePreprocessResult.Rejected(DecodeFailedReason)
            val sourceBitmap = decoded
            if (
                sourceBitmap.width != decodeSize.first ||
                sourceBitmap.height != decodeSize.second
            ) {
                return GloshiaImagePreprocessResult.Rejected(DecodeFailedReason)
            }

            preparedImages += sourceBitmap.toPreparedImage()
            val regionalCropPlans =
                GloshiaRegionalCropPlanner.plan(
                    sourceBitmap.width,
                    sourceBitmap.height,
                    allowStandardAspect = false,
                )
            regionalCropPlans.forEach { crop -> preparedImages += sourceBitmap.toPreparedImage(crop) }
            GloshiaImagePreprocessResult
                .Ready(
                    image = preparedImages.first(),
                    regionalImages = preparedImages.drop(1),
                    sourceBounds = requireNotNull(sourceBounds),
                ).also {
                    returnedPreparedImages = true
                }
        } catch (rejected: RejectedHeaderException) {
            GloshiaImagePreprocessResult.Rejected(rejected.reason)
        } catch (_: Exception) {
            GloshiaImagePreprocessResult.Rejected(DecodeFailedReason)
        } finally {
            if (!returnedPreparedImages) {
                preparedImages.forEach { it.rgb888.fill(0) }
            }
            decoded?.recycle()
        }
    }

    private fun Bitmap.toPreparedImage(crop: GloshiaImageCropPlan? = null): GloshiaPreparedImage {
        val sourceRect =
            crop?.let {
                Rect(
                    it.left,
                    it.top,
                    it.left + it.width,
                    it.top + it.height,
                )
            } ?: Rect(0, 0, width, height)
        val plan =
            GloshiaImageFitPlanner.plan(sourceRect.width(), sourceRect.height())
                ?: throw RejectedHeaderException(DecodeFailedReason)
        val destinationRect =
            Rect(
                plan.offsetX,
                plan.offsetY,
                plan.offsetX + plan.contentWidth,
                plan.offsetY + plan.contentHeight,
            )
        val letterboxed =
            Bitmap.createBitmap(
                GloshiaImageContract.TargetSize,
                GloshiaImageContract.TargetSize,
                Bitmap.Config.ARGB_8888,
            )
        return try {
            letterboxed.eraseColor(PaddingColor)
            Canvas(letterboxed).drawBitmap(
                this,
                sourceRect,
                destinationRect,
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
            GloshiaPreparedImage(
                width = GloshiaImageContract.TargetSize,
                height = GloshiaImageContract.TargetSize,
                rgb888 = letterboxed.toRgb888(),
            )
        } finally {
            letterboxed.recycle()
        }
    }

    private fun Bitmap.toRgb888(): ByteArray {
        val output = ByteArray(width * height * GloshiaImageContract.RgbChannelCount)
        val row = IntArray(width)
        var outputIndex = 0
        for (y in 0 until height) {
            getPixels(row, 0, width, 0, y, width, 1)
            for (pixel in row) {
                output[outputIndex++] = Color.red(pixel).toByte()
                output[outputIndex++] = Color.green(pixel).toByte()
                output[outputIndex++] = Color.blue(pixel).toByte()
            }
        }
        return output
    }

    private class RejectedHeaderException(
        val reason: String,
    ) : RuntimeException()

    // Signed representation of opaque sRGB #7F7F7F; kept constant for local JVM tests.
    private const val PaddingColor = -8_421_505
    const val AnimatedImageReason = GloshiaVisualPolicyContract.AnimatedImageReason
    const val DecodeFailedReason = GloshiaVisualPolicyContract.DecodeFailedReason
    const val UnsupportedImageReason = "unsupported_image"
    const val UnsafeDimensionsReason = "unsafe_dimensions"
}
