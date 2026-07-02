package com.contentfilter.core.data

import com.contentfilter.core.database.dao.DeviceActivationDao
import com.contentfilter.core.database.dao.ExtraTimeGrantDao
import com.contentfilter.core.database.dao.OutboxOperationDao
import com.contentfilter.core.database.entity.OutboxOperationEntity
import com.contentfilter.core.domain.model.ExtraTimeGrant
import com.contentfilter.core.domain.repository.ExtraTimeGrantRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class RoomExtraTimeGrantRepository
    @Inject
    constructor(
        private val extraTimeGrantDao: ExtraTimeGrantDao,
        private val outboxDao: OutboxOperationDao,
        private val deviceActivationDao: DeviceActivationDao,
    ) : ExtraTimeGrantRepository {
        override fun observeActiveGrants(nowEpochMillis: Long): Flow<List<ExtraTimeGrant>> =
            extraTimeGrantDao.observeActive(nowEpochMillis).map { grants -> grants.map { it.toDomain() } }

        override fun observeGrants(): Flow<List<ExtraTimeGrant>> =
            extraTimeGrantDao.observeAll().map { grants -> grants.map { it.toDomain() } }

        override suspend fun saveGrant(grant: ExtraTimeGrant) {
            extraTimeGrantDao.upsert(grant.toEntity())
            outboxDao.upsert(grant.toOutboxOperation(deviceActivationDao.latest()?.accountId))
        }

        private fun ExtraTimeGrant.toOutboxOperation(accountId: String?): OutboxOperationEntity {
            val now = System.currentTimeMillis()
            val payload =
                org.json.JSONObject()
                    .put("id", id)
                    .put("account_id", accountId)
                    .put("request_id", requestId)
                    .put("target_type", targetType.name)
                    .put("target", target)
                    .put("granted_minutes", grantedMinutes)
                    .put("valid_until", Instant.ofEpochMilli(validUntilEpochMillis).toString())
                    .put("updated_at", Instant.ofEpochMilli(now).toString())
                    .toString()
            return OutboxOperationEntity(
                id = UUID.randomUUID().toString(),
                tableName = EXTRA_TIME_GRANTS_TABLE,
                operation = UPSERT_OPERATION,
                payload = payload,
                status = PENDING_STATUS,
                attemptCount = 0,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            )
        }

        private companion object {
            const val EXTRA_TIME_GRANTS_TABLE = "extra_time_grants"
            const val UPSERT_OPERATION = "Upsert"
            const val PENDING_STATUS = "Pending"
        }
    }
