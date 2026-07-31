package com.contentfilter.core.network.remote

import com.contentfilter.core.domain.repository.AppFeedbackRepository
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import javax.inject.Inject

class SupabaseAppFeedbackRepository
    @Inject
    constructor(
        private val client: SupabaseRestClient,
    ) : AppFeedbackRepository {
        override suspend fun submitRating(
            deviceId: String,
            stars: Int,
            comment: String,
            appVersionCode: Int,
        ): Result<Unit> =
            client
                .invokeRpc(
                    "submit_own_app_rating",
                    JSONObject()
                        .put("p_device_id", deviceId)
                        .put("p_stars", stars)
                        .put("p_comment", comment)
                        .put("p_app_version_code", appVersionCode),
                ).asResult()

        override suspend fun reportDeviceMetadata(
            deviceId: String,
            manufacturer: String,
            model: String,
            androidVersion: String,
            androidSdk: Int,
        ): Result<Unit> =
            client
                .invokeRpc(
                    "report_own_device_metadata",
                    JSONObject()
                        .put("p_device_id", deviceId)
                        .put("p_manufacturer", manufacturer)
                        .put("p_model", model)
                        .put("p_android_version", androidVersion)
                        .put("p_android_sdk", androidSdk),
                ).asResult()

        override suspend fun updateAdminPhone(
            deviceId: String,
            phoneE164: String,
        ): Result<Unit> =
            client
                .invokeRpc(
                    "update_own_admin_contact",
                    JSONObject()
                        .put("p_device_id", deviceId)
                        .put("p_phone_e164", phoneE164),
                ).asResult()

        override suspend fun getAdminContact(deviceId: String): Result<AppFeedbackRepository.AdminContact> =
            when (val result = client.invokeRpcForArray("get_own_admin_contact", JSONObject().put("p_device_id", deviceId))) {
                is RemoteResult.Success -> {
                    val row = result.value.optJSONObject(0)
                        ?: return Result.failure(IllegalStateException("Missing admin contact"))
                    Result.success(
                        AppFeedbackRepository.AdminContact(
                            contactEmail = row.optString("contact_email"),
                            phoneE164 = row.optString("phone_e164"),
                        ),
                    )
                }
                is RemoteResult.Failure -> Result.failure(IllegalStateException(result.reason))
            }

        override suspend fun updateAdminContact(
            deviceId: String,
            contactEmail: String,
            phoneE164: String,
        ): Result<Unit> =
            client
                .invokeRpc(
                    "update_own_admin_contact_v2",
                    JSONObject()
                        .put("p_device_id", deviceId)
                        .put("p_contact_email", contactEmail)
                        .put("p_phone_e164", phoneE164),
                ).asResult()

        override suspend fun getUserContact(deviceId: String): Result<AppFeedbackRepository.UserContact> =
            when (val result = client.invokeRpcForArray("get_own_user_contact", JSONObject().put("p_device_id", deviceId))) {
                is RemoteResult.Success -> {
                    val row = result.value.optJSONObject(0)
                        ?: return Result.failure(IllegalStateException("Missing user contact"))
                    Result.success(
                        AppFeedbackRepository.UserContact(
                            contactEmail = row.optString("contact_email"),
                            phoneE164 = row.optString("phone_e164"),
                        ),
                    )
                }
                is RemoteResult.Failure -> Result.failure(IllegalStateException(result.reason))
            }

        override suspend fun updateUserContact(
            deviceId: String,
            contactEmail: String,
            phoneE164: String,
        ): Result<Unit> =
            client
                .invokeRpc(
                    "update_own_user_contact",
                    JSONObject()
                        .put("p_device_id", deviceId)
                        .put("p_contact_email", contactEmail)
                        .put("p_phone_e164", phoneE164),
                ).asResult()

        override suspend fun getRatingAvailability(deviceId: String): Result<AppFeedbackRepository.RatingAvailability> =
            when (val result = client.invokeRpcForArray("get_own_app_rating_status", JSONObject().put("p_device_id", deviceId))) {
                is RemoteResult.Success -> {
                    val row = result.value.optJSONObject(0)
                    val nextAvailableAt = row?.optString("next_available_at")
                        ?.takeIf { it.isNotBlank() && it != "null" }
                        ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                    Result.success(AppFeedbackRepository.RatingAvailability(nextAvailableAt))
                }
                is RemoteResult.Failure -> Result.failure(IllegalStateException(result.reason))
            }

        override suspend fun submitSupportReport(
            deviceId: String,
            category: String,
            safeSummary: String,
            appVersionCode: Int,
            manufacturer: String,
            model: String,
            androidVersion: String,
            diagnosticCodes: List<String>,
        ): Result<Unit> =
            client
                .invokeRpc(
                    "submit_own_support_report",
                    JSONObject()
                        .put("p_device_id", deviceId)
                        .put("p_category", category)
                        .put("p_safe_summary", safeSummary)
                        .put("p_app_version_code", appVersionCode)
                        .put("p_manufacturer", manufacturer)
                        .put("p_model", model)
                        .put("p_android_version", androidVersion)
                        .put("p_diagnostic_codes", JSONArray(diagnosticCodes)),
                ).asResult()

        private fun RemoteResult<Unit>.asResult(): Result<Unit> =
            when (this) {
                is RemoteResult.Success -> Result.success(Unit)
                is RemoteResult.Failure -> Result.failure(IllegalStateException(reason))
            }
    }
