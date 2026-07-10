package com.contentfilter.core.data

import com.contentfilter.core.database.dao.InstalledAppDao
import com.contentfilter.core.database.entity.InstalledAppEntity
import com.contentfilter.core.domain.model.InstalledApp
import com.contentfilter.core.domain.repository.InstalledAppRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomInstalledAppRepository
    @Inject
    constructor(
        private val dao: InstalledAppDao,
    ) : InstalledAppRepository {
        override fun observeInstalledApps(): Flow<List<InstalledApp>> =
            dao.observeAll().map { apps -> apps.map(InstalledAppEntity::toDomain) }

        override suspend fun latestUpdatedAt(deviceId: String): Long? = dao.latestUpdatedAt(deviceId)

        override suspend fun mergeInstalledApps(apps: List<InstalledApp>) {
            if (apps.isNotEmpty()) dao.upsertAll(apps.map(InstalledApp::toEntity))
        }

        override suspend fun deleteForDevice(deviceId: String) {
            dao.deleteForDevice(deviceId)
        }
    }

private fun InstalledAppEntity.toDomain(): InstalledApp =
    InstalledApp(
        id = id,
        accountId = accountId,
        deviceId = deviceId,
        appName = appName,
        packageName = packageName,
        versionName = versionName,
        isSystemApp = isSystemApp,
        iconBase64 = iconBase64,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

private fun InstalledApp.toEntity(): InstalledAppEntity =
    InstalledAppEntity(
        id = id,
        accountId = accountId,
        deviceId = deviceId,
        appName = appName,
        packageName = packageName,
        versionName = versionName,
        isSystemApp = isSystemApp,
        iconBase64 = iconBase64,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
