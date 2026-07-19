package com.contentfilter.user.dag

import android.content.Context
import android.util.Base64
import com.contentfilter.core.network.remote.RemoteResult
import com.contentfilter.core.network.remote.SupabaseRestClient
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.security.MessageDigest
import javax.inject.Inject

class DagCalibrationRepository
    @Inject
    constructor(
        private val client: SupabaseRestClient,
        @ApplicationContext context: Context,
    ) {
        private val calibrationStore = DagImageCalibrationStore(context)

        internal suspend fun refresh(deviceId: String): RemoteResult<Unit> {
            val payload =
                JSONObject()
                    .put("action", "current")
                    .put("device_id", deviceId)
                    .put("model_version", DagProfessionalImageClassifier.ModelVersion)
            return when (val result = client.invokeFunctionForObject("dag-calibration", payload)) {
                is RemoteResult.Failure -> result
                is RemoteResult.Success -> {
                    val calibration = result.value.optJSONObject("calibration")
                    val thresholds = calibration?.optJSONObject("thresholds")
                    if (calibration != null && thresholds != null) {
                        val decisions = result.value.optJSONObject("exact_decisions")
                        calibrationStore.save(
                            calibration.optLong("version_number", 0),
                            thresholds,
                            decisions?.stringSet("allow").orEmpty(),
                            decisions?.stringSet("block").orEmpty(),
                        )
                    } else {
                        calibrationStore.clear()
                    }
                    RemoteResult.Success(Unit)
                }
            }
        }

        internal fun currentVersion(): Long = calibrationStore.current().version

        internal fun exactDecision(imageHash: String): DagImageDecision? = calibrationStore.exactDecision(imageHash)

        internal suspend fun submitReview(
            deviceId: String,
            thumbnail: ByteArray,
            classification: DagImageClassification,
        ): RemoteResult<Unit> {
            if (classification.decision != DagImageDecision.Uncertain || classification.scores.isEmpty()) {
                return RemoteResult.Success(Unit)
            }
            val hash = thumbnail.dagCalibrationHash()
            val scoreJson = JSONObject()
            classification.scores.forEach { (name, score) -> scoreJson.put(name, score.toDouble()) }
            val payload =
                JSONObject()
                    .put("action", "submit")
                    .put("device_id", deviceId)
                    .put("image_base64", Base64.encodeToString(thumbnail, Base64.NO_WRAP))
                    .put("image_hash", hash)
                    .put("model_version", DagProfessionalImageClassifier.ModelVersion)
                    .put("initial_decision", "uncertain")
                    .put("scores", scoreJson)
                    .put("signals", classification.signals.toJsonArray())
                    .put("calibration_version", classification.calibrationVersion)
            return client.invokeFunction("dag-calibration", payload)
        }

        internal suspend fun submitManualBlock(
            deviceId: String,
            thumbnail: ByteArray,
            classification: DagImageClassification,
        ): RemoteResult<Unit> {
            if (classification.scores.isEmpty()) return RemoteResult.Success(Unit)
            val hash = thumbnail.dagCalibrationHash()
            val scoreJson = JSONObject()
            classification.scores.forEach { (name, score) -> scoreJson.put(name, score.toDouble()) }
            val payload =
                JSONObject()
                    .put("action", "submit_manual_block")
                    .put("device_id", deviceId)
                    .put("image_base64", Base64.encodeToString(thumbnail, Base64.NO_WRAP))
                    .put("image_hash", hash)
                    .put("model_version", DagProfessionalImageClassifier.ModelVersion)
                    .put("initial_decision", classification.decision.name.lowercase())
                    .put("scores", scoreJson)
                    .put("signals", classification.signals.toJsonArray())
                    .put("calibration_version", classification.calibrationVersion)
            return client.invokeFunction("dag-calibration", payload)
        }

        internal suspend fun submitManualBlurReview(
            deviceId: String,
            thumbnail: ByteArray,
            classification: DagImageClassification,
        ): RemoteResult<Unit> {
            if (classification.scores.isEmpty()) return RemoteResult.Success(Unit)
            val hash = thumbnail.dagCalibrationHash()
            val scoreJson = JSONObject()
            classification.scores.forEach { (name, score) -> scoreJson.put(name, score.toDouble()) }
            val payload =
                JSONObject()
                    .put("action", "submit_manual_blur_review")
                    .put("device_id", deviceId)
                    .put("image_base64", Base64.encodeToString(thumbnail, Base64.NO_WRAP))
                    .put("image_hash", hash)
                    .put("model_version", DagProfessionalImageClassifier.ModelVersion)
                    .put("initial_decision", classification.decision.name.lowercase())
                    .put("scores", scoreJson)
                    .put("signals", classification.signals.toJsonArray())
                    .put("calibration_version", classification.calibrationVersion)
            return client.invokeFunction("dag-calibration", payload)
        }

        private fun JSONObject.stringSet(name: String): Set<String> {
            val values = optJSONArray(name) ?: return emptySet()
            return buildSet {
                for (index in 0 until values.length()) {
                    values.optString(index).takeIf { it.matches(HashPattern) }?.let(::add)
                }
            }
        }

        private fun Set<String>.toJsonArray() = org.json.JSONArray(sorted())

        private companion object {
            val HashPattern = Regex("^[0-9a-f]{64}$")
        }
    }

internal fun ByteArray.dagCalibrationHash(): String =
    MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }
