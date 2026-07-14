package com.contentfilter.core.network.remote

import com.contentfilter.core.domain.model.DeviceProtectionAlert
import com.contentfilter.core.domain.model.SystemHealthSnapshot
import com.contentfilter.core.network.dto.RemoteDeviceDto
import org.json.JSONObject
import java.time.Instant
import javax.inject.Inject

class SupabaseRemoteDeviceRepository
    @Inject
    constructor(
        private val client: SupabaseRestClient,
    ) : RemoteDeviceRepository {
        override suspend fun pullDevices(updatedAfterIso: String?): RemoteResult<List<RemoteDeviceDto>> =
            client.selectUpdatedSince(SupabaseTable.Devices, updatedAfterIso).mapArray(RemoteDeviceDto::fromJson)

        override suspend fun pullDevice(deviceId: String): RemoteResult<List<RemoteDeviceDto>> =
            client.selectByEquals(
                table = SupabaseTable.Devices,
                filters = mapOf("id" to deviceId),
            ).mapArray(RemoteDeviceDto::fromJson)

        override suspend fun markDeviceSeen(
            deviceId: String,
            health: SystemHealthSnapshot?,
        ): RemoteResult<Unit> {
            val withProtection = health?.toDeviceSeenJson() ?: client.deviceSeenJson()
            val result = client.patchById(SupabaseTable.Devices, deviceId, withProtection)
            if (result is RemoteResult.Success || health == null) return result
            return client.patchById(SupabaseTable.Devices, deviceId, client.deviceSeenJson())
        }

        override suspend fun updateAppVersion(
            deviceId: String,
            appVersionCode: Int,
        ): RemoteResult<Unit> {
            val now = Instant.now().toString()
            return client.patchById(
                table = SupabaseTable.Devices,
                id = deviceId,
                json =
                    JSONObject()
                        .put("device_app_version_code", appVersionCode)
                        .put("updated_at", now),
            )
        }

        override suspend fun acknowledgePolicyApplied(
            deviceId: String,
            policyId: String,
            revision: Long,
        ): RemoteResult<Unit> {
            val now = Instant.now().toString()
            return client.patchById(
                table = SupabaseTable.Devices,
                id = deviceId,
                json =
                    JSONObject()
                        .put("applied_policy_id", policyId)
                        .put("applied_policy_revision", revision)
                        .put("policy_applied_at", now)
                        .put("last_seen_at", now)
                        .put("updated_at", now),
            )
        }

        private fun SystemHealthSnapshot.toDeviceSeenJson(): JSONObject {
            val now = Instant.now().toString()
            return JSONObject()
                .put("last_seen_at", now)
                .put("updated_at", now)
                .put("vpn_state", vpnState.name)
                .put("accessibility_state", accessibilityState.name)
                .put("device_admin_state", deviceAdminState.name)
                .put(
                    "protection_alert",
                    DeviceProtectionAlert.fromStates(vpnState, accessibilityState, deviceAdminState)
                        ?: JSONObject.NULL,
                )
                .put("protection_updated_at", now)
        }
    }
