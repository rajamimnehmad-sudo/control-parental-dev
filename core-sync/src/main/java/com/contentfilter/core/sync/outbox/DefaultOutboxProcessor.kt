package com.contentfilter.core.sync.outbox

import android.util.Log
import com.contentfilter.core.database.dao.DeviceActivationDao
import com.contentfilter.core.database.dao.OutboxOperationDao
import com.contentfilter.core.database.entity.OutboxOperationEntity
import com.contentfilter.core.domain.model.PolicyMutationReceipt
import com.contentfilter.core.network.dto.RemoteAccessRequestDto
import com.contentfilter.core.network.dto.RemoteAppGroupAppDto
import com.contentfilter.core.network.dto.RemoteAppGroupDto
import com.contentfilter.core.network.dto.RemoteDailyLimitDto
import com.contentfilter.core.network.dto.RemoteExtraTimeGrantDto
import com.contentfilter.core.network.dto.RemotePolicyDto
import com.contentfilter.core.network.dto.RemotePolicyRuleDto
import com.contentfilter.core.network.remote.RemoteLimitRepository
import com.contentfilter.core.network.remote.RemotePolicyRepository
import com.contentfilter.core.network.remote.RemoteRequestRepository
import com.contentfilter.core.network.remote.RemoteResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.time.Instant
import javax.inject.Inject

class DefaultOutboxProcessor
    @Inject
    constructor(
        private val outboxDao: OutboxOperationDao,
        private val deviceActivationDao: DeviceActivationDao,
        private val requestRepository: RemoteRequestRepository,
        private val policyRepository: RemotePolicyRepository,
        private val limitRepository: RemoteLimitRepository,
    ) : OutboxProcessor {
        private val pushMutex = Mutex()

        override suspend fun processPending() {
            val operations = outboxDao.pending(limit = MaxOperationsPerRun)
            if (operations.isNotEmpty()) {
                Log.i(LogTag, "Processing pending outbox operations count=${operations.size}")
            }
            val plan = compactOutboxOperations(operations)
            plan.superseded.forEach { markSuperseded(it) }
            val touchedAggregates = linkedSetOf<String>()
            plan.pending.sortedWith(OutboxPriorityComparator).forEach { operation ->
                pushMutex.withLock {
                    processCurrent(operation.id)
                }
                operation.aggregateId?.let(touchedAggregates::add)
            }
            touchedAggregates.forEach { aggregateId ->
                notifyCompletedAggregate(aggregateId)
            }
        }

        override suspend fun processPolicyMutation(receipt: PolicyMutationReceipt): OutboxBatchResult =
            pushMutex.withLock {
                val startedAt = System.currentTimeMillis()
                var lastFailure: RemoteResult.Failure? = null
                for (pass in 0 until MaxMutationPasses) {
                    val matching = pendingCriticalOperations(receipt.policyId)
                    if (matching.isEmpty()) break
                    val plan = compactOutboxOperations(matching)
                    plan.superseded.forEach { markSuperseded(it) }
                    for (operation in plan.pending.sortedWith(OutboxPriorityComparator)) {
                        val result = processCurrent(operation.id)
                        if (result is RemoteResult.Failure) {
                            lastFailure = result
                            break
                        }
                    }
                    if (lastFailure != null || pendingCriticalOperations(receipt.policyId).isEmpty()) break
                }
                val pending = pendingCriticalOperations(receipt.policyId)
                val latest = outboxDao.latestPolicyOperation(receipt.policyId)
                val effectiveRevision = maxOf(receipt.revision, latest?.revision ?: 0L)
                if (pending.isNotEmpty() || lastFailure != null) {
                    Log.w(
                        LogTag,
                        "policy fast path pending requestId=${receipt.requestId} " +
                            "deviceId=${receipt.deviceId.take(8)} policyId=${receipt.policyId.take(8)} " +
                            "revision=$effectiveRevision durationMs=${System.currentTimeMillis() - startedAt} " +
                            "pending=${pending.size} error=${lastFailure?.reason}",
                    )
                    return@withLock OutboxBatchResult(
                        serverConfirmed = false,
                        notificationDelivered = false,
                        revision = effectiveRevision,
                        pendingOperationIds = pending.map { it.id },
                        error = lastFailure?.reason,
                    )
                }
                val notification =
                    policyRepository.notifyPolicyChanged(
                        requestId = latest?.requestId ?: receipt.requestId,
                        deviceId = latest?.deviceId ?: receipt.deviceId,
                        policyId = receipt.policyId,
                        revision = effectiveRevision,
                    )
                val notified = notification is RemoteResult.Success
                if (!notified && latest != null) {
                    markNotificationPending(latest)
                }
                Log.i(
                    LogTag,
                    "policy fast path confirmed requestId=${receipt.requestId} " +
                        "deviceId=${receipt.deviceId.take(8)} policyId=${receipt.policyId.take(8)} " +
                        "revision=$effectiveRevision durationMs=${System.currentTimeMillis() - startedAt} " +
                        "notificationDelivered=$notified operations=${receipt.operationIds.size}",
                )
                OutboxBatchResult(
                    serverConfirmed = true,
                    notificationDelivered = notified,
                    revision = effectiveRevision,
                    pendingOperationIds = if (notified) emptyList() else listOfNotNull(latest?.id),
                    error = (notification as? RemoteResult.Failure)?.reason,
                )
            }

        private suspend fun pendingCriticalOperations(policyId: String): List<OutboxOperationEntity> =
            filterPolicyMutationOperations(outboxDao.pendingForTables(CriticalPolicyTables), policyId)

        private suspend fun processCurrent(operationId: String): RemoteResult<Unit> {
            val operation =
                outboxDao.byId(operationId)
                    ?.takeIf { it.status == OutboxStatus.Pending.name }
                    ?: return RemoteResult.Success(Unit)
            val result =
                runCatching { push(operation) }
                    .getOrElse { exception ->
                        Log.e(
                            LogTag,
                            "Outbox push crashed id=${operation.id} table=${operation.tableName}: ${exception.message}",
                            exception,
                        )
                        RemoteResult.Failure("Outbox payload could not be pushed.", retryable = false)
                    }
            val nextStatus = nextOutboxStatus(result)
            outboxDao.updateStatusIfCurrent(
                id = operation.id,
                expectedUpdatedAt = operation.updatedAtEpochMillis,
                status = nextStatus.name,
                attemptCount = operation.attemptCount + 1,
                updatedAt = System.currentTimeMillis(),
            )
            val resultLabel = if (result is RemoteResult.Success) "succeeded" else "failed"
            Log.i(
                LogTag,
                "Outbox push $resultLabel requestId=${operation.requestId ?: "legacy"} " +
                    "id=${operation.id} table=${operation.tableName} revision=${operation.revision}",
            )
            return result
        }

        private suspend fun markSuperseded(operation: OutboxOperationEntity) {
            outboxDao.updateStatusIfCurrent(
                id = operation.id,
                expectedUpdatedAt = operation.updatedAtEpochMillis,
                status = OutboxStatus.Superseded.name,
                attemptCount = operation.attemptCount,
                updatedAt = System.currentTimeMillis(),
            )
        }

        private suspend fun notifyCompletedAggregate(aggregateId: String) {
            if (pendingCriticalOperations(aggregateId).isNotEmpty()) return
            val latest = outboxDao.latestPolicyOperation(aggregateId) ?: return
            val requestId = latest.requestId ?: return
            val deviceId = latest.deviceId ?: return
            val revision = latest.revision ?: return
            val result = policyRepository.notifyPolicyChanged(requestId, deviceId, aggregateId, revision)
            if (result is RemoteResult.Failure) markNotificationPending(latest)
        }

        private suspend fun markNotificationPending(operation: OutboxOperationEntity) {
            outboxDao.updateStatusIfCurrent(
                id = operation.id,
                expectedUpdatedAt = operation.updatedAtEpochMillis,
                status = OutboxStatus.Pending.name,
                attemptCount = operation.attemptCount + 1,
                updatedAt = System.currentTimeMillis(),
            )
        }

        private suspend fun push(operation: OutboxOperationEntity): RemoteResult<Unit> =
            when (operation.tableName) {
                PoliciesTable -> policyRepository.upsertPolicy(operation.toPolicyDto())
                PolicyRulesTable -> policyRepository.upsertPolicyRule(operation.toPolicyRuleDto())
                DailyLimitsTable -> limitRepository.upsertDailyLimit(operation.toDailyLimitDto())
                AppGroupsTable -> limitRepository.upsertAppGroup(operation.toAppGroupDto())
                AppGroupAppsTable -> limitRepository.upsertAppGroupApp(operation.toAppGroupAppDto())
                AccessRequestsTable -> requestRepository.upsertAccessRequest(operation.toAccessRequestDto())
                ExtraTimeGrantsTable -> requestRepository.upsertExtraTimeGrant(operation.toExtraTimeGrantDto())
                else -> RemoteResult.Failure("Unsupported outbox table ${operation.tableName}.", retryable = false)
            }

        private suspend fun OutboxOperationEntity.toPolicyDto(): RemotePolicyDto =
            RemotePolicyDto.fromJson(payloadJsonWithAccount())

        private suspend fun OutboxOperationEntity.toPolicyRuleDto(): RemotePolicyRuleDto =
            RemotePolicyRuleDto.fromJson(payloadJsonWithAccount())

        private suspend fun OutboxOperationEntity.toDailyLimitDto(): RemoteDailyLimitDto =
            RemoteDailyLimitDto.fromJson(payloadJsonWithAccount())

        private suspend fun OutboxOperationEntity.toAppGroupDto(): RemoteAppGroupDto =
            RemoteAppGroupDto.fromJson(payloadJsonWithAccountAndDevice())

        private suspend fun OutboxOperationEntity.toAppGroupAppDto(): RemoteAppGroupAppDto =
            RemoteAppGroupAppDto.fromJson(payloadJsonWithAccountAndDevice())

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
            const val DailyLimitsTable = "daily_limits"
            const val AppGroupsTable = "app_groups"
            const val AppGroupAppsTable = "app_group_apps"
            const val AccessRequestsTable = "access_requests"
            const val ExtraTimeGrantsTable = "extra_time_grants"
            const val LogTag = "OutboxProcessor"
            const val MaxOperationsPerRun = 250
            const val MaxMutationPasses = 3
            val CriticalPolicyTables =
                listOf(PoliciesTable, PolicyRulesTable, DailyLimitsTable, AppGroupsTable, AppGroupAppsTable)
        }
    }

internal data class OutboxCompactionPlan(
    val pending: List<OutboxOperationEntity>,
    val superseded: List<OutboxOperationEntity>,
)

internal fun compactOutboxOperations(operations: List<OutboxOperationEntity>): OutboxCompactionPlan {
    val selected = mutableListOf<OutboxOperationEntity>()
    val superseded = mutableListOf<OutboxOperationEntity>()
    operations
        .groupBy { operation -> "${operation.tableName}:${operation.payloadRowId()}" }
        .values
        .forEach { duplicates ->
            val newest = duplicates.maxWithOrNull(compareByRevision) ?: return@forEach
            selected += newest
            superseded += duplicates.filterNot { it === newest }
        }
    return OutboxCompactionPlan(pending = selected, superseded = superseded)
}

internal fun filterPolicyMutationOperations(
    operations: List<OutboxOperationEntity>,
    policyId: String,
): List<OutboxOperationEntity> =
    operations.filter { operation ->
        operation.aggregateId == policyId || operation.payloadAggregateId() == policyId
    }

internal fun nextOutboxStatus(result: RemoteResult<Unit>): OutboxStatus =
    when (result) {
        is RemoteResult.Success -> OutboxStatus.Synced
        is RemoteResult.Failure -> if (result.retryable) OutboxStatus.Pending else OutboxStatus.Failed
    }

internal val OutboxPriorityComparator: Comparator<OutboxOperationEntity> =
    compareBy<OutboxOperationEntity> { operation -> operation.tableOrder() }
        .thenByDescending { operation -> operation.effectiveRevision() }
        .thenBy { operation -> operation.createdAtEpochMillis }

private val compareByRevision: Comparator<OutboxOperationEntity> =
    compareBy<OutboxOperationEntity> { operation -> operation.effectiveRevision() }
        .thenBy { operation -> operation.updatedAtEpochMillis }

private fun OutboxOperationEntity.payloadRowId(): String =
    runCatching { JSONObject(payload).optString("id") }
        .getOrNull()
        .orEmpty()
        .ifBlank { id }

private fun OutboxOperationEntity.payloadAggregateId(): String? =
    runCatching {
        val json = JSONObject(payload)
        if (tableName == "policies") json.optString("id") else json.optString("policy_id")
    }.getOrNull()?.takeIf { it.isNotBlank() }

private fun OutboxOperationEntity.effectiveRevision(): Long =
    revision
        ?: runCatching {
            val json = JSONObject(payload)
            if (tableName == "policies") {
                json.optLong("version", 0L)
            } else {
                json.optString("updated_at").takeIf { it.isNotBlank() }?.let(Instant::parse)?.toEpochMilli() ?: 0L
            }
        }.getOrDefault(updatedAtEpochMillis)

private fun OutboxOperationEntity.tableOrder(): Int =
    when (tableName) {
        "policies" -> 0
        "policy_rules" -> 1
        "daily_limits" -> 2
        "app_groups" -> 3
        "app_group_apps" -> 4
        else -> 5
    }
