package com.contentfilter.user.dag

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max

internal enum class DagImageDecision {
    Allowed,
    Blocked,
    Uncertain,
}

internal data class DagImageClassification(
    val decision: DagImageDecision,
    val unsafeScore: Float? = null,
    val mimeType: String? = null,
)

internal fun dagImageDecision(unsafeScore: Float): DagImageDecision =
    when {
        !unsafeScore.isFinite() || unsafeScore < 0f || unsafeScore > 1f -> DagImageDecision.Uncertain
        unsafeScore <= DagImageClassifier.SafeThreshold -> DagImageDecision.Allowed
        unsafeScore >= DagImageClassifier.BlockThreshold -> DagImageDecision.Blocked
        else -> DagImageDecision.Uncertain
    }

internal class DagImageClassifier(
    context: Context,
) : Closeable {
    private val applicationContext = context.applicationContext
    private val lock = Any()
    private var interpreter: Interpreter? = null

    fun classify(
        bytes: ByteArray,
        mimeType: String?,
    ): DagImageClassification =
        synchronized(lock) {
            val detectedMime = supportedStaticImageMime(bytes, mimeType)
            if (detectedMime == null) {
                return@synchronized DagImageClassification(DagImageDecision.Uncertain)
            }
            val bitmap = decodeForClassification(bytes) ?: return@synchronized DagImageClassification(DagImageDecision.Uncertain)
            try {
                val output = Array(1) { FloatArray(OutputClasses) }
                model().run(bitmapToInput(bitmap), output)
                val unsafeScore = output[0][UnsafeOutputIndex]
                DagImageClassification(dagImageDecision(unsafeScore), unsafeScore, detectedMime)
            } catch (_: RuntimeException) {
                DagImageClassification(DagImageDecision.Uncertain)
            } finally {
                bitmap.recycle()
            }
        }

    override fun close() {
        synchronized(lock) {
            interpreter?.close()
            interpreter = null
        }
    }

    private fun model(): Interpreter =
        interpreter ?: loadModel().also { loaded ->
            check(loaded.getInputTensor(0).shape().contentEquals(intArrayOf(1, InputSize, InputSize, 3)))
            check(loaded.getOutputTensor(0).shape().contentEquals(intArrayOf(1, OutputClasses)))
            interpreter = loaded
        }

    private fun loadModel(): Interpreter {
        val descriptor = applicationContext.assets.openFd(ModelAsset)
        descriptor.use {
            FileInputStream(it.fileDescriptor).channel.use { channel ->
                val mapped = channel.map(FileChannel.MapMode.READ_ONLY, it.startOffset, it.declaredLength)
                return Interpreter(mapped, Interpreter.Options().setNumThreads(ModelThreads))
            }
        }
    }

    private fun decodeForClassification(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in MinimumDimension..MaximumSourceDimension) return null
        if (bounds.outHeight !in MinimumDimension..MaximumSourceDimension) return null

        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / sampleSize > MaximumDecodeDimension) sampleSize *= 2
        val decoded =
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            ) ?: return null
        return Bitmap.createScaledBitmap(decoded, ResizeSize, ResizeSize, true).also {
            if (it !== decoded) decoded.recycle()
        }
    }

    private fun bitmapToInput(bitmap: Bitmap): ByteBuffer {
        val input =
            ByteBuffer.allocateDirect(
                InputSize * InputSize * 3 * Float.SIZE_BYTES,
            ).order(ByteOrder.nativeOrder())
        val pixels = IntArray(InputSize * InputSize)
        val offset = (ResizeSize - InputSize) / 2
        bitmap.getPixels(pixels, 0, InputSize, offset, offset, InputSize, InputSize)
        pixels.forEach { pixel ->
            input.putFloat(Color.blue(pixel) - VggMeanBlue)
            input.putFloat(Color.green(pixel) - VggMeanGreen)
            input.putFloat(Color.red(pixel) - VggMeanRed)
        }
        input.rewind()
        return input
    }

    private fun supportedStaticImageMime(
        bytes: ByteArray,
        mimeType: String?,
    ): String? {
        if (bytes.size !in MinimumBytes..MaximumImageBytes) return null
        val mime = mimeType.orEmpty().substringBefore(';').trim().lowercase()
        if (mime == "image/svg+xml" || mime == "image/gif") return null
        if (
            bytes.startsWithAscii("GIF8") ||
            bytes.containsAscii("acTL") ||
            bytes.containsAscii("ANIM") ||
            bytes.containsAscii("ANMF")
        ) {
            return null
        }
        return when {
            bytes.isJpeg() -> "image/jpeg"
            bytes.isPng() -> "image/png"
            bytes.isWebP() -> "image/webp"
            else -> null
        }
    }

    private fun ByteArray.isJpeg(): Boolean =
        size >= 3 && this[0] == 0xff.toByte() && this[1] == 0xd8.toByte() && this[2] == 0xff.toByte()

    private fun ByteArray.isPng(): Boolean =
        size >= 8 &&
            this[0] == 0x89.toByte() &&
            this[1] == 0x50.toByte() &&
            this[2] == 0x4e.toByte() &&
            this[3] == 0x47.toByte()

    private fun ByteArray.isWebP(): Boolean =
        size >= 12 && startsWithAscii("RIFF") && copyOfRange(8, 12).startsWithAscii("WEBP")

    private fun ByteArray.startsWithAscii(value: String): Boolean =
        size >= value.length && value.indices.all { index -> this[index].toInt() and 0xff == value[index].code }

    private fun ByteArray.containsAscii(value: String): Boolean {
        if (size < value.length) return false
        return (0..size - value.length).any { offset ->
            value.indices.all { index -> this[offset + index].toInt() and 0xff == value[index].code }
        }
    }

    companion object {
        internal const val SafeThreshold = 0.08f
        internal const val BlockThreshold = 0.20f
        internal const val MaximumImageBytes = 4 * 1024 * 1024

        private const val ModelAsset = "dag/nsfw_open_nsfw_quantized.tflite"
        private const val InputSize = 224
        private const val ResizeSize = 256
        private const val OutputClasses = 2
        private const val UnsafeOutputIndex = 1
        private const val ModelThreads = 2
        private const val MinimumBytes = 32
        private const val MinimumDimension = 24
        private const val MaximumSourceDimension = 16_384
        private const val MaximumDecodeDimension = 1_024
        private const val VggMeanBlue = 103.939f
        private const val VggMeanGreen = 116.779f
        private const val VggMeanRed = 123.68f
    }
}
