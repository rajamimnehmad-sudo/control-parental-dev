package com.contentfilter.core.sync.outbox

import android.util.Log
import com.contentfilter.core.database.dao.DeviceActivationDao
import com.contentfilter.core.database.dao.OutboxOperationDao
import com.contentfilter.core.database.entity.OutboxOperationEntity
import com.contentfilter.core.network.dto.RemoteAccessRequestDto
import com.contentfilter.core.network.dto.RemoteExtraTimeGrantDto
import com.contentfilter.core.network.dto.RemotePolicyDto
import com.contentfilter.core.network.dto.RemotePolicyRuleDto
import com.contentfilter.core.network.remote.RemotePolicyRepository
import com.contentfilter.core.network.remote.RemoteRequestRepository
import com.contentfilter.core.network.remote.RemoteResult
import javax.inject.Inject
import org.json.JSONObject

class DefaultOutboxProcessor
    @Inject
    constructor(
        private val outboxDao: OutboxOperationDao,
        private val deviceActivationDao: DeviceActivationDao,
        private val requestRepository: RemoteRequestRepository,
        private val policyRepository: RemotePolicyRepository,
    ) : OutboxProcessor {
        override suspend fun processPending() {
            val operations = outboxDao.pending()
            if (operations.isNotEmpty()) {
                Log.i(LogTag, "Processing pending outbox operations count=${operations.size}")
            }
            operations.forEach { operation ->
                val result = runCatching {
                    push(operation)
                }.getOrElse { exception ->
                    Log.e(
                        LogTag,
                        "Outbox push crashed id=${operation.id} table=${operation.tableName}: ${exception.message}",
                        exception,
                    )
                    RemoteResult.Failure("Outbox payload could not be pushed.", retryable = false)
                }
                val nextStatus = when (result) {
                    is RemoteResult.Success -> OutboxStatus.Synced
                    is RemoteResult.Failure -> if (result.retryable) OutboxStatus.Pending else OutboxStatus.Failed
                }
                when (result) {
                    is RemoteResult.Success -> Log.i(
                        LogTag,
                        "Outbox push succeeded id=${operation.id} table=${operation.tableName}",
                    )
                    is RemoteResult.Failure -> Log.w(
                        LogTag,
                        "Outbox push failed id=${operation.id} table=${operation.tableName} retryable=${result.retryable}: ${result.reason}",
                    )
                }
                outboxDao.updateStatus(
                    id = operation.id,
                    status = nextStatus.name,
                    attemptCount = operation.attemptCount + 1,
                    updatedAt = System.currentTimeMillis(),
                )
            }
        }

        private suspend fun push(operation: OutboxOperationEntity): RemoteResult<Unit> =
            when (operation.tableName) {
                PoliciesTable -> policyRepository.upsertPolicy(operation.toPolicyDto())
                PolicyRulesTable -> policyRepository.upsertPolicyRule(operation.toPolicyRuleDto())
                AccessRequestsTable -> requestRepository.upsertAccessRequest(operation.toAccessRequestDto())
                ExtraTimeGrantsTable -> requestRepository.upsertExtraTimeGrant(operation.toExtraTimeGrantDto())
                else -> RemoteResult.Failure("Unsupported outbox table ${operation.tableName}.", retryable = false)
            }

        private suspend fun OutboxOperationEntity.toPolicyDto(): RemotePolicyDto =
            RemotePolicyDto.fromJson(payloadJsonWithAccount())

        private suspend fun OutboxOperationEntity.toPolicyRuleDto(): RemotePolicyRuleDto =
            RemotePolicyRuleDto.fromJson(payloadJsonWithAccount())

        private suspend fun OutboxOperationEntity.toAccessRequestDto(): RemoteAccessRequestDto =
            RemoteAccessRequestDto.fromJson(payloadJsonWithAccountAndDevice())

        private suspend fun OutboxOperationEntity.toExtraTimeGrantDto(): RemoteExtraTimeGrantDto =
            RemoteExtraTimeGrantDto.fromJson(payloadJsonWithAccount())

        private suspend fun OutboxOperationEntity.payloadJsonWithAccount(): JSONObject {
            val json = JSONObject(payload)
            val activation = deviceActivationDao.latest()
            if (json.isNull("account_id") && activation != null) {
                json.put("account_id", activation.accountId)
            }
            return json
        }

        private suspend fun OutboxOperationEntity.payloadJsonWithAccountAndDevice(): JSONObject {
            val json = payloadJsonWithAccount()
            val activation = deviceActivationDao.latest()
            if (json.isNull("device_id") && activation != null) {
                json.put("device_id", activation.deviceId)
            }
            return json
        }

        private companion object {
            const val PoliciesTable = "policies"
            const val PolicyRulesTable = "policy_rules"
            const val AccessRequestsTable = "access_requests"
            const val ExtraTimeGrantsTable = "extra_time_grants"
            const val LogTag = "OutboxProcessor"
        }
    }
