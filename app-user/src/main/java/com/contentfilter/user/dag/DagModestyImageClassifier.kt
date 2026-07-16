package com.contentfilter.user.dag

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import java.io.Closeable
import java.nio.FloatBuffer

internal data class DagModestyScores(
    val femaleFace: Float = 0f,
    val femaleBreastCovered: Float = 0f,
    val femaleGenitaliaCovered: Float = 0f,
    val buttocksCovered: Float = 0f,
    val armpitsExposed: Float = 0f,
    val bellyExposed: Float = 0f,
    val explicitRegion: Float = 0f,
)

internal fun requiresKosherModestyBlur(scores: DagModestyScores): Boolean {
    val femaleContext = scores.femaleFace >= FemaleFaceThreshold
    return scores.explicitRegion >= ExplicitThreshold ||
        scores.femaleBreastCovered >= CoveredRegionThreshold ||
        scores.femaleGenitaliaCovered >= CoveredRegionThreshold ||
        scores.buttocksCovered >= CoveredRegionThreshold ||
        (femaleContext && scores.armpitsExposed >= ExposedRegionThreshold) ||
        (femaleContext && scores.bellyExposed >= ExposedRegionThreshold)
}

internal class DagModestyImageClassifier(
    context: Context,
) : Closeable {
    private val applicationContext = context.applicationContext
    private val environment by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { OrtEnvironment.getEnvironment() }
    private var session: OrtSession? = null

    fun requiresBlur(bitmap: Bitmap): Boolean {
        if (!isAvailable()) return false
        return runCatching {
            OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(bitmap.toModelInput()),
                longArrayOf(1, Channels.toLong(), InputSize.toLong(), InputSize.toLong()),
            ).use { tensor ->
                model().run(mapOf(InputName to tensor)).use { output ->
                    val batch = output[0].value as? Array<*> ?: return@use true
                    val channels = batch.firstOrNull() as? Array<*> ?: return@use true
                    requiresKosherModestyBlur(channels.toModestyScores())
                }
            }
        }.getOrDefault(true)
    }

    override fun close() {
        session?.close()
        session = null
    }

    internal fun isAvailable(): Boolean = android.os.Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }

    private fun model(): OrtSession =
        session ?: applicationContext.assets.open(ModelAsset).use { input ->
            OrtSession.SessionOptions().use { options ->
                environment.createSession(input.readBytes(), options).also { session = it }
            }
        }

    private fun Bitmap.toModelInput(): FloatArray {
        val side = maxOf(width, height)
        val squared = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        Canvas(squared).apply {
            drawColor(Color.BLACK)
            drawBitmap(this@toModelInput, 0f, 0f, null)
        }
        val scaled = Bitmap.createScaledBitmap(squared, InputSize, InputSize, true)
        if (squared !== scaled) squared.recycle()
        val pixels = IntArray(InputSize * InputSize)
        scaled.getPixels(pixels, 0, InputSize, 0, 0, InputSize, InputSize)
        scaled.recycle()

        val plane = InputSize * InputSize
        return FloatArray(plane * Channels).also { output ->
            pixels.forEachIndexed { index, pixel ->
                output[index] = ((pixel shr 16) and 0xff) / 255f
                output[plane + index] = ((pixel shr 8) and 0xff) / 255f
                output[plane * 2 + index] = (pixel and 0xff) / 255f
            }
        }
    }

    private fun Array<*>.toModestyScores(): DagModestyScores {
        fun maximum(classIndex: Int): Float = (getOrNull(BoxChannels + classIndex) as? FloatArray)?.maxOrNull() ?: 1f

        return DagModestyScores(
            femaleGenitaliaCovered = maximum(FemaleGenitaliaCovered),
            femaleFace = maximum(FemaleFace),
            armpitsExposed = maximum(ArmpitsExposed),
            bellyExposed = maximum(BellyExposed),
            femaleBreastCovered = maximum(FemaleBreastCovered),
            buttocksCovered = maximum(ButtocksCovered),
            explicitRegion = ExplicitClasses.maxOf(::maximum),
        )
    }

    private companion object {
        const val ModelAsset = "dag/nudenet_modesty_320n_uint8.onnx"
        const val InputName = "images"
        const val InputSize = 320
        const val Channels = 3
        const val BoxChannels = 4
        const val FemaleGenitaliaCovered = 0
        const val FemaleFace = 1
        const val ArmpitsExposed = 11
        const val BellyExposed = 13
        const val FemaleBreastCovered = 16
        const val ButtocksCovered = 17
        val ExplicitClasses = listOf(2, 3, 4, 6, 13, 14)
    }
}

private const val FemaleFaceThreshold = 0.30f
private const val CoveredRegionThreshold = 0.18f
private const val ExposedRegionThreshold = 0.20f
private const val ExplicitThreshold = 0.20f
