package com.contentfilter.core.network.remote

import android.util.Log
import com.contentfilter.core.domain.model.ProtectionAlertType
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.domain.repository.PushNotificationRepository
import org.json.JSONObject
import javax.inject.Inject

class SupabasePushNotificationRepository
    @Inject
    constructor(
        private val client: SupabaseRestClient,
        private val activationRepository: DeviceActivationRepository,
    ) : PushNotificationRepository {
        override suspend fun registerAdminToken(token: String) {
            val json = JSONObject().put("p_fcm_token", token)
            when (val result = client.invokeRpc(RegisterAdminPushTokenRpc, json)) {
                is RemoteResult.Success -> Unit
                is RemoteResult.Failure -> Log.w(LogTag, "Admin FCM token registration failed: ${result.reason}")
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
            const val RegisterAdminPushTokenRpc = "register_admin_push_token"
            const val LogTag = "PushNotifications"
        }
    }
