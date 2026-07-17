package com.contentfilter.user.dag

import android.content.Context
import org.json.JSONObject

internal data class DagImageCalibration(
    val version: Long = 0,
    val professionalSafe: Float = 0.15f,
    val professionalBlock: Float = 0.65f,
    val femaleFace: Float = 0.30f,
    val femaleBreastCovered: Float = 0.18f,
    val femaleGenitaliaCovered: Float = 0.18f,
    val buttocksCovered: Float = 0.18f,
    val armpitsExposed: Float = 0.20f,
    val bellyExposed: Float = 0.20f,
    val explicitRegion: Float = 0.20f,
)

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
                        femaleBreastCovered = score("female_breast_covered", 0.18f),
                        femaleGenitaliaCovered = score("female_genitalia_covered", 0.18f),
                        buttocksCovered = score("buttocks_covered", 0.18f),
                        armpitsExposed = score("armpits_exposed", 0.20f),
                        bellyExposed = score("belly_exposed", 0.20f),
                        explicitRegion = score("explicit_region", 0.20f),
                    ).takeIf { it.professionalSafe < it.professionalBlock }
                }
        return calibration ?: DagImageCalibration()
    }

    fun save(
        version: Long,
        thresholds: JSONObject,
    ) {
        val copy = JSONObject(thresholds.toString()).put("version", version)
        preferences.edit().putString(CalibrationKey, copy.toString()).apply()
    }

    private companion object {
        const val PreferencesName = "dag_image_calibration"
        const val CalibrationKey = "active"
    }
}
