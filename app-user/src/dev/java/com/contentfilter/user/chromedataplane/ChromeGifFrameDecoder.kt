@file:Suppress("DEPRECATION") // Movie is Android's bounded, seekable GIF decoder.

package com.contentfilter.user.chromedataplane

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Movie
import android.graphics.Paint
import com.glosh.visual.AndroidGloshiaImagePreprocessor
import com.glosh.visual.GloshiaImageContract
import com.glosh.visual.GloshiaPreparedImage

internal sealed interface ChromeGifFrameDecodeResult {
    data object Completed : ChromeGifFrameDecodeResult

    data object Stopped : ChromeGifFrameDecodeResult

    data class Rejected(
        val reason: String,
    ) : ChromeGifFrameDecodeResult
}

internal fun interface ChromeGifFrameDecoder {
    fun decode(
        bytes: ByteArray,
        timeline: ChromeGifTimeline,
        inspectFrame: (ChromeGifFrame, GloshiaPreparedImage) -> Boolean,
    ): ChromeGifFrameDecodeResult
}

/** Renders one bounded frame at a time and clears all decoded pixels before advancing. */
internal object AndroidChromeGifFrameDecoder : ChromeGifFrameDecoder {
    override fun decode(
        bytes: ByteArray,
        timeline: ChromeGifTimeline,
        inspectFrame: (ChromeGifFrame, GloshiaPreparedImage) -> Boolean,
    ): ChromeGifFrameDecodeResult {
        val movie =
            runCatching { Movie.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
                ?: return rejected(DecodeFailedReason)
        if (
            movie.width() != timeline.width ||
            movie.height() != timeline.height ||
            !GloshiaImageContract.hasSafeDimensions(movie.width(), movie.height())
        ) {
            return rejected(DecodeMismatchReason)
        }
        val bitmap =
            runCatching {
                Bitmap.createBitmap(
                    GloshiaImageContract.TargetSize,
                    GloshiaImageContract.TargetSize,
                    Bitmap.Config.ARGB_8888,
                )
            }.getOrNull() ?: return rejected(DecodeFailedReason)
        val scale =
            minOf(
                GloshiaImageContract.TargetSize.toFloat() / movie.width().toFloat(),
                GloshiaImageContract.TargetSize.toFloat() / movie.height().toFloat(),
            )
        val contentWidth = (movie.width() * scale).toInt().coerceIn(1, GloshiaImageContract.TargetSize)
        val contentHeight = (movie.height() * scale).toInt().coerceIn(1, GloshiaImageContract.TargetSize)
        val offsetX = (GloshiaImageContract.TargetSize - contentWidth) / 2
        val offsetY = (GloshiaImageContract.TargetSize - contentHeight) / 2
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        return try {
            timeline.frames.forEach { frame ->
                bitmap.eraseColor(PaddingColor)
                movie.setTime(frame.sampleTimeMillis)
                val checkpoint = canvas.save()
                canvas.translate(offsetX.toFloat(), offsetY.toFloat())
                canvas.scale(
                    contentWidth.toFloat() / movie.width().toFloat(),
                    contentHeight.toFloat() / movie.height().toFloat(),
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
                if (!shouldContinue) return ChromeGifFrameDecodeResult.Stopped
            }
            ChromeGifFrameDecodeResult.Completed
        } catch (_: Exception) {
            rejected(DecodeFailedReason)
        } finally {
            bitmap.eraseColor(Color.TRANSPARENT)
            bitmap.recycle()
        }
    }

    private fun Bitmap.toPreparedImage(): GloshiaPreparedImage =
        AndroidGloshiaImagePreprocessor.prepareVideoCapturedRaster(
            bitmap = this,
            maxLongEdge = GloshiaImageContract.TargetSize,
        ) ?: error(DecodeFailedReason)

    private fun rejected(reason: String) = ChromeGifFrameDecodeResult.Rejected(reason)

    const val DecodeFailedReason = "gif_decode_failed"
    const val DecodeMismatchReason = "gif_decode_mismatch"
    private const val PaddingColor = -8_421_505
}
