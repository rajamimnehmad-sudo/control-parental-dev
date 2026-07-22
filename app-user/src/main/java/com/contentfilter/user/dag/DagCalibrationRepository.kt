package com.contentfilter.user.dag

import android.content.Context
import android.util.Base64
import com.contentfilter.core.network.remote.RemoteResult
import com.contentfilter.core.network.remote.SupabaseRestClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.security.MessageDigest
import javax.inject.Inject

class DagCalibrationRepository
    @Inject
    constructor(
        private val client: SupabaseRestClient,
        @ApplicationContext context: Context,
        private val outboxStore: DagCalibrationOutboxStore,
    ) {
        private val calibrationStore = DagImageCalibrationStore(context)
        private val retryScheduler = DagCalibrationRetryScheduler(context)
        private val deliveryMutex = Mutex()

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
        ): DagCalibrationDeliveryResult {
            if (classification.decision != DagImageDecision.Uncertain || classification.scores.isEmpty()) {
                return DagCalibrationDeliveryResult.Accepted
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
            return enqueueAndDeliver(payload)
        }

        internal suspend fun submitManualBlock(
            deviceId: String,
            thumbnail: ByteArray,
            classification: DagImageClassification,
        ): DagCalibrationDeliveryResult {
            if (classification.scores.isEmpty()) return DagCalibrationDeliveryResult.Rejected("missing_scores")
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
            return enqueueAndDeliver(payload)
        }

        internal suspend fun submitManualBlurReview(
            deviceId: String,
            thumbnail: ByteArray,
            classification: DagImageClassification,
        ): DagCalibrationDeliveryResult {
            if (classification.scores.isEmpty()) return DagCalibrationDeliveryResult.Rejected("missing_scores")
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
            return enqueueAndDeliver(payload)
        }

        internal suspend fun flushPending(deviceId: String): Boolean {
            outboxStore.pending()
                .filter { it.payload.optString("device_id") == deviceId }
                .forEach { deliver(it) }
            return outboxStore.pending().none { it.payload.optString("device_id") == deviceId }
        }

        private suspend fun enqueueAndDeliver(payload: JSONObject): DagCalibrationDeliveryResult {
            val id = outboxStore.enqueue(payload)
            retryScheduler.requestRetry()
            val item =
                outboxStore.pending().firstOrNull { it.id == id }
                    ?: return DagCalibrationDeliveryResult.Queued
            return deliver(item)
        }

        private suspend fun deliver(item: DagCalibrationOutboxItem): DagCalibrationDeliveryResult =
            deliveryMutex.withLock {
                if (outboxStore.pending().none { it.id == item.id }) {
                    DagCalibrationDeliveryResult.Accepted
                } else {
                    deliverUnlocked(item)
                }
            }

        private suspend fun deliverUnlocked(item: DagCalibrationOutboxItem): DagCalibrationDeliveryResult =
            when (val result = client.invokeFunctionForObject("dag-calibration", item.payload)) {
                is RemoteResult.Failure -> {
                    if (result.retryable) {
                        retryScheduler.requestRetry()
                        DagCalibrationDeliveryResult.Queued
                    } else {
                        outboxStore.remove(item.id)
                        DagCalibrationDeliveryResult.Rejected("request_rejected")
                    }
                }
                is RemoteResult.Success -> {
                    outboxStore.remove(item.id)
                    dagCalibrationAcknowledgement(
                        accepted = result.value.optBoolean("accepted", false),
                        reason = result.value.optString("reason", "not_accepted"),
                    )
                }
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

internal sealed interface DagCalibrationDeliveryResult {
    data object Accepted : DagCalibrationDeliveryResult

    data class Rejected(val reason: String) : DagCalibrationDeliveryResult

    data object Queued : DagCalibrationDeliveryResult
}

internal fun dagCalibrationAcknowledgement(
    accepted: Boolean,
    reason: String,
): DagCalibrationDeliveryResult =
    if (accepted) {
        DagCalibrationDeliveryResult.Accepted
    } else {
        DagCalibrationDeliveryResult.Rejected(reason.ifBlank { "not_accepted" })
    }

internal fun ByteArray.dagCalibrationHash(): String =
    MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }
