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
            val payload = JSONObject().put("action", "current").put("device_id", deviceId)
            return when (val result = client.invokeFunctionForObject("dag-calibration", payload)) {
                is RemoteResult.Failure -> result
                is RemoteResult.Success -> {
                    val calibration = result.value.optJSONObject("calibration")
                    val thresholds = calibration?.optJSONObject("thresholds")
                    if (calibration != null && thresholds != null) {
                        calibrationStore.save(calibration.optLong("version_number", 0), thresholds)
                    }
                    RemoteResult.Success(Unit)
                }
            }
        }

        internal suspend fun submitReview(
            deviceId: String,
            thumbnail: ByteArray,
            classification: DagImageClassification,
        ): RemoteResult<Unit> {
            if (classification.decision != DagImageDecision.Uncertain || classification.scores.isEmpty()) {
                return RemoteResult.Success(Unit)
            }
            val hash = MessageDigest.getInstance("SHA-256").digest(thumbnail).joinToString("") { "%02x".format(it) }
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
            return client.invokeFunction("dag-calibration", payload)
        }

        internal suspend fun submitManualBlock(
            deviceId: String,
            thumbnail: ByteArray,
            classification: DagImageClassification,
        ): RemoteResult<Unit> {
            if (classification.scores.isEmpty()) return RemoteResult.Success(Unit)
            val hash = MessageDigest.getInstance("SHA-256").digest(thumbnail).joinToString("") { "%02x".format(it) }
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
            return client.invokeFunction("dag-calibration", payload)
        }
    }
