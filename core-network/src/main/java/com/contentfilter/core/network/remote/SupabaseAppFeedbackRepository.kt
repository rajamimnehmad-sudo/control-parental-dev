package com.contentfilter.core.network.remote

import com.contentfilter.core.domain.repository.AppFeedbackRepository
import org.json.JSONObject
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

        private fun RemoteResult<Unit>.asResult(): Result<Unit> =
            when (this) {
                is RemoteResult.Success -> Result.success(Unit)
                is RemoteResult.Failure -> Result.failure(IllegalStateException(reason))
            }
    }
