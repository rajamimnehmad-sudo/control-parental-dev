package com.contentfilter.core.data

import android.util.Log
import com.contentfilter.core.database.dao.AccessRequestDao
import com.contentfilter.core.database.dao.DeviceActivationDao
import com.contentfilter.core.database.dao.OutboxOperationDao
import com.contentfilter.core.database.entity.DeviceActivationEntity
import com.contentfilter.core.database.entity.OutboxOperationEntity
import com.contentfilter.core.domain.model.AccessRequest
import com.contentfilter.core.domain.model.RequestStatus
import com.contentfilter.core.domain.repository.AccessRequestRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class RoomAccessRequestRepository
    @Inject
    constructor(
        private val accessRequestDao: AccessRequestDao,
        private val outboxDao: OutboxOperationDao,
        private val deviceActivationDao: DeviceActivationDao,
    ) : AccessRequestRepository {
        override fun observePendingRequests(): Flow<List<AccessRequest>> =
            accessRequestDao
                .observeByStatuses(listOf(RequestStatus.PendingLocal.name, RequestStatus.PendingRemote.name))
                .map { requests -> requests.map { it.toDomain() } }

        override fun observeRequests(): Flow<List<AccessRequest>> =
            accessRequestDao.observeAll().map { requests -> requests.map { it.toDomain() } }

        override suspend fun saveRequest(request: AccessRequest) {
            val activation = deviceActivationDao.latest()
            val requestWithDevice = request.copy(deviceId = request.deviceId ?: activation?.deviceId)
            accessRequestDao.upsert(requestWithDevice.toEntity())
            val operation = requestWithDevice.toOutboxOperation(activation)
            outboxDao.upsert(operation)
            Log.i(
                LOG_TAG,
                "Saved access request id=${requestWithDevice.id} status=${requestWithDevice.status} outboxId=${operation.id}",
            )
        }

        override suspend fun updateStatus(
            requestId: String,
            status: RequestStatus,
        ) {
            val current = accessRequestDao.requestById(requestId) ?: return
            val updated = current.toDomain().copy(status = status)
            accessRequestDao.upsert(updated.toEntity())
            val operation = updated.toOutboxOperation(deviceActivationDao.latest())
            outboxDao.upsert(operation)
            Log.i(LOG_TAG, "Updated access request id=$requestId status=$status outboxId=${operation.id}")
        }

        private fun AccessRequest.toOutboxOperation(
            activation: DeviceActivationEntity?,
        ): OutboxOperationEntity {
            val now = System.currentTimeMillis()
            return OutboxOperationEntity(
                id = UUID.randomUUID().toString(),
                tableName = ACCESS_REQUESTS_TABLE,
                operation = UPSERT_OPERATION,
                payload = toRemoteJson(activation).toString(),
                status = PENDING_STATUS,
                attemptCount = 0,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            )
        }

        private fun AccessRequest.toRemoteJson(
            activation: DeviceActivationEntity?,
        ): JSONObject =
            activation.let {
                if (activation == null) {
                    Log.w(LOG_TAG, "Access request id=$id enqueued without local activation.")
                }
                JSONObject()
                    .put("id", id)
                    .put("account_id", activation?.accountId)
                    .put("device_id", deviceId)
                    .put("request_type", requestType.name)
                    .put("target_type", targetType.name)
                    .put("target", target)
                    .put("target_package_name", targetPackageName)
                    .put("target_domain", targetDomain)
                    .put("reason", reason)
                    .put("requested_minutes", requestedMinutes)
                    .put("status", status.remoteName())
                    .put("created_at", Instant.ofEpochMilli(createdAtEpochMillis).toString())
                    .put("updated_at", Instant.ofEpochMilli(System.currentTimeMillis()).toString())
                    .put("expires_at", expiresAtEpochMillis?.let { Instant.ofEpochMilli(it).toString() })
            }

        private companion object {
            const val ACCESS_REQUESTS_TABLE = "access_requests"
            const val UPSERT_OPERATION = "Upsert"
            const val PENDING_STATUS = "Pending"
            const val LOG_TAG = "AccessRequests"
        }
    }

private fun RequestStatus.remoteName(): String =
    when (this) {
        RequestStatus.PendingLocal -> RequestStatus.PendingRemote.name
        else -> name
    }
