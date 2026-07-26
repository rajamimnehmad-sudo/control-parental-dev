package com.contentfilter.user.dag2

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DagV2CalibrationNormalizeResult {
    data class Success(
        val image: DagV2CalibrationNormalizedImage,
    ) : DagV2CalibrationNormalizeResult

    data class Rejected(
        val reason: String,
    ) : DagV2CalibrationNormalizeResult
}

@Singleton
class DagV2CalibrationImageNormalizer
    @Inject
    constructor() {
        suspend fun normalize(sourceBytes: ByteArray): DagV2CalibrationNormalizeResult =
            withContext(Dispatchers.Default) {
                var bitmap: Bitmap? = null
                try {
                    val source = ImageDecoder.createSource(ByteBuffer.wrap(sourceBytes))
                    bitmap =
                        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                            val width = info.size.width
                            val height = info.size.height
                            if (
                                width <= 0 ||
                                height <= 0 ||
                                width > MaxSourceSide ||
                                height > MaxSourceSide ||
                                width.toLong() * height.toLong() > MaxSourcePixels
                            ) {
                                throw IllegalArgumentException("dimensions_rejected")
                            }
                            val scale = minOf(1.0, MaxOutputSide.toDouble() / maxOf(width, height))
                            decoder.setTargetSize(
                                (width * scale).toInt().coerceAtLeast(1),
                                (height * scale).toInt().coerceAtLeast(1),
                            )
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                            decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_LOW_RAM
                            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                            decoder.isMutableRequired = false
                        }
                    val rgb =
                        if (bitmap.config == Bitmap.Config.ARGB_8888 && !bitmap.hasAlpha()) {
                            bitmap
                        } else {
                            Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also {
                                    converted ->
                                val canvas = android.graphics.Canvas(converted)
                                canvas.drawColor(android.graphics.Color.WHITE)
                                canvas.drawBitmap(bitmap, 0f, 0f, null)
                            }
                        }
                    if (rgb !== bitmap) {
                        bitmap.recycle()
                        bitmap = rgb
                    }
                    val encoded =
                        encodeBoundedJpeg(rgb)
                            ?: return@withContext DagV2CalibrationNormalizeResult.Rejected("normalized_sample_too_large")
                    DagV2CalibrationNormalizeResult.Success(
                        DagV2CalibrationNormalizedImage(
                            jpegBytes = encoded,
                            width = rgb.width,
                            height = rgb.height,
                        ),
                    )
                } catch (_: Exception) {
                    DagV2CalibrationNormalizeResult.Rejected("decode_failed")
                } finally {
                    bitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
                }
            }

        private fun encodeBoundedJpeg(bitmap: Bitmap): ByteArray? {
            for (quality in JpegQualities) {
                val output = ByteArrayOutputStream()
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) continue
                val bytes = output.toByteArray()
                if (bytes.size <= MaxOutputBytes) return bytes
                bytes.fill(0)
            }
            return null
        }

        private companion object {
            const val MaxSourceSide = 12_000
            const val MaxSourcePixels = 40_000_000L
            const val MaxOutputSide = 768
            const val MaxOutputBytes = 512 * 1024
            val JpegQualities = intArrayOf(92, 88, 84, 80, 76, 72, 68)
        }
    }
