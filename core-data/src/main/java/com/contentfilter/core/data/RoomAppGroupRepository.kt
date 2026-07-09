package com.contentfilter.core.data

import com.contentfilter.core.database.dao.AppGroupDao
import com.contentfilter.core.database.dao.DeviceActivationDao
import com.contentfilter.core.database.dao.OutboxOperationDao
import com.contentfilter.core.database.entity.AppGroupAppEntity
import com.contentfilter.core.database.entity.OutboxOperationEntity
import com.contentfilter.core.domain.model.AppGroup
import com.contentfilter.core.domain.model.AppGroupApp
import com.contentfilter.core.domain.repository.AppGroupRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class RoomAppGroupRepository
    @Inject
    constructor(
        private val appGroupDao: AppGroupDao,
        private val deviceActivationDao: DeviceActivationDao,
        private val outboxDao: OutboxOperationDao,
    ) : AppGroupRepository {
        @OptIn(ExperimentalCoroutinesApi::class)
        override fun observeGroups(deviceId: String?): Flow<List<AppGroup>> =
            activeDeviceIdFlow(deviceId).flatMapLatest { activeDeviceId ->
                if (activeDeviceId == null) {
                    flowOf(emptyList())
                } else {
                    appGroupDao.observeEnabledGroupsForDevice(activeDeviceId).flatMapLatest { groups ->
                        if (groups.isEmpty()) {
                            flowOf(emptyList())
                        } else {
                            appGroupDao.observeEnabledAppsForGroups(groups.map { it.id }).map { apps ->
                                groups.map { group -> group.toDomain(apps) }
                            }
                        }
                    }
                }
            }

        override suspend fun saveGroup(group: AppGroup) {
            val now = System.currentTimeMillis()
            appGroupDao.upsertGroup(group.toEntity(now))
            outboxDao.upsert(group.toOutboxOperation(now))
        }

        override suspend fun deleteGroup(group: AppGroup) {
            val now = System.currentTimeMillis()
            val existingApps = appGroupDao.appsForGroup(group.id)
            existingApps.forEach { app ->
                appGroupDao.deleteAppById(app.id)
                outboxDao.upsert(app.toDeletedOutboxOperation(group.deviceId, now))
            }
            appGroupDao.deleteGroupById(group.id)
            outboxDao.upsert(group.copy(enabled = false).toDeletedOutboxOperation(now))
        }

        override suspend fun replaceGroupApps(
            group: AppGroup,
            packageNames: List<String>,
        ) {
            saveGroup(group)
            val now = System.currentTimeMillis()
            val normalizedPackages = packageNames.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            val existing = appGroupDao.appsForGroup(group.id)
            val existingByPackage = existing.associateBy { it.packageName }
            val keep = normalizedPackages.toSet()
            existing.filter { it.packageName !in keep }.forEach { old ->
                appGroupDao.deleteAppById(old.id)
                outboxDao.upsert(old.toDeletedOutboxOperation(group.deviceId, now))
            }
            normalizedPackages.forEach { packageName ->
                val entity =
                    existingByPackage[packageName]?.copy(enabled = true, updatedAtEpochMillis = now)
                        ?: AppGroupApp(
                            id = UUID.randomUUID().toString(),
                            groupId = group.id,
                            packageName = packageName,
                            enabled = true,
                        ).toEntity(now)
                appGroupDao.upsertApp(entity)
                outboxDao.upsert(entity.toOutboxOperation(group.deviceId, now))
            }
        }

        private fun activeDeviceIdFlow(deviceId: String?): Flow<String?> =
            if (deviceId != null) {
                flowOf(deviceId)
            } else {
                deviceActivationDao.observeLatest().map { it?.deviceId }
            }

        private suspend fun AppGroup.toOutboxOperation(now: Long): OutboxOperationEntity {
            val activation = deviceActivationDao.latest()
            val payload =
                JSONObject()
                    .put("id", id)
                    .put("account_id", activation?.accountId)
                    .put("device_id", deviceId)
                    .put("name", name)
                    .put("color", color)
                    .put("limit_minutes", limitMinutes)
                    .put("reset_minute_of_day", resetMinuteOfDay)
                    .put("enabled", enabled)
                    .put("updated_at", Instant.ofEpochMilli(now).toString())
                    .toString()
            return outboxOperation(AppGroupsTable, payload, now)
        }

        private suspend fun AppGroup.toDeletedOutboxOperation(now: Long): OutboxOperationEntity {
            val activation = deviceActivationDao.latest()
            val deletedAt = Instant.ofEpochMilli(now).toString()
            val payload =
                JSONObject()
                    .put("id", id)
                    .put("account_id", activation?.accountId)
                    .put("device_id", deviceId)
                    .put("name", name)
                    .put("color", color)
                    .put("limit_minutes", limitMinutes)
                    .put("reset_minute_of_day", resetMinuteOfDay)
                    .put("enabled", false)
                    .put("updated_at", deletedAt)
                    .put("deleted_at", deletedAt)
                    .toString()
            return outboxOperation(AppGroupsTable, payload, now)
        }

        private suspend fun AppGroupAppEntity.toOutboxOperation(
            deviceId: String,
            now: Long,
        ): OutboxOperationEntity {
            val activation = deviceActivationDao.latest()
            val payload =
                JSONObject()
                    .put("id", id)
                    .put("account_id", activation?.accountId)
                    .put("device_id", deviceId)
                    .put("group_id", groupId)
                    .put("package_name", packageName)
                    .put("enabled", enabled)
                    .put("updated_at", Instant.ofEpochMilli(now).toString())
                    .toString()
            return outboxOperation(AppGroupAppsTable, payload, now)
        }

        private suspend fun AppGroupAppEntity.toDeletedOutboxOperation(
            deviceId: String,
            now: Long,
        ): OutboxOperationEntity {
            val activation = deviceActivationDao.latest()
            val deletedAt = Instant.ofEpochMilli(now).toString()
            val payload =
                JSONObject()
                    .put("id", id)
                    .put("account_id", activation?.accountId)
                    .put("device_id", deviceId)
                    .put("group_id", groupId)
                    .put("package_name", packageName)
                    .put("enabled", false)
                    .put("updated_at", deletedAt)
                    .put("deleted_at", deletedAt)
                    .toString()
            return outboxOperation(AppGroupAppsTable, payload, now)
        }

        private fun outboxOperation(
            tableName: String,
            payload: String,
            now: Long,
        ): OutboxOperationEntity =
            OutboxOperationEntity(
                id = UUID.randomUUID().toString(),
                tableName = tableName,
                operation = UpsertOperation,
                payload = payload,
                status = PendingStatus,
                attemptCount = 0,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            )

        private companion object {
            const val AppGroupsTable = "app_groups"
            const val AppGroupAppsTable = "app_group_apps"
            const val UpsertOperation = "Upsert"
            const val PendingStatus = "Pending"
        }
    }
