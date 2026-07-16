package com.contentfilter.user.dag

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import java.io.Closeable
import java.nio.FloatBuffer
import kotlin.math.exp

internal class DagProfessionalImageClassifier(
    private val context: Context,
) : Closeable {
    private val environment by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { OrtEnvironment.getEnvironment() }
    private var session: OrtSession? = null

    fun classify(bitmap: Bitmap): DagImageClassification {
        if (!supportsOnnxRuntime()) return DagImageClassification(DagImageDecision.Allowed)
        return runCatching {
            val input = bitmap.toModelInput()
            OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(input),
                longArrayOf(1, Channels.toLong(), InputSize.toLong(), InputSize.toLong()),
            ).use { tensor ->
                model().run(mapOf(InputName to tensor)).use { output ->
                    val logits =
                        (output[0].value as? Array<*>)?.firstOrNull() as? FloatArray
                            ?: return@use DagImageClassification(DagImageDecision.Uncertain)
                    if (logits.size != OutputClasses) return@use DagImageClassification(DagImageDecision.Uncertain)
                    val unsafeProbability = binarySoftmaxFirst(logits[0], logits[1])
                    DagImageClassification(
                        decision = dagProfessionalImageDecision(unsafeProbability),
                        unsafeScore = unsafeProbability,
                    )
                }
            }
        }.getOrElse { DagImageClassification(DagImageDecision.Uncertain) }
    }

    override fun close() {
        session?.close()
        session = null
    }

    private fun model(): OrtSession =
        session ?: context.assets.open(ModelAsset).use { input ->
            OrtSession.SessionOptions().use { options ->
                environment.createSession(input.readBytes(), options).also { session = it }
            }
        }

    private fun Bitmap.toModelInput(): FloatArray {
        val side = minOf(width, height)
        val cropped = Bitmap.createBitmap(this, (width - side) / 2, (height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(cropped, InputSize, InputSize, true)
        if (cropped !== this && cropped !== scaled) cropped.recycle()
        val pixels = IntArray(InputSize * InputSize)
        scaled.getPixels(pixels, 0, InputSize, 0, 0, InputSize, InputSize)
        if (scaled !== this) scaled.recycle()

        val plane = InputSize * InputSize
        return FloatArray(plane * Channels).also { output ->
            pixels.forEachIndexed { index, pixel ->
                output[index] = (((pixel shr 16) and 0xff) / 127.5f) - 1f
                output[plane + index] = (((pixel shr 8) and 0xff) / 127.5f) - 1f
                output[plane * 2 + index] = ((pixel and 0xff) / 127.5f) - 1f
            }
        }
    }

    private fun supportsOnnxRuntime(): Boolean = android.os.Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }

    companion object {
        internal const val ModelVersion = "marqo-nsfw-vit-tiny-384-1"
        internal const val SafeThreshold = 0.08f
        internal const val BlockThreshold = 0.20f
        private const val ModelAsset = "dag/nsfw_marqo_vit_tiny_384.onnx"
        private const val InputName = "pixel_values"
        private const val InputSize = 384
        private const val Channels = 3
        private const val OutputClasses = 2
    }
}

internal fun binarySoftmaxFirst(
    first: Float,
    second: Float,
): Float {
    if (!first.isFinite() || !second.isFinite()) return Float.NaN
    val maximum = maxOf(first, second)
    val firstExponent = exp((first - maximum).toDouble())
    val secondExponent = exp((second - maximum).toDouble())
    return (firstExponent / (firstExponent + secondExponent)).toFloat()
}
