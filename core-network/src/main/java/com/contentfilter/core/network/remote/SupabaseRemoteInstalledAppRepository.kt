package com.contentfilter.core.network.remote

import com.contentfilter.core.network.dto.RemoteInstalledAppDto
import javax.inject.Inject

class SupabaseRemoteInstalledAppRepository
    @Inject
    constructor(
        private val client: SupabaseRestClient,
    ) : RemoteInstalledAppRepository {
        override suspend fun pullInstalledApps(): RemoteResult<List<RemoteInstalledAppDto>> =
            client.selectAll(SupabaseTable.DeviceApps).mapArray(RemoteInstalledAppDto::fromJson)

        override suspend fun upsertInstalledApp(app: RemoteInstalledAppDto): RemoteResult<Unit> =
            client.upsert(SupabaseTable.DeviceApps, app.toJson())
    }
