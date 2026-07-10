package com.contentfilter.core.network.remote

import android.util.Log
import com.contentfilter.core.domain.model.ProtectionAlertType
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.domain.repository.PushNotificationRepository
import org.json.JSONObject
import java.time.Instant
import javax.inject.Inject

class SupabasePushNotificationRepository
    @Inject
    constructor(
        private val client: SupabaseRestClient,
        private val activationRepository: DeviceActivationRepository,
    ) : PushNotificationRepository {
        override suspend fun registerAdminToken(token: String) {
            val activation = activationRepository.currentActivation() ?: return
            val now = Instant.now().toString()
            val json =
                JSONObject()
                    .put("id", activation.deviceId)
                    .put("account_id", activation.accountId)
                    .put("device_id", activation.deviceId)
                    .put("app_role", "admin")
                    .put("platform", "android")
                    .put("fcm_token", token)
                    .put("updated_at", now)
            when (val result = client.upsert(SupabaseTable.DevicePushTokens, json)) {
                is RemoteResult.Success -> Unit
                is RemoteResult.Failure -> Log.w(LogTag, "Admin FCM token upsert failed: ${result.reason}")
            }
        }

        override suspend fun reportProtectionAlert(type: ProtectionAlertType) {
            val activation = activationRepository.currentActivation() ?: return
            val json =
                JSONObject()
                    .put("device_id", activation.deviceId)
                    .put("alert_type", type.remoteValue)
            when (val result = client.invokeFunction(FunctionName, json)) {
                is RemoteResult.Success -> Unit
                is RemoteResult.Failure -> Log.w(LogTag, "Protection alert push failed: ${result.reason}")
            }
        }

        private companion object {
            const val FunctionName = "send-protection-alert"
            const val LogTag = "PushNotifications"
        }
    }
