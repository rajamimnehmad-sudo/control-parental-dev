package com.contentfilter.dagbrowser

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.Paint
import java.nio.ByteBuffer
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

internal sealed interface DagImagePreprocessResult {
    data class Ready(
        val image: DagPreparedImage,
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
 * The full image is fitted inside a square instead of being cropped. This preserves clothing and
 * body context for the future directed classifier. Animated and partial images fail closed.
 */
internal object AndroidDagImagePreprocessor : DagImagePreprocessor {
    override fun prepare(bytes: ByteArray): DagImagePreprocessResult {
        var decoded: Bitmap? = null
        var letterboxed: Bitmap? = null
        return try {
            val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes).asReadOnlyBuffer())
            var fitPlan: DagImageFitPlan? = null
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
                    val plan =
                        DagImageFitPlanner.plan(size.width, size.height)
                            ?: throw RejectedHeaderException(DagMediaBytesPolicy.UnsafeDimensionsReason)
                    fitPlan = plan
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
                    decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                    decoder.setTargetSize(plan.contentWidth, plan.contentHeight)
                    decoder.setOnPartialImageListener { false }
                }

            val plan = fitPlan ?: return DagImagePreprocessResult.Rejected(DecodeFailedReason)
            val sourceBitmap = decoded
            if (
                sourceBitmap.width != plan.contentWidth ||
                sourceBitmap.height != plan.contentHeight
            ) {
                return DagImagePreprocessResult.Rejected(DecodeFailedReason)
            }

            letterboxed =
                Bitmap.createBitmap(
                    DagImageDecodeContract.TargetSize,
                    DagImageDecodeContract.TargetSize,
                    Bitmap.Config.ARGB_8888,
                )
            letterboxed.eraseColor(PaddingColor)
            Canvas(letterboxed).drawBitmap(
                sourceBitmap,
                plan.offsetX.toFloat(),
                plan.offsetY.toFloat(),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )

            DagImagePreprocessResult.Ready(
                DagPreparedImage(
                    width = DagImageDecodeContract.TargetSize,
                    height = DagImageDecodeContract.TargetSize,
                    rgb888 = letterboxed.toRgb888(),
                ),
            )
        } catch (rejected: RejectedHeaderException) {
            DagImagePreprocessResult.Rejected(rejected.reason)
        } catch (_: Exception) {
            DagImagePreprocessResult.Rejected(DecodeFailedReason)
        } finally {
            decoded?.recycle()
            letterboxed?.recycle()
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
