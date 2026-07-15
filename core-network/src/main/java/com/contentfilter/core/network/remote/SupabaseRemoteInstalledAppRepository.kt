package com.contentfilter.core.network.remote

import com.contentfilter.core.network.dto.RemoteInstalledAppDto
import org.json.JSONArray
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

        override suspend fun upsertInstalledApps(apps: List<RemoteInstalledAppDto>): RemoteResult<Unit> {
            apps.chunked(MaxUpsertBatchSize).forEach { batch ->
                val result =
                    client.upsert(
                        SupabaseTable.DeviceApps,
                        JSONArray().apply { batch.forEach { put(it.toJson()) } },
                    )
                if (result is RemoteResult.Failure) return result
            }
            return RemoteResult.Success(Unit)
        }

        private companion object {
            const val MaxUpsertBatchSize = 20
        }
    }
