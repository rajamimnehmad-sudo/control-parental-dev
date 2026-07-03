package com.contentfilter.core.network.remote

import com.contentfilter.core.network.dto.RemoteDeviceDto

interface RemoteDeviceRepository {
    suspend fun pullDevices(updatedAfterIso: String?): RemoteResult<List<RemoteDeviceDto>>

    suspend fun markDeviceSeen(deviceId: String): RemoteResult<Unit>
}
