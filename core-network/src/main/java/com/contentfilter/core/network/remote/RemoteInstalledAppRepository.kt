package com.contentfilter.core.network.remote

import com.contentfilter.core.network.dto.RemoteInstalledAppDto

interface RemoteInstalledAppRepository {
    suspend fun pullInstalledApps(): RemoteResult<List<RemoteInstalledAppDto>>

    suspend fun upsertInstalledApp(app: RemoteInstalledAppDto): RemoteResult<Unit>
}
