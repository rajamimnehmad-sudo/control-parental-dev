package com.contentfilter.core.data

import androidx.room.withTransaction
import com.contentfilter.core.database.AppDatabase
import com.contentfilter.core.database.dao.AppGroupDao
import com.contentfilter.core.database.dao.DeviceActivationDao
import com.contentfilter.core.database.dao.OutboxOperationDao
import com.contentfilter.core.database.dao.PolicyDao
import com.contentfilter.core.database.entity.AppGroupAppEntity
import com.contentfilter.core.database.entity.OutboxOperationEntity
import com.contentfilter.core.database.entity.PolicyEntity
import com.contentfilter.core.domain.model.AppGroup
import com.contentfilter.core.domain.model.AppGroupApp
import com.contentfilter.core.domain.model.PolicyMutationReceipt
import com.contentfilter.core.domain.repository.AppGroupRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
        private val database: AppDatabase,
        private val appGroupDao: AppGroupDao,
        private val policyDao: PolicyDao,
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

        override suspend fun saveGroup(
            group: AppGroup,
            requestId: String?,
        ): PolicyMutationReceipt =
            saveGroupMutation(group, requestId) { revision, _, operations ->
                appGroupDao.upsertGroup(group.toEntity(revision + 1L))
                operations += group.toOutboxOperation(revision + 1L)
            }

        override suspend fun deleteGroup(
            group: AppGroup,
            requestId: String?,
        ): PolicyMutationReceipt =
            saveGroupMutation(group, requestId) { revision, _, operations ->
                var nextTimestamp = revision + 1L
                appGroupDao.appsForGroup(group.id).forEach { app ->
                    appGroupDao.upsertApp(app.copy(enabled = false, updatedAtEpochMillis = nextTimestamp))
                    operations += app.toDeletedOutboxOperation(group.deviceId, nextTimestamp++)
                }
                appGroupDao.upsertGroup(group.copy(enabled = false).toEntity(nextTimestamp))
                operations += group.copy(enabled = false).toDeletedOutboxOperation(nextTimestamp)
            }

        override suspend fun replaceGroupApps(
            group: AppGroup,
            packageNames: List<String>,
            requestId: String?,
        ): PolicyMutationReceipt =
            saveGroupMutation(group, requestId) { revision, _, operations ->
                var nextTimestamp = revision + 1L
                appGroupDao.upsertGroup(group.toEntity(nextTimestamp))
                operations += group.toOutboxOperation(nextTimestamp++)
                val normalizedPackages = packageNames.map { it.trim() }.filter { it.isNotBlank() }.distinct()
                val existing = appGroupDao.appsForGroup(group.id)
                val existingByPackage = existing.associateBy { it.packageName }
                val keep = normalizedPackages.toSet()
                existing.filter { it.packageName !in keep }.forEach { old ->
                    appGroupDao.upsertApp(old.copy(enabled = false, updatedAtEpochMillis = nextTimestamp))
                    operations += old.toDeletedOutboxOperation(group.deviceId, nextTimestamp++)
                }
                normalizedPackages.forEach { packageName ->
                    val entity =
                        existingByPackage[packageName]?.copy(enabled = true, updatedAtEpochMillis = nextTimestamp)
                            ?: AppGroupApp(
                                id = UUID.randomUUID().toString(),
                                groupId = group.id,
                                packageName = packageName,
                                enabled = true,
                            ).toEntity(nextTimestamp)
                    appGroupDao.upsertApp(entity)
                    operations += entity.toOutboxOperation(group.deviceId, nextTimestamp++)
                }
            }

        private suspend fun saveGroupMutation(
            group: AppGroup,
            requestId: String?,
            mutate: suspend (Long, PolicyEntity, MutableList<OutboxOperationEntity>) -> Unit,
        ): PolicyMutationReceipt {
            val activation = deviceActivationDao.latest()
            val effectiveRequestId = requestId ?: UUID.randomUUID().toString()
            lateinit var receipt: PolicyMutationReceipt
            database.withTransaction {
                val currentPolicy = policyDao.activePolicyForDevice(group.deviceId)
                if (currentPolicy != null && !currentPolicy.id.isRemoteCompatibleId()) {
                    policyDao.deactivatePolicy(currentPolicy.id)
                }
                val revision =
                    maxOf(
                        System.currentTimeMillis(),
                        (currentPolicy?.version ?: 0L) + 1L,
                        (currentPolicy?.updatedAtEpochMillis ?: 0L) + 1L,
                    )
                val policy =
                    currentPolicy
                        ?.takeIf { it.id.isRemoteCompatibleId() }
                        ?.copy(version = revision, updatedAtEpochMillis = revision, active = true)
                        ?: PolicyEntity(
                            id = UUID.randomUUID().toString(),
                            deviceId = group.deviceId,
                            version = revision,
                            active = true,
                            updatedAtEpochMillis = revision,
                        )
                val operations = mutableListOf<OutboxOperationEntity>()
                policyDao.upsertPolicy(policy)
                operations += policy.toOutboxOperation(activation?.accountId, effectiveRequestId)
                mutate(revision, policy, operations)
                val enriched =
                    operations.map { operation ->
                        operation.copy(
                            requestId = effectiveRequestId,
                            aggregateId = policy.id,
                            deviceId = group.deviceId,
                            revision = revision,
                        )
                    }
                enriched.forEach { outboxDao.upsert(it) }
                receipt =
                    PolicyMutationReceipt(
                        requestId = effectiveRequestId,
                        deviceId = group.deviceId,
                        policyId = policy.id,
                        revision = revision,
                        operationIds = enriched.map { it.id },
                    )
            }
            return receipt
        }

        private fun activeDeviceIdFlow(deviceId: String?): Flow<String?> =
            if (deviceId != null) {
                flowOf(deviceId)
            } else {
                deviceActivationDao.observeLatest().map { it?.deviceId }
            }

        private fun String.isRemoteCompatibleId(): Boolean = runCatching { UUID.fromString(this) }.isSuccess

        private fun PolicyEntity.toOutboxOperation(
            accountId: String?,
            requestId: String,
        ): OutboxOperationEntity {
            val payload =
                JSONObject()
                    .put("id", id)
                    .put("account_id", accountId)
                    .put("device_id", deviceId)
                    .put("version", version)
                    .put("active", active)
                    .put("updated_at", Instant.ofEpochMilli(updatedAtEpochMillis).toString())
                    .toString()
            return outboxOperation(
                tableName = PoliciesTable,
                rowId = id,
                payload = payload,
                now = updatedAtEpochMillis,
                priority = PolicyParentPriority,
            ).copy(requestId = requestId)
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
            return outboxOperation(AppGroupsTable, id, payload, now, PolicyDependentPriority)
        }

        private suspend fun AppGroup.toDeletedOutboxOperation(now: Long): OutboxOperationEntity {
            val deletedAt = Instant.ofEpochMilli(now).toString()
            val base = toOutboxOperation(now)
            val payload = JSONObject(base.payload).put("enabled", false).put("deleted_at", deletedAt).toString()
            return base.copy(payload = payload)
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
            return outboxOperation(AppGroupAppsTable, id, payload, now, PolicyDependentPriority)
        }

        private suspend fun AppGroupAppEntity.toDeletedOutboxOperation(
            deviceId: String,
            now: Long,
        ): OutboxOperationEntity {
            val deletedAt = Instant.ofEpochMilli(now).toString()
            val base = toOutboxOperation(deviceId, now)
            val payload = JSONObject(base.payload).put("enabled", false).put("deleted_at", deletedAt).toString()
            return base.copy(payload = payload)
        }

        private fun outboxOperation(
            tableName: String,
            rowId: String,
            payload: String,
            now: Long,
            priority: Int,
        ): OutboxOperationEntity =
            OutboxOperationEntity(
                id = "$tableName:$rowId",
                tableName = tableName,
                operation = UpsertOperation,
                payload = payload,
                status = PendingStatus,
                attemptCount = 0,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                priority = priority,
            )

        private companion object {
            const val PoliciesTable = "policies"
            const val AppGroupsTable = "app_groups"
            const val AppGroupAppsTable = "app_group_apps"
            const val UpsertOperation = "Upsert"
            const val PendingStatus = "Pending"
            const val PolicyParentPriority = 100
            const val PolicyDependentPriority = 70
        }
    }
