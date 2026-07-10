package com.contentfilter.core.network.remote

import com.contentfilter.core.network.dto.RemoteInstalledAppDto
import javax.inject.Inject

class SupabaseRemoteInstalledAppRepository
    @Inject
    constructor(
        private val client: SupabaseRestClient,
    ) : RemoteInstalledAppRepository {
        override suspend fun pullInstalledApps(
            deviceId: String?,
            updatedAfterIso: String?,
        ): RemoteResult<List<RemoteInstalledAppDto>> =
            if (deviceId == null) {
                client.selectUpdatedSince(SupabaseTable.DeviceApps, updatedAfterIso)
            } else {
                client.selectByEquals(
                    table = SupabaseTable.DeviceApps,
                    filters = mapOf("device_id" to deviceId),
                    updatedAfterIso = updatedAfterIso,
                )
            }.mapArray(RemoteInstalledAppDto::fromJson)

        override suspend fun upsertInstalledApp(app: RemoteInstalledAppDto): RemoteResult<Unit> =
            client.upsert(SupabaseTable.DeviceApps, app.toJson())
    }
