package com.contentfilter.core.network.remote

import com.contentfilter.core.network.dto.RemoteInstalledAppDto

interface RemoteInstalledAppRepository {
    suspend fun pullInstalledApps(
        deviceId: String? = null,
        updatedAfterIso: String? = null,
    ): RemoteResult<List<RemoteInstalledAppDto>>

    suspend fun upsertInstalledApp(app: RemoteInstalledAppDto): RemoteResult<Unit>

    suspend fun upsertInstalledApps(apps: List<RemoteInstalledAppDto>): RemoteResult<Unit>
}
