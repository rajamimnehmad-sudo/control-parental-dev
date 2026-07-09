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

        override suspend fun markDeviceSeen(
            deviceId: String,
            health: SystemHealthSnapshot?,
        ): RemoteResult<Unit> {
            val withProtection = health?.toDeviceSeenJson() ?: client.deviceSeenJson()
            val result = client.patchById(SupabaseTable.Devices, deviceId, withProtection)
            if (result is RemoteResult.Success || health == null) return result
            return client.patchById(SupabaseTable.Devices, deviceId, client.deviceSeenJson())
        }

        private fun SystemHealthSnapshot.toDeviceSeenJson(): JSONObject {
            val now = Instant.now().toString()
            return JSONObject()
                .put("last_seen_at", now)
                .put("updated_at", now)
                .put("vpn_state", vpnState.name)
                .put("accessibility_state", accessibilityState.name)
                .put("protection_alert", DeviceProtectionAlert.fromStates(vpnState, accessibilityState))
                .put("protection_updated_at", now)
        }
    }
