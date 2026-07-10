package com.contentfilter.core.data

import androidx.room.withTransaction
import com.contentfilter.core.database.AppDatabase
import com.contentfilter.core.database.dao.DailyLimitDao
import com.contentfilter.core.database.dao.DeviceActivationDao
import com.contentfilter.core.database.dao.OutboxOperationDao
import com.contentfilter.core.database.dao.PolicyDao
import com.contentfilter.core.database.entity.OutboxOperationEntity
import com.contentfilter.core.database.entity.PolicyEntity
import com.contentfilter.core.domain.model.DailyLimit
import com.contentfilter.core.domain.model.PolicyMutationReceipt
import com.contentfilter.core.domain.model.PolicyTargetType
import com.contentfilter.core.domain.repository.DailyLimitRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class RoomDailyLimitRepository
    @Inject
    constructor(
        private val database: AppDatabase,
        private val dailyLimitDao: DailyLimitDao,
        private val policyDao: PolicyDao,
        private val outboxDao: OutboxOperationDao,
        private val deviceActivationDao: DeviceActivationDao,
    ) : DailyLimitRepository {
        @OptIn(ExperimentalCoroutinesApi::class)
        override fun observeLimits(deviceId: String?): Flow<List<DailyLimit>> =
            activeDeviceIdFlow(deviceId).flatMapLatest { activeDeviceId ->
                if (activeDeviceId != null) {
                    dailyLimitDao.observeEnabledForDevice(activeDeviceId)
                } else {
                    dailyLimitDao.observeEnabled()
                }
            }.map { limits ->
                limits
                    .map { it.toDomain() }
                    .filter { it.targetType == PolicyTargetType.App || it.targetType == PolicyTargetType.Domain }
            }

        override suspend fun saveLimit(
            limit: DailyLimit,
            deviceId: String?,
            requestId: String?,
        ): PolicyMutationReceipt {
            val activation = deviceActivationDao.latest()
            val targetDeviceId = requireNotNull(deviceId ?: activation?.deviceId)
            val effectiveRequestId = requestId ?: UUID.randomUUID().toString()
            lateinit var receipt: PolicyMutationReceipt
            database.withTransaction {
                val currentPolicy = activePolicy(targetDeviceId)
                if (currentPolicy != null && !currentPolicy.id.isRemoteCompatibleId()) {
                    policyDao.deactivatePolicy(currentPolicy.id)
                }
                val revision = nextRevision(currentPolicy)
                val policy =
                    currentPolicy
                        ?.takeIf { it.id.isRemoteCompatibleId() }
                        ?.copy(version = revision, updatedAtEpochMillis = revision, active = true)
                        ?: createDefaultPolicy(targetDeviceId, revision)
                val policyOperation =
                    policy.toOutboxOperation(activation?.accountId, revision, effectiveRequestId, targetDeviceId)
                val limitOperation =
                    limit.toOutboxOperation(
                        policyId = policy.id,
                        accountId = activation?.accountId,
                        now = revision + 1L,
                        requestId = effectiveRequestId,
                        deviceId = targetDeviceId,
                        revision = revision,
                    )
                policyDao.upsertPolicy(policy)
                dailyLimitDao.upsert(
                    limit.toEntity().copy(
                        policyId = policy.id,
                        updatedAtEpochMillis = revision + 1L,
                    ),
                )
                outboxDao.upsert(policyOperation)
                outboxDao.upsert(limitOperation)
                receipt =
                    PolicyMutationReceipt(
                        requestId = effectiveRequestId,
                        deviceId = targetDeviceId,
                        policyId = policy.id,
                        revision = revision,
                        operationIds = listOf(policyOperation.id, limitOperation.id),
                    )
            }
            return receipt
        }

        override suspend fun deleteLimit(
            limit: DailyLimit,
            deviceId: String?,
            requestId: String?,
        ): PolicyMutationReceipt {
            val activation = deviceActivationDao.latest()
            val targetDeviceId = requireNotNull(deviceId ?: activation?.deviceId)
            val effectiveRequestId = requestId ?: UUID.randomUUID().toString()
            lateinit var receipt: PolicyMutationReceipt
            database.withTransaction {
                val stored = dailyLimitDao.byId(limit.id)
                val currentPolicy =
                    stored?.policyId?.let { policyDao.policyById(it) }
                        ?: activePolicy(targetDeviceId)
                        ?: createDefaultPolicy(targetDeviceId, System.currentTimeMillis())
                val revision = nextRevision(currentPolicy)
                val policy = currentPolicy.copy(version = revision, updatedAtEpochMillis = revision, active = true)
                val policyOperation =
                    policy.toOutboxOperation(activation?.accountId, revision, effectiveRequestId, targetDeviceId)
                val limitOperation =
                    limit.toDeletedOutboxOperation(
                        policyId = policy.id,
                        accountId = activation?.accountId,
                        now = revision + 1L,
                        requestId = effectiveRequestId,
                        deviceId = targetDeviceId,
                        revision = revision,
                    )
                policyDao.upsertPolicy(policy)
                dailyLimitDao.upsert(
                    limit.toEntity().copy(
                        policyId = policy.id,
                        enabled = false,
                        updatedAtEpochMillis = revision + 1L,
                    ),
                )
                outboxDao.upsert(policyOperation)
                outboxDao.upsert(limitOperation)
                receipt =
                    PolicyMutationReceipt(
                        requestId = effectiveRequestId,
                        deviceId = targetDeviceId,
                        policyId = policy.id,
                        revision = revision,
                        operationIds = listOf(policyOperation.id, limitOperation.id),
                    )
            }
            return receipt
        }

        private fun nextRevision(currentPolicy: PolicyEntity?): Long =
            maxOf(
                System.currentTimeMillis(),
                (currentPolicy?.version ?: 0L) + 1L,
                (currentPolicy?.updatedAtEpochMillis ?: 0L) + 1L,
            )

        private fun activeDeviceIdFlow(deviceId: String?): Flow<String?> =
            if (deviceId != null) {
                flowOf(deviceId)
            } else {
                deviceActivationDao.observeLatest().map { it?.deviceId }
            }

        private suspend fun activePolicy(deviceId: String?): PolicyEntity? =
            if (deviceId != null) {
                policyDao.activePolicyForDevice(deviceId)
            } else {
                policyDao.activePolicy()
            }

        private suspend fun createDefaultPolicy(
            deviceId: String?,
            now: Long,
        ): PolicyEntity {
            val policy =
                PolicyEntity(
                    id = UUID.randomUUID().toString(),
                    deviceId = deviceId,
                    version = now,
                    active = true,
                    updatedAtEpochMillis = now,
                )
            return policy
        }

        private fun String.isRemoteCompatibleId(): Boolean = runCatching { UUID.fromString(this) }.isSuccess

        private fun PolicyEntity.toOutboxOperation(
            accountId: String?,
            now: Long,
            requestId: String,
            deviceId: String,
        ): OutboxOperationEntity {
            val payload =
                org.json.JSONObject()
                    .put("id", id)
                    .put("account_id", accountId)
                    .put("device_id", deviceId)
                    .put("version", version)
                    .put("active", active)
                    .put("updated_at", Instant.ofEpochMilli(updatedAtEpochMillis).toString())
                    .toString()
            return outboxOperation(
                rowId = id,
                payload = payload,
                now = now,
                tableName = POLICIES_TABLE,
                requestId = requestId,
                aggregateId = id,
                deviceId = deviceId,
                revision = version,
                priority = POLICY_PARENT_PRIORITY,
            )
        }

        private fun DailyLimit.toOutboxOperation(
            policyId: String,
            accountId: String?,
            now: Long,
            requestId: String,
            deviceId: String,
            revision: Long,
        ): OutboxOperationEntity {
            val payload =
                org.json.JSONObject()
                    .put("id", id)
                    .put("account_id", accountId)
                    .put("policy_id", policyId)
                    .put("target_type", targetType.name)
                    .put("target", target)
                    .put("limit_minutes", limitMinutes)
                    .put("enabled", enabled)
                    .put("updated_at", Instant.ofEpochMilli(now).toString())
                    .toString()
            return outboxOperation(
                rowId = id,
                payload = payload,
                now = now,
                tableName = DAILY_LIMITS_TABLE,
                requestId = requestId,
                aggregateId = policyId,
                deviceId = deviceId,
                revision = revision,
                priority = POLICY_DEPENDENT_PRIORITY,
            )
        }

        private fun DailyLimit.toDeletedOutboxOperation(
            policyId: String,
            accountId: String?,
            now: Long,
            requestId: String,
            deviceId: String,
            revision: Long,
        ): OutboxOperationEntity {
            val deletedAt = Instant.ofEpochMilli(now).toString()
            val payload =
                org.json.JSONObject()
                    .put("id", id)
                    .put("account_id", accountId)
                    .put("policy_id", policyId)
                    .put("target_type", targetType.name)
                    .put("target", target)
                    .put("limit_minutes", limitMinutes)
                    .put("enabled", false)
                    .put("updated_at", deletedAt)
                    .put("deleted_at", deletedAt)
                    .toString()
            return outboxOperation(
                rowId = id,
                payload = payload,
                now = now,
                tableName = DAILY_LIMITS_TABLE,
                requestId = requestId,
                aggregateId = policyId,
                deviceId = deviceId,
                revision = revision,
                priority = POLICY_DEPENDENT_PRIORITY,
            )
        }

        private fun outboxOperation(
            rowId: String,
            payload: String,
            now: Long,
            tableName: String,
            requestId: String,
            aggregateId: String,
            deviceId: String,
            revision: Long,
            priority: Int,
        ): OutboxOperationEntity =
            OutboxOperationEntity(
                id = "$tableName:$rowId",
                tableName = tableName,
                operation = UPSERT_OPERATION,
                payload = payload,
                status = PENDING_STATUS,
                attemptCount = 0,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                requestId = requestId,
                aggregateId = aggregateId,
                deviceId = deviceId,
                revision = revision,
                priority = priority,
            )

        private companion object {
            const val POLICIES_TABLE = "policies"
            const val DAILY_LIMITS_TABLE = "daily_limits"
            const val UPSERT_OPERATION = "Upsert"
            const val PENDING_STATUS = "Pending"
            const val POLICY_PARENT_PRIORITY = 100
            const val POLICY_DEPENDENT_PRIORITY = 80
        }
    }
