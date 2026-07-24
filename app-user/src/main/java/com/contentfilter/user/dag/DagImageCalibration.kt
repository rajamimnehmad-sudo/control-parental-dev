package com.contentfilter.user.dag

import android.content.Context
import org.json.JSONObject

internal data class DagImageCalibration(
    val version: Long = 0,
    val professionalSafe: Float = 0.15f,
    val professionalBlock: Float = 0.65f,
    val femaleFace: Float = 0.30f,
    val maleFace: Float = 0.30f,
    val maleBreastExposed: Float = 0.55f,
    val femaleBreastCovered: Float = 0.18f,
    val femaleGenitaliaCovered: Float = 0.18f,
    val buttocksCovered: Float = 0.18f,
    val armpitsExposed: Float = 0.20f,
    val bellyExposed: Float = 0.20f,
    val explicitRegion: Float = 0.20f,
    val sleevesAboveElbow: Float = 0.72f,
    val hemAboveKnee: Float = 0.72f,
)

internal fun DagImageCalibration.withSafeBounds(): DagImageCalibration {
    val boundedProfessionalBlock = professionalBlock.coerceIn(0.35f, 0.90f)
    return copy(
        professionalSafe = professionalSafe.coerceIn(0.05f, 0.75f).coerceAtMost(boundedProfessionalBlock - 0.05f),
        professionalBlock = boundedProfessionalBlock,
        femaleFace = femaleFace.coerceIn(0.12f, 0.65f),
        maleFace = maleFace.coerceIn(0.12f, 0.65f),
        maleBreastExposed = maleBreastExposed.coerceIn(0.25f, 0.90f),
        femaleBreastCovered = femaleBreastCovered.coerceIn(0.08f, 0.65f),
        femaleGenitaliaCovered = femaleGenitaliaCovered.coerceIn(0.08f, 0.65f),
        buttocksCovered = buttocksCovered.coerceIn(0.08f, 0.65f),
        armpitsExposed = armpitsExposed.coerceIn(0.08f, 0.65f),
        bellyExposed = bellyExposed.coerceIn(0.08f, 0.65f),
        explicitRegion = explicitRegion.coerceIn(0.08f, 0.65f),
        sleevesAboveElbow = sleevesAboveElbow.coerceIn(0.45f, 0.95f),
        hemAboveKnee = hemAboveKnee.coerceIn(0.45f, 0.95f),
    )
}

internal class DagImageCalibrationStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun current(): DagImageCalibration {
        val calibration =
            preferences.getString(CalibrationKey, null)
                ?.let { runCatching { JSONObject(it) }.getOrNull() }
                ?.let { json ->
                    fun score(
                        name: String,
                        fallback: Float,
                    ) = json.optDouble(name, fallback.toDouble()).toFloat().coerceIn(0f, 1f)

                    DagImageCalibration(
                        version = json.optLong("version", 0),
                        professionalSafe = score("professional_safe", 0.15f),
                        professionalBlock = score("professional_block", 0.65f),
                        femaleFace = score("female_face", 0.30f),
                        maleFace = score("male_face", 0.30f),
                        maleBreastExposed = score("male_breast_exposed", 0.55f),
                        femaleBreastCovered = score("female_breast_covered", 0.18f),
                        femaleGenitaliaCovered = score("female_genitalia_covered", 0.18f),
                        buttocksCovered = score("buttocks_covered", 0.18f),
                        armpitsExposed = score("armpits_exposed", 0.20f),
                        bellyExposed = score("belly_exposed", 0.20f),
                        explicitRegion = score("explicit_region", 0.20f),
                        sleevesAboveElbow = score("sleeves_above_elbow", 0.72f),
                        hemAboveKnee = score("hem_above_knee", 0.72f),
                    ).withSafeBounds()
                }
        return calibration ?: DagImageCalibration()
    }

    fun save(
        version: Long,
        thresholds: JSONObject,
    ) {
        val copy =
            JSONObject(thresholds.toString())
                .put("version", version)
        preferences.edit().putString(CalibrationKey, copy.toString()).apply()
    }

    fun clear() {
        preferences.edit().remove(CalibrationKey).apply()
    }

    private companion object {
        const val PreferencesName = "dag_image_calibration"
        const val CalibrationKey = "active"
    }
}
