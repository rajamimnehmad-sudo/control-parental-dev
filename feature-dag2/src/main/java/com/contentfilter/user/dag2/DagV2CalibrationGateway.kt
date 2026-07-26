package com.contentfilter.user.dag2

import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.network.config.AuthTokenProvider
import com.contentfilter.core.network.config.DeviceTokenProvider
import com.contentfilter.core.network.config.SupabaseConfigProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

interface DagV2CalibrationGateway {
    suspend fun deliver(submission: DagV2CalibrationSubmission): DagV2CalibrationDeliveryResult
}

@Singleton
class SupabaseDagV2CalibrationGateway
    @Inject
    constructor(
        private val configProvider: SupabaseConfigProvider,
        private val authTokenProvider: AuthTokenProvider,
        private val deviceTokenProvider: DeviceTokenProvider,
        private val activationRepository: DeviceActivationRepository,
        private val httpClient: OkHttpClient,
    ) : DagV2CalibrationGateway {
        override suspend fun deliver(submission: DagV2CalibrationSubmission): DagV2CalibrationDeliveryResult =
            withContext(Dispatchers.IO) {
                val config = configProvider.current()
                val baseUrl =
                    config.normalizedUrlOrNull()
                        ?: return@withContext temporary("config_unavailable")
                val activation =
                    activationRepository.currentActivation()
                        ?: return@withContext permanent("device_not_activated")
                val authToken = authTokenProvider.currentToken()
                val deviceToken =
                    deviceTokenProvider.currentDeviceToken()
                        ?: return@withContext permanent("device_token_missing")
                val multipart =
                    MultipartBody
                        .Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("device_id", activation.deviceId)
                        .addFormDataPart("content_sha256", submission.contentSha256)
                        .addFormDataPart("perceptual_hash", submission.perceptualHash)
                        .addFormDataPart("width", submission.width.toString())
                        .addFormDataPart("height", submission.height.toString())
                        .addFormDataPart("mime_type", NormalizedMime)
                        .addFormDataPart("size_bytes", (submission.jpegBytes?.size ?: 0).toString())
                        .addFormDataPart("source_kind", submission.sourceKind)
                        .addFormDataPart("source_host", submission.sourceHost)
                        .addFormDataPart("document_host", submission.documentHost)
                        .addFormDataPart("source_url_hash", submission.sourceUrlHash)
                        .addFormDataPart("review_decision", submission.decision.wireValue)
                        .addFormDataPart("policy_version", submission.policyVersion)
                        .addFormDataPart("collector_version", submission.collectorVersion)
                        .apply {
                            submission.existingContentSha256?.let {
                                addFormDataPart("existing_content_sha256", it)
                            }
                            submission.jpegBytes?.let {
                                addFormDataPart(
                                    "sample",
                                    "${submission.contentSha256}.jpg",
                                    it.toRequestBody(NormalizedMime.toMediaType()),
                                )
                            }
                        }.build()
                val request =
                    Request
                        .Builder()
                        .url("$baseUrl/functions/v1/$FunctionName")
                        .header("apikey", config.anonKey)
                        .header("Authorization", "Bearer ${authToken ?: config.anonKey}")
                        .header("x-device-token", deviceToken)
                        .post(multipart)
                        .build()
                runCatching { httpClient.newCall(request).execute() }
                    .fold(
                        onFailure = { temporary(it.dagV2CalibrationTransportReason()) },
                        onSuccess = { response ->
                            response.use {
                                val body = it.body?.string().orEmpty().take(MaxResponseCharacters)
                                if (it.isSuccessful) {
                                    runCatching {
                                        val json = JSONObject(body)
                                        DagV2CalibrationDeliveryResult.Accepted(
                                            sampleId = json.getString("sample_id"),
                                            deduplicated = json.optBoolean("deduplicated"),
                                            auditRecorded = json.optBoolean("audit_recorded"),
                                        )
                                    }.getOrElse { temporary("invalid_response") }
                                } else if (
                                    it.code == 408 ||
                                    it.code == 409 ||
                                    it.code == 425 ||
                                    it.code == 429 ||
                                    it.code >= 500
                                ) {
                                    temporary("remote_${it.code}")
                                } else {
                                    permanent("submission_rejected_${it.code}")
                                }
                            }
                        },
                    )
            }

        private fun temporary(reason: String) = DagV2CalibrationDeliveryResult.TemporaryFailure(reason)

        private fun permanent(reason: String) = DagV2CalibrationDeliveryResult.PermanentFailure(reason)

        private companion object {
            const val FunctionName = "dag-v2-calibration"
            const val NormalizedMime = "image/jpeg"
            const val MaxResponseCharacters = 4_096
        }
    }

internal fun Throwable.dagV2CalibrationTransportReason(): String =
    when (this) {
        is SocketTimeoutException -> "network_timeout"
        is UnknownHostException -> "network_dns"
        is SSLException -> "network_tls"
        is ConnectException -> "network_connect"
        is IOException -> "network_io"
        else -> "network_failure"
    }

@Singleton
class DagV2CalibrationOutboxFlusher
    @Inject
    constructor(
        private val store: DagV2CalibrationOutboxStore,
        private val gateway: SupabaseDagV2CalibrationGateway,
        private val metrics: DagV2Metrics,
    ) {
        private val mutex = kotlinx.coroutines.sync.Mutex()

        suspend fun flush(): DagV2CalibrationDeliveryResult? =
            mutex.withLock {
                var last: DagV2CalibrationDeliveryResult? = null
                val pending = store.pending()
                for (submission in pending) {
                    var result: DagV2CalibrationDeliveryResult = gateway.deliver(submission)
                    for (attempt in 1 until MaxAttempts) {
                        if (result !is DagV2CalibrationDeliveryResult.TemporaryFailure) break
                        delay(RetryDelayMillis * attempt)
                        result = gateway.deliver(submission)
                    }
                    last = result
                    when (result) {
                        is DagV2CalibrationDeliveryResult.Accepted -> {
                            store.removeAccepted(submission.submissionId)
                            submission.jpegBytes?.fill(0)
                            metrics.event(DagV2MetricNames.LabelDelivered)
                        }
                        is DagV2CalibrationDeliveryResult.PermanentFailure -> {
                            pending.forEach { it.jpegBytes?.fill(0) }
                            metrics.event(DagV2MetricNames.LabelRejected)
                            return@withLock result
                        }
                        is DagV2CalibrationDeliveryResult.TemporaryFailure -> {
                            pending.forEach { it.jpegBytes?.fill(0) }
                            return@withLock result
                        }
                    }
                }
                pending.forEach { it.jpegBytes?.fill(0) }
                if (last != null) metrics.event(DagV2MetricNames.OutboxFlushed)
                last
            }

        private companion object {
            const val MaxAttempts = 3
            const val RetryDelayMillis = 5_000L
        }
    }
