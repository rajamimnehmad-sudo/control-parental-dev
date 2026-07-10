package com.contentfilter.core.data

import com.contentfilter.core.database.dao.DailyLimitDao
import com.contentfilter.core.database.dao.DeviceActivationDao
import com.contentfilter.core.database.dao.OutboxOperationDao
import com.contentfilter.core.database.dao.PolicyDao
import com.contentfilter.core.database.entity.OutboxOperationEntity
import com.contentfilter.core.database.entity.PolicyEntity
import com.contentfilter.core.domain.model.DailyLimit
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
        ) {
            val targetDeviceId = deviceId ?: deviceActivationDao.latest()?.deviceId
            val currentPolicy = activePolicy(targetDeviceId)
            if (currentPolicy != null && !currentPolicy.id.isRemoteCompatibleId()) {
                policyDao.deactivatePolicy(currentPolicy.id)
            }
            val now = System.currentTimeMillis()
            val policy =
                currentPolicy
                    ?.takeIf { it.id.isRemoteCompatibleId() }
                    ?.copy(version = now, updatedAtEpochMillis = now, active = true)
                    ?: createDefaultPolicy(targetDeviceId, now)
            policyDao.upsertPolicy(policy)
            dailyLimitDao.upsert(limit.toEntity().copy(policyId = policy.id))
            val accountId = deviceActivationDao.latest()?.accountId
            outboxDao.upsert(policy.toOutboxOperation(accountId, now))
            outboxDao.upsert(limit.toOutboxOperation(policy.id, accountId, now + 1))
        }

        override suspend fun deleteLimit(limit: DailyLimit) {
            val policyId =
                dailyLimitDao.byId(limit.id)?.policyId
                    ?: policyDao.activePolicy()?.id
                    ?: return
            dailyLimitDao.deleteById(limit.id)
            outboxDao.upsert(limit.toDeletedOutboxOperation(policyId, deviceActivationDao.latest()?.accountId))
        }

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
            policyDao.upsertPolicy(policy)
            return policy
        }

        private fun String.isRemoteCompatibleId(): Boolean = runCatching { UUID.fromString(this) }.isSuccess

        private fun PolicyEntity.toOutboxOperation(
            accountId: String?,
            now: Long,
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
                payload = payload,
                now = now,
                tableName = POLICIES_TABLE,
            )
        }

        private fun DailyLimit.toOutboxOperation(
            policyId: String,
            accountId: String?,
            now: Long,
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
                payload = payload,
                now = now,
                tableName = DAILY_LIMITS_TABLE,
            )
        }

        private fun DailyLimit.toDeletedOutboxOperation(
            policyId: String,
            accountId: String?,
        ): OutboxOperationEntity {
            val now = System.currentTimeMillis()
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
                payload = payload,
                now = now,
                tableName = DAILY_LIMITS_TABLE,
            )
        }

        private fun outboxOperation(
            payload: String,
            now: Long,
            tableName: String,
        ): OutboxOperationEntity =
            OutboxOperationEntity(
                id = UUID.randomUUID().toString(),
                tableName = tableName,
                operation = UPSERT_OPERATION,
                payload = payload,
                status = PENDING_STATUS,
                attemptCount = 0,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            )

        private companion object {
            const val POLICIES_TABLE = "policies"
            const val DAILY_LIMITS_TABLE = "daily_limits"
            const val UPSERT_OPERATION = "Upsert"
            const val PENDING_STATUS = "Pending"
        }
    }
