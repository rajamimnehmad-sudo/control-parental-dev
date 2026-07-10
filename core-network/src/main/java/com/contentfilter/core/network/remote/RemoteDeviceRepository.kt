package com.contentfilter.core.network.remote

import com.contentfilter.core.domain.model.SystemHealthSnapshot
import com.contentfilter.core.network.dto.RemoteDeviceDto

interface RemoteDeviceRepository {
    suspend fun pullDevices(updatedAfterIso: String?): RemoteResult<List<RemoteDeviceDto>>

    suspend fun pullDevice(deviceId: String): RemoteResult<List<RemoteDeviceDto>>

    suspend fun markDeviceSeen(
        deviceId: String,
        health: SystemHealthSnapshot?,
    ): RemoteResult<Unit>

    suspend fun acknowledgePolicyApplied(
        deviceId: String,
        policyId: String,
        revision: Long,
    ): RemoteResult<Unit>
}
