package com.contentfilter.core.network.remote

import com.contentfilter.core.network.dto.RemoteDeviceDto
import javax.inject.Inject

class SupabaseRemoteDeviceRepository
    @Inject
    constructor(
        private val client: SupabaseRestClient,
    ) : RemoteDeviceRepository {
        override suspend fun pullDevices(updatedAfterIso: String?): RemoteResult<List<RemoteDeviceDto>> =
            client.selectUpdatedSince(SupabaseTable.Devices, updatedAfterIso).mapArray(RemoteDeviceDto::fromJson)

        override suspend fun markDeviceSeen(deviceId: String): RemoteResult<Unit> =
            client.patchById(SupabaseTable.Devices, deviceId, client.deviceSeenJson())
    }
