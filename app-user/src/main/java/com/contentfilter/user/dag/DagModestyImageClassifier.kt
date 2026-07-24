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
    val maleFace: Float = 0f,
    val maleBreastExposed: Float = 0f,
    val femaleBreastCovered: Float = 0f,
    val femaleGenitaliaCovered: Float = 0f,
    val buttocksCovered: Float = 0f,
    val armpitsExposed: Float = 0f,
    val bellyExposed: Float = 0f,
    val explicitRegion: Float = 0f,
    val sleevesAboveElbow: Float = 0f,
    val hemAboveKnee: Float = 0f,
)

internal fun DagModestyScores.withTzniutPose(scores: DagTzniutPoseScores): DagModestyScores =
    copy(
        sleevesAboveElbow = scores.sleevesAboveElbow,
        hemAboveKnee = scores.hemAboveKnee,
    )

internal fun DagModestyScores.toCalibrationScores(): Map<String, Float> =
    mapOf(
        "female_face" to femaleFace,
        "male_face" to maleFace,
        "male_breast_exposed" to maleBreastExposed,
        "female_breast_covered" to femaleBreastCovered,
        "female_genitalia_covered" to femaleGenitaliaCovered,
        "buttocks_covered" to buttocksCovered,
        "armpits_exposed" to armpitsExposed,
        "belly_exposed" to bellyExposed,
        "explicit_region" to explicitRegion,
        "sleeves_above_elbow" to sleevesAboveElbow,
        "hem_above_knee" to hemAboveKnee,
    )

internal fun requiresKosherModestyBlur(
    scores: DagModestyScores,
    calibration: DagImageCalibration = DagImageCalibration(),
    audienceContext: DagImageAudienceContext = DagImageAudienceContext.Unknown,
): Boolean {
    val femaleContext = scores.hasFemaleContext(calibration, audienceContext)
    val femaleSpecificRegion = !scores.hasMaleOnlyContext(calibration, audienceContext)
    val universallySensitive =
        audienceContext == DagImageAudienceContext.IntimateClothing ||
            scores.explicitRegion >= calibration.explicitRegion ||
            scores.femaleGenitaliaCovered >= calibration.femaleGenitaliaCovered ||
            scores.buttocksCovered >= calibration.buttocksCovered
    if (universallySensitive || audienceContext == DagImageAudienceContext.YoungChild) return universallySensitive
    return (femaleSpecificRegion && scores.femaleBreastCovered >= calibration.femaleBreastCovered) ||
        (femaleContext && scores.armpitsExposed >= calibration.armpitsExposed) ||
        (femaleContext && scores.bellyExposed >= calibration.bellyExposed) ||
        (femaleContext && scores.sleevesAboveElbow >= calibration.sleevesAboveElbow) ||
        (femaleContext && scores.hemAboveKnee >= calibration.hemAboveKnee)
}

internal fun dagModestyImageDecision(
    scores: DagModestyScores,
    calibration: DagImageCalibration = DagImageCalibration(),
    audienceContext: DagImageAudienceContext = DagImageAudienceContext.Unknown,
): DagImageDecision {
    if (!requiresKosherModestyBlur(scores, calibration, audienceContext)) return DagImageDecision.Allowed
    val femaleContext = scores.hasFemaleContext(calibration, audienceContext)
    val femaleSpecificRegion = !scores.hasMaleOnlyContext(calibration, audienceContext)
    val clearlyBlocked =
        scores.explicitRegion >= calibration.explicitRegion.strongThreshold() ||
            scores.femaleGenitaliaCovered >= calibration.femaleGenitaliaCovered.strongThreshold() ||
            scores.buttocksCovered >= calibration.buttocksCovered.strongThreshold() ||
            (
                femaleContext &&
                    (
                        scores.armpitsExposed >= calibration.armpitsExposed.strongThreshold() ||
                            scores.bellyExposed >= calibration.bellyExposed.strongThreshold() ||
                            scores.sleevesAboveElbow >= calibration.sleevesAboveElbow.strongThreshold() ||
                            scores.hemAboveKnee >= calibration.hemAboveKnee.strongThreshold()
                    )
            ) ||
            (femaleSpecificRegion && scores.femaleBreastCovered >= calibration.femaleBreastCovered.strongThreshold())
    return if (clearlyBlocked) DagImageDecision.Blocked else DagImageDecision.Uncertain
}

internal fun DagModestyScores.hasFemaleContext(
    calibration: DagImageCalibration,
    audienceContext: DagImageAudienceContext,
): Boolean {
    if (audienceContext == DagImageAudienceContext.YoungChild) return false
    if (audienceContext == DagImageAudienceContext.FemaleSixPlus) return true
    val femaleDetected = femaleFace >= calibration.femaleFace
    val maleDominates =
        (audienceContext == DagImageAudienceContext.Male || maleFace >= calibration.maleFace) &&
            maleFace >= femaleFace + GenderDominanceMargin
    return femaleDetected && !maleDominates
}

private fun DagModestyScores.hasMaleOnlyContext(
    calibration: DagImageCalibration,
    audienceContext: DagImageAudienceContext,
): Boolean {
    if (audienceContext == DagImageAudienceContext.YoungChild) return true
    if (audienceContext == DagImageAudienceContext.IntimateClothing) return false
    if (audienceContext == DagImageAudienceContext.FemaleSixPlus) return false
    val maleDetected = audienceContext == DagImageAudienceContext.Male || maleFace >= calibration.maleFace
    return maleDetected && (femaleFace < calibration.femaleFace || maleFace >= femaleFace + GenderDominanceMargin)
}

internal fun dagAudienceAwareImageDecision(
    ensembleDecision: DagImageDecision,
    modestyDecision: DagImageDecision,
    scores: DagModestyScores?,
    calibration: DagImageCalibration,
    audienceContext: DagImageAudienceContext,
): DagImageDecision {
    if (modestyDecision != DagImageDecision.Allowed) {
        return dagCombinedImageDecision(ensembleDecision, modestyDecision)
    }
    val measured = scores ?: return ensembleDecision
    val maleChestOnly =
        measured.hasMaleOnlyContext(calibration, audienceContext) &&
            measured.maleBreastExposed >= calibration.maleBreastExposed &&
            !measured.hasFemaleContext(calibration, audienceContext)
    val youngChild = audienceContext == DagImageAudienceContext.YoungChild
    return if (maleChestOnly || youngChild) DagImageDecision.Allowed else ensembleDecision
}

internal fun dagCombinedImageDecision(
    ensembleDecision: DagImageDecision,
    modestyDecision: DagImageDecision,
): DagImageDecision =
    when {
        ensembleDecision == DagImageDecision.Blocked || modestyDecision == DagImageDecision.Blocked -> {
            DagImageDecision.Blocked
        }
        ensembleDecision == DagImageDecision.Uncertain || modestyDecision == DagImageDecision.Uncertain -> {
            DagImageDecision.Uncertain
        }
        else -> DagImageDecision.Allowed
    }

private fun Float.strongThreshold(): Float = (this + StrongDecisionMargin).coerceAtMost(1f)

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

    internal fun prepare() {
        if (isAvailable()) model()
    }

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
        fun maximum(
            classIndex: Int,
            missingValue: Float = 1f,
        ): Float = (getOrNull(BoxChannels + classIndex) as? FloatArray)?.maxOrNull() ?: missingValue

        return DagModestyScores(
            femaleGenitaliaCovered = maximum(FemaleGenitaliaCovered),
            femaleFace = maximum(FemaleFace),
            maleFace = maximum(MaleFace, 0f),
            maleBreastExposed = maximum(MaleBreastExposed, 0f),
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
        const val MaleBreastExposed = 5
        const val ArmpitsExposed = 11
        const val MaleFace = 12
        const val BellyExposed = 13
        const val FemaleBreastCovered = 16
        const val ButtocksCovered = 17
        val ExplicitClasses = listOf(2, 3, 4, 6, 14)
        val FailedInferenceScores = DagModestyScores(explicitRegion = 1f)
    }
}

private const val StrongDecisionMargin = 0.15f
private const val GenderDominanceMargin = 0.10f
