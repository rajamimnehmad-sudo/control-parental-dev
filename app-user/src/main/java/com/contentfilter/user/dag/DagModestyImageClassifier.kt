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

internal fun DagModestyScores.toCalibrationScores(): Map<String, Float> =
    mapOf(
        "female_face" to femaleFace,
        "female_breast_covered" to femaleBreastCovered,
        "female_genitalia_covered" to femaleGenitaliaCovered,
        "buttocks_covered" to buttocksCovered,
        "armpits_exposed" to armpitsExposed,
        "belly_exposed" to bellyExposed,
        "explicit_region" to explicitRegion,
    )

internal fun requiresKosherModestyBlur(
    scores: DagModestyScores,
    calibration: DagImageCalibration = DagImageCalibration(),
): Boolean {
    val femaleContext = scores.femaleFace >= calibration.femaleFace
    return scores.explicitRegion >= calibration.explicitRegion ||
        scores.femaleBreastCovered >= calibration.femaleBreastCovered ||
        scores.femaleGenitaliaCovered >= calibration.femaleGenitaliaCovered ||
        scores.buttocksCovered >= calibration.buttocksCovered ||
        (femaleContext && scores.armpitsExposed >= calibration.armpitsExposed) ||
        (femaleContext && scores.bellyExposed >= calibration.bellyExposed)
}

internal class DagModestyImageClassifier(
    context: Context,
) : Closeable {
    private val applicationContext = context.applicationContext
    private val environment by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { OrtEnvironment.getEnvironment() }
    private var session: OrtSession? = null

    fun classify(bitmap: Bitmap): DagModestyScores? {
        if (!isAvailable()) return null
        return runCatching {
            OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(bitmap.toModelInput()),
                longArrayOf(1, Channels.toLong(), InputSize.toLong(), InputSize.toLong()),
            ).use { tensor ->
                model().run(mapOf(InputName to tensor)).use { output ->
                    val batch = output[0].value as? Array<*> ?: return@use FailedInferenceScores
                    val channels = batch.firstOrNull() as? Array<*> ?: return@use FailedInferenceScores
                    channels.toModestyScores()
                }
            }
        }.getOrDefault(FailedInferenceScores)
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
        val FailedInferenceScores = DagModestyScores(explicitRegion = 1f)
    }
}
