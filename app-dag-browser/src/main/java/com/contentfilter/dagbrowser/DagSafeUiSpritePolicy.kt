package com.contentfilter.dagbrowser

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.Base64

internal data class DagSafeUiSpriteMetrics(
    val width: Int,
    val height: Int,
    val transparentPixels: Int,
    val visiblePixels: Int,
    val quantizedColorCount: Int,
)

internal sealed interface DagSafeUiSpriteResult {
    data object NotSafe : DagSafeUiSpriteResult

    data class Sanitized(
        val pngBase64: String,
    ) : DagSafeUiSpriteResult
}

internal fun interface DagSafeUiSpriteInspector {
    fun inspect(
        bytes: ByteArray,
        bounds: DagImageBounds,
    ): DagSafeUiSpriteResult
}

internal object DagSafeUiSpritePolicy {
    fun isCandidate(
        bounds: DagImageBounds,
        byteCount: Int,
    ): Boolean {
        val longEdge = maxOf(bounds.width, bounds.height)
        val shortEdge = minOf(bounds.width, bounds.height)
        return bounds.mimeType == "image/png" &&
            longEdge in MinimumLongEdge..MaximumLongEdge &&
            shortEdge in 1..MaximumShortEdge &&
            longEdge >= shortEdge * MinimumAspectRatio &&
            bounds.width.toLong() * bounds.height.toLong() <= MaximumPixels &&
            byteCount in 1..MaximumInputBytes
    }

    fun isSafe(metrics: DagSafeUiSpriteMetrics): Boolean {
        val pixels = metrics.width.toLong() * metrics.height.toLong()
        if (pixels <= 0L || pixels > MaximumPixels) return false
        val pixelCount = pixels.toDouble()
        if (metrics.transparentPixels.toDouble() / pixelCount < MinimumTransparentFraction) {
            return false
        }
        if (metrics.visiblePixels.toDouble() / pixelCount < MinimumVisibleFraction) return false
        return metrics.quantizedColorCount in 1..MaximumQuantizedColors
    }

    const val MaximumOutputBytes = 512 * 1024
    private const val MinimumLongEdge = DagImageDecodeContract.MaxDimension + 1
    private const val MaximumLongEdge = 8_192
    private const val MaximumShortEdge = 128
    private const val MinimumAspectRatio = 16
    private const val MaximumPixels = 1_048_576L
    private const val MaximumInputBytes = 512 * 1024
    private const val MaximumQuantizedColors = 256
    private const val MinimumTransparentFraction = 0.30
    private const val MinimumVisibleFraction = 0.005
}

internal object AndroidDagSafeUiSpriteInspector : DagSafeUiSpriteInspector {
    override fun inspect(
        bytes: ByteArray,
        bounds: DagImageBounds,
    ): DagSafeUiSpriteResult {
        if (!DagSafeUiSpritePolicy.isCandidate(bounds, bytes.size)) {
            return DagSafeUiSpriteResult.NotSafe
        }
        var bitmap: Bitmap? = null
        return try {
            val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes).asReadOnlyBuffer())
            bitmap =
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    if (
                        info.isAnimated ||
                        info.size.width != bounds.width ||
                        info.size.height != bounds.height ||
                        info.mimeType != bounds.mimeType
                    ) {
                        throw IllegalArgumentException("sprite header changed")
                    }
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
                    decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                    decoder.setOnPartialImageListener { false }
                }
            val decoded = requireNotNull(bitmap)
            val row = IntArray(decoded.width)
            val colors = HashSet<Int>()
            var transparentPixels = 0
            var visiblePixels = 0
            for (y in 0 until decoded.height) {
                decoded.getPixels(row, 0, decoded.width, 0, y, decoded.width, 1)
                for (pixel in row) {
                    if (Color.alpha(pixel) <= TransparentAlphaMaximum) {
                        transparentPixels += 1
                    } else {
                        visiblePixels += 1
                        colors += quantizedColor(pixel)
                        if (colors.size > QuantizedColorEarlyExit) {
                            return DagSafeUiSpriteResult.NotSafe
                        }
                    }
                }
            }
            val metrics =
                DagSafeUiSpriteMetrics(
                    width = decoded.width,
                    height = decoded.height,
                    transparentPixels = transparentPixels,
                    visiblePixels = visiblePixels,
                    quantizedColorCount = colors.size,
                )
            if (!DagSafeUiSpritePolicy.isSafe(metrics)) return DagSafeUiSpriteResult.NotSafe
            val png =
                ByteArrayOutputStream().use { output ->
                    check(decoded.compress(Bitmap.CompressFormat.PNG, 100, output))
                    output.toByteArray()
                }
            if (png.isEmpty() || png.size > DagSafeUiSpritePolicy.MaximumOutputBytes) {
                return DagSafeUiSpriteResult.NotSafe
            }
            DagSafeUiSpriteResult.Sanitized(Base64.getEncoder().encodeToString(png))
        } catch (_: Exception) {
            DagSafeUiSpriteResult.NotSafe
        } finally {
            bitmap?.recycle()
        }
    }

    private fun quantizedColor(pixel: Int): Int =
        ((Color.red(pixel) shr ChannelShift) shl RedShift) or
            ((Color.green(pixel) shr ChannelShift) shl GreenShift) or
            (Color.blue(pixel) shr ChannelShift)

    private const val TransparentAlphaMaximum = 16
    private const val QuantizedColorEarlyExit = 256
    private const val ChannelShift = 4
    private const val RedShift = 8
    private const val GreenShift = 4
}
