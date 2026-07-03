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
import kotlinx.coroutines.flow.Flow
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
        override fun observeLimits(): Flow<List<DailyLimit>> =
            dailyLimitDao.observeEnabled().map { limits ->
                limits
                    .map { it.toDomain() }
                    .filter { it.targetType == PolicyTargetType.App || it.targetType == PolicyTargetType.Domain }
            }

        override suspend fun saveLimit(limit: DailyLimit) {
            val policy = policyDao.activePolicy() ?: createDefaultPolicy()
            dailyLimitDao.upsert(limit.toEntity())
            outboxDao.upsert(limit.toOutboxOperation(policy.id, deviceActivationDao.latest()?.accountId))
        }

        private suspend fun createDefaultPolicy(): PolicyEntity {
            val policy =
                PolicyEntity(
                    id = UUID.randomUUID().toString(),
                    version = System.currentTimeMillis(),
                    active = true,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                )
            policyDao.upsertPolicy(policy)
            return policy
        }

        private fun DailyLimit.toOutboxOperation(
            policyId: String,
            accountId: String?,
        ): OutboxOperationEntity {
            val now = System.currentTimeMillis()
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
            return OutboxOperationEntity(
                id = UUID.randomUUID().toString(),
                tableName = DAILY_LIMITS_TABLE,
                operation = UPSERT_OPERATION,
                payload = payload,
                status = PENDING_STATUS,
                attemptCount = 0,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            )
        }

        private companion object {
            const val DAILY_LIMITS_TABLE = "daily_limits"
            const val UPSERT_OPERATION = "Upsert"
            const val PENDING_STATUS = "Pending"
        }
    }
