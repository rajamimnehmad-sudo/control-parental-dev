package com.contentfilter.core.domain.repository

import com.contentfilter.core.domain.model.InstalledApp
import kotlinx.coroutines.flow.Flow

interface InstalledAppRepository {
    fun observeInstalledApps(): Flow<List<InstalledApp>>

    suspend fun latestUpdatedAt(deviceId: String): Long?

    suspend fun mergeInstalledApps(apps: List<InstalledApp>)

    suspend fun deleteForDevice(deviceId: String)
}
