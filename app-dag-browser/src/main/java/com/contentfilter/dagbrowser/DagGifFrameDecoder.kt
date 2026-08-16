@file:Suppress("DEPRECATION") // Movie is Android's public seekable GIF decoder.

package com.contentfilter.dagbrowser

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Movie
import android.graphics.Paint

internal sealed interface DagGifFrameDecodeResult {
    data object Completed : DagGifFrameDecodeResult

    data object Stopped : DagGifFrameDecodeResult

    data class Rejected(
        val reason: String,
    ) : DagGifFrameDecodeResult
}

internal fun interface DagGifFrameDecoder {
    fun decode(
        bytes: ByteArray,
        timeline: DagGifTimeline,
        inspectFrame: (DagGifFrame, DagPreparedImage) -> Boolean,
    ): DagGifFrameDecodeResult
}

/** Renders one bounded GIF frame at a time; decoded pixels are cleared before advancing. */
internal object AndroidDagGifFrameDecoder : DagGifFrameDecoder {
    override fun decode(
        bytes: ByteArray,
        timeline: DagGifTimeline,
        inspectFrame: (DagGifFrame, DagPreparedImage) -> Boolean,
    ): DagGifFrameDecodeResult {
        val movie =
            runCatching { Movie.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
                ?: return rejected(DecodeFailedReason)
        if (
            movie.width() != timeline.width ||
            movie.height() != timeline.height ||
            !DagImageDecodeContract.hasSafeDimensions(movie.width(), movie.height())
        ) {
            return rejected(DecodeMismatchReason)
        }
        val plan =
            DagImageFitPlanner.plan(movie.width(), movie.height())
                ?: return rejected(DecodeMismatchReason)
        val bitmap =
            runCatching {
                Bitmap.createBitmap(
                    DagImageDecodeContract.TargetSize,
                    DagImageDecodeContract.TargetSize,
                    Bitmap.Config.ARGB_8888,
                )
            }.getOrNull() ?: return rejected(DecodeFailedReason)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        return try {
            for (frame in timeline.frames) {
                bitmap.eraseColor(PaddingColor)
                movie.setTime(frame.sampleTimeMillis)
                val checkpoint = canvas.save()
                canvas.translate(plan.offsetX.toFloat(), plan.offsetY.toFloat())
                canvas.scale(
                    plan.contentWidth.toFloat() / movie.width(),
                    plan.contentHeight.toFloat() / movie.height(),
                )
                movie.draw(canvas, 0f, 0f, paint)
                canvas.restoreToCount(checkpoint)
                val prepared = bitmap.toPreparedImage()
                val shouldContinue =
                    try {
                        inspectFrame(frame, prepared)
                    } finally {
                        prepared.rgb888.fill(0)
                    }
                if (!shouldContinue) return DagGifFrameDecodeResult.Stopped
            }
            DagGifFrameDecodeResult.Completed
        } catch (_: Exception) {
            rejected(DecodeFailedReason)
        } finally {
            bitmap.eraseColor(Color.TRANSPARENT)
            bitmap.recycle()
        }
    }

    private fun Bitmap.toPreparedImage(): DagPreparedImage {
        val rgb = ByteArray(width * height * DagImageDecodeContract.RgbChannelCount)
        val row = IntArray(width)
        var outputIndex = 0
        for (y in 0 until height) {
            getPixels(row, 0, width, 0, y, width, 1)
            for (pixel in row) {
                rgb[outputIndex++] = Color.red(pixel).toByte()
                rgb[outputIndex++] = Color.green(pixel).toByte()
                rgb[outputIndex++] = Color.blue(pixel).toByte()
            }
        }
        return DagPreparedImage(width = width, height = height, rgb888 = rgb)
    }

    private fun rejected(reason: String) = DagGifFrameDecodeResult.Rejected(reason)

    const val DecodeFailedReason = "gif_decode_failed"
    const val DecodeMismatchReason = "gif_decode_mismatch"
    private const val PaddingColor = -8_421_505
}
