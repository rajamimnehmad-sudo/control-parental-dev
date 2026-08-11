package com.contentfilter.dagbrowser

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

internal data class DagImageFitPlan(
    val contentWidth: Int,
    val contentHeight: Int,
    val offsetX: Int,
    val offsetY: Int,
)

internal object DagImageFitPlanner {
    fun plan(
        sourceWidth: Int,
        sourceHeight: Int,
        targetSize: Int = DagImageDecodeContract.TargetSize,
    ): DagImageFitPlan? {
        if (sourceWidth <= 0 || sourceHeight <= 0 || targetSize <= 0) return null
        val scale =
            min(
                targetSize.toDouble() / sourceWidth.toDouble(),
                targetSize.toDouble() / sourceHeight.toDouble(),
            )
        val contentWidth = (sourceWidth * scale).roundToInt().coerceIn(1, targetSize)
        val contentHeight = (sourceHeight * scale).roundToInt().coerceIn(1, targetSize)
        return DagImageFitPlan(
            contentWidth = contentWidth,
            contentHeight = contentHeight,
            offsetX = (targetSize - contentWidth) / 2,
            offsetY = (targetSize - contentHeight) / 2,
        )
    }
}

internal data class DagPreparedImage(
    val width: Int,
    val height: Int,
    val rgb888: ByteArray,
)

internal data class DagImageCropPlan(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

internal object DagUncertainRegionalCropper {
    fun quadrantViews(image: DagPreparedImage): List<DagPreparedImage> {
        if (!DagImageDecodeContract.isValid(image)) return emptyList()
        val cropSize =
            (DagImageDecodeContract.TargetSize * CropFraction)
                .roundToInt()
                .coerceIn(1, DagImageDecodeContract.TargetSize)
        val lastStart = DagImageDecodeContract.TargetSize - cropSize
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

    private fun DagPreparedImage.cropAndScale(
        left: Int,
        top: Int,
        cropSize: Int,
    ): DagPreparedImage {
        val targetSize = DagImageDecodeContract.TargetSize
        val channels = DagImageDecodeContract.RgbChannelCount
        val output = ByteArray(DagImageDecodeContract.PreparedByteCount)
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
        return DagPreparedImage(
            width = targetSize,
            height = targetSize,
            rgb888 = output,
        )
    }

    private const val CropFraction = 0.56
}

internal object DagRegionalCropPlanner {
    fun plan(
        sourceWidth: Int,
        sourceHeight: Int,
        allowStandardAspect: Boolean = false,
    ): List<DagImageCropPlan> {
        if (sourceWidth <= 0 || sourceHeight <= 0) return emptyList()
        val longEdge = max(sourceWidth, sourceHeight)
        val shortEdge = min(sourceWidth, sourceHeight)
        if (!allowStandardAspect && longEdge.toDouble() / shortEdge.toDouble() < MinAspectRatio) {
            return emptyList()
        }
        return if (sourceWidth >= sourceHeight) {
            val cropWidth = (sourceWidth * CropFraction).roundToInt().coerceIn(1, sourceWidth)
            cropStarts(sourceWidth, cropWidth).map { left ->
                DagImageCropPlan(
                    left = left,
                    top = 0,
                    width = cropWidth,
                    height = sourceHeight,
                )
            }
        } else {
            val cropHeight = (sourceHeight * CropFraction).roundToInt().coerceIn(1, sourceHeight)
            cropStarts(sourceHeight, cropHeight).map { top ->
                DagImageCropPlan(
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
    private const val RegionalDecodeLongEdge = DagImageDecodeContract.TargetSize * 3
}

internal sealed interface DagImagePreprocessResult {
    data class Ready(
        val image: DagPreparedImage,
        val regionalImages: List<DagPreparedImage> = emptyList(),
    ) : DagImagePreprocessResult

    data class Rejected(
        val reason: String,
    ) : DagImagePreprocessResult
}

internal fun interface DagImagePreprocessor {
    fun prepare(bytes: ByteArray): DagImagePreprocessResult
}

/**
 * Produces a bounded, model-neutral RGB image without retaining or persisting source media.
 *
 * The full image is fitted inside a square instead of being cropped. Extremely wide or tall images
 * also receive three bounded regional views so a small subject does not disappear during resize.
 * Animated and partial images fail closed.
 */
internal object AndroidDagImagePreprocessor : DagImagePreprocessor {
    override fun prepare(bytes: ByteArray): DagImagePreprocessResult {
        var decoded: Bitmap? = null
        val preparedImages = mutableListOf<DagPreparedImage>()
        var returnedPreparedImages = false
        return try {
            val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes).asReadOnlyBuffer())
            var expectedDecodeSize: Pair<Int, Int>? = null
            decoded =
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val size = info.size
                    when {
                        info.isAnimated -> throw RejectedHeaderException(AnimatedImageReason)
                        info.mimeType !in DagImageDecodeContract.SupportedMimeTypes ->
                            throw RejectedHeaderException(DagMediaBytesPolicy.UnsupportedImageReason)
                        !DagImageDecodeContract.hasSafeDimensions(size.width, size.height) ->
                            throw RejectedHeaderException(DagMediaBytesPolicy.UnsafeDimensionsReason)
                    }
                    val fullImagePlan =
                        DagImageFitPlanner.plan(size.width, size.height)
                            ?: throw RejectedHeaderException(
                                DagMediaBytesPolicy.UnsafeDimensionsReason,
                            )
                    expectedDecodeSize =
                        DagRegionalCropPlanner.decodeSize(
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
                expectedDecodeSize ?: return DagImagePreprocessResult.Rejected(DecodeFailedReason)
            val sourceBitmap = decoded
            if (
                sourceBitmap.width != decodeSize.first ||
                sourceBitmap.height != decodeSize.second
            ) {
                return DagImagePreprocessResult.Rejected(DecodeFailedReason)
            }

            preparedImages += sourceBitmap.toPreparedImage()
            val regionalCropPlans =
                DagRegionalCropPlanner.plan(
                    sourceBitmap.width,
                    sourceBitmap.height,
                    allowStandardAspect = false,
                )
            regionalCropPlans.forEach { crop -> preparedImages += sourceBitmap.toPreparedImage(crop) }
            DagImagePreprocessResult
                .Ready(
                    image = preparedImages.first(),
                    regionalImages = preparedImages.drop(1),
                ).also {
                    returnedPreparedImages = true
                }
        } catch (rejected: RejectedHeaderException) {
            DagImagePreprocessResult.Rejected(rejected.reason)
        } catch (_: Exception) {
            DagImagePreprocessResult.Rejected(DecodeFailedReason)
        } finally {
            if (!returnedPreparedImages) {
                preparedImages.forEach { it.rgb888.fill(0) }
            }
            decoded?.recycle()
        }
    }

    private fun Bitmap.toPreparedImage(crop: DagImageCropPlan? = null): DagPreparedImage {
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
            DagImageFitPlanner.plan(sourceRect.width(), sourceRect.height())
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
                DagImageDecodeContract.TargetSize,
                DagImageDecodeContract.TargetSize,
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
            DagPreparedImage(
                width = DagImageDecodeContract.TargetSize,
                height = DagImageDecodeContract.TargetSize,
                rgb888 = letterboxed.toRgb888(),
            )
        } finally {
            letterboxed.recycle()
        }
    }

    private fun Bitmap.toRgb888(): ByteArray {
        val output = ByteArray(width * height * DagImageDecodeContract.RgbChannelCount)
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
    const val AnimatedImageReason = "animated_image"
    const val DecodeFailedReason = "decode_failed"
}

internal object DagImageDecodeContract {
    val SupportedMimeTypes =
        setOf(
            "image/avif",
            "image/gif",
            "image/heic",
            "image/heif",
            "image/jpeg",
            "image/png",
            "image/webp",
        )
    const val TargetSize = 224
    const val RgbChannelCount = 3
    const val MaxDimension = 4_096
    const val MaxPixels = 16_777_216L
    const val PreparedByteCount = TargetSize * TargetSize * RgbChannelCount

    fun hasSafeDimensions(
        width: Int,
        height: Int,
    ): Boolean =
        width in 1..MaxDimension &&
            height in 1..MaxDimension &&
            width.toLong() * height.toLong() <= MaxPixels

    fun isValid(image: DagPreparedImage): Boolean =
        image.width == TargetSize &&
            image.height == TargetSize &&
            image.rgb888.size == PreparedByteCount
}
