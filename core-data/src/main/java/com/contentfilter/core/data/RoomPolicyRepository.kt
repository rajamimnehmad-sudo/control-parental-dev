package com.contentfilter.core.data

import android.util.Log
import androidx.room.withTransaction
import com.contentfilter.core.database.AppDatabase
import com.contentfilter.core.database.dao.DeviceActivationDao
import com.contentfilter.core.database.dao.ExtraTimeGrantDao
import com.contentfilter.core.database.dao.OutboxOperationDao
import com.contentfilter.core.database.dao.PolicyDao
import com.contentfilter.core.database.entity.OutboxOperationEntity
import com.contentfilter.core.database.entity.PolicyEntity
import com.contentfilter.core.domain.model.PolicyMutationReceipt
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicySnapshot
import com.contentfilter.core.domain.repository.PolicyRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class RoomPolicyRepository
    @Inject
    constructor(
        private val database: AppDatabase,
        private val policyDao: PolicyDao,
        private val outboxDao: OutboxOperationDao,
        private val deviceActivationDao: DeviceActivationDao,
        private val extraTimeGrantDao: ExtraTimeGrantDao,
    ) : PolicyRepository {
        @OptIn(ExperimentalCoroutinesApi::class)
        override fun observeActivePolicy(deviceId: String?): Flow<PolicySnapshot> =
            activeDeviceIdFlow(deviceId)
                .flatMapLatest { activeDeviceId -> activePolicyFlow(activeDeviceId) }
                .flatMapLatest { policy ->
                    val activeGrants = extraTimeGrantDao.observeActive(System.currentTimeMillis())
                    if (policy == null) {
                        activeGrants.map { grants ->
                            PolicySnapshot(
                                id = "local-default",
                                deviceId = deviceId,
                                version = 1L,
                                rules = emptyList(),
                                extraTimeGrants = grants.map { it.toDomain() },
                            )
                        }
                    } else {
                        combine(policyDao.observeRulesForPolicy(policy.id), activeGrants) { rules, grants ->
                            PolicySnapshot(
                                id = policy.id,
                                deviceId = policy.deviceId,
                                version = policy.version,
                                rules = rules.map { it.toDomain() },
                                extraTimeGrants = grants.map { it.toDomain() },
                            )
                        }
                    }
                }

        override suspend fun getActivePolicy(deviceId: String?): PolicySnapshot {
            val activeDeviceId = deviceId ?: deviceActivationDao.latest()?.deviceId
            val policy = activePolicy(activeDeviceId)
            val rules = policy?.let { policyDao.rulesForPolicy(it.id) }.orEmpty()
            val grants = extraTimeGrantDao.active(System.currentTimeMillis()).map { it.toDomain() }
            return if (policy == null) {
                PolicySnapshot(
                    id = "local-default",
                    deviceId = activeDeviceId,
                    version = 1L,
                    rules = emptyList(),
                    extraTimeGrants = grants,
                )
            } else {
                PolicySnapshot(
                    id = policy.id,
                    deviceId = policy.deviceId,
                    version = policy.version,
                    rules = rules.map { it.toDomain() },
                    extraTimeGrants = grants,
                )
            }
        }

        override suspend fun saveRule(
            rule: PolicyRule,
            deviceId: String?,
        ) {
            saveRules(listOf(rule), deviceId)
        }

        override suspend fun saveRules(
            rules: List<PolicyRule>,
            deviceId: String?,
            requestId: String?,
        ): PolicyMutationReceipt {
            val effectiveRequestId = requestId ?: UUID.randomUUID().toString()
            if (rules.isEmpty()) {
                val current = getActivePolicy(deviceId)
                return PolicyMutationReceipt(
                    requestId = effectiveRequestId,
                    deviceId = current.deviceId ?: requireNotNull(deviceId),
                    policyId = current.id,
                    revision = current.version,
                    operationIds = emptyList(),
                )
            }
            val activation = deviceActivationDao.latest()
            val targetDeviceId = requireNotNull(deviceId ?: activation?.deviceId)
            val accountId = activation?.accountId
            var storedRevision = 0L
            var storedPolicyId = ""
            var operationIds = emptyList<String>()
            database.withTransaction {
                val currentPolicy = activePolicy(targetDeviceId)
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
                        ?: createDefaultPolicy(targetDeviceId, revision)
                val ruleEntities =
                    rules.mapIndexed { index, rule ->
                        rule.toEntity(policy.id, revision + index + 1L)
                    }
                policyDao.upsertPolicy(policy)
                policyDao.upsertRules(ruleEntities)
                val operations =
                    buildPolicyOutboxOperations(
                        policy = policy,
                        rules = rules,
                        accountId = accountId,
                        revision = revision,
                        requestId = effectiveRequestId,
                        deviceId = targetDeviceId,
                    )
                operations.forEach { operation ->
                    outboxDao.upsert(operation)
                }
                storedRevision = revision
                storedPolicyId = policy.id
                operationIds = operations.map { it.id }
            }
            Log.i(
                LogTag,
                "policy outbox generated requestId=$effectiveRequestId deviceId=${targetDeviceId.take(8)} " +
                    "policyId=${storedPolicyId.take(8)} policyRevision=$storedRevision " +
                    "policyParent=1 dependentRules=${rules.size}",
            )
            return PolicyMutationReceipt(
                requestId = effectiveRequestId,
                deviceId = targetDeviceId,
                policyId = storedPolicyId,
                revision = storedRevision,
                operationIds = operationIds,
            )
        }

        override suspend fun deleteRule(rule: PolicyRule) {
            val policy =
                policyDao.policyIdForRule(rule.id)
                    ?.let { policyDao.policyById(it) }
                    ?: return
            policyDao.deleteRuleById(rule.id)
            val accountId = deviceActivationDao.latest()?.accountId
            outboxDao.upsert(rule.toDeletedOutboxOperation(policy.id, accountId))
        }

        private suspend fun createDefaultPolicy(
            deviceId: String?,
            now: Long,
        ): PolicyEntity {
            return PolicyEntity(
                id = UUID.randomUUID().toString(),
                deviceId = deviceId,
                version = now,
                active = true,
                updatedAtEpochMillis = now,
            )
        }

        private fun activeDeviceIdFlow(deviceId: String?): Flow<String?> =
            if (deviceId != null) {
                flowOf(deviceId)
            } else {
                deviceActivationDao.observeLatest().map { it?.deviceId }
            }

        private fun activePolicyFlow(deviceId: String?): Flow<PolicyEntity?> =
            if (deviceId != null) {
                policyDao.observeActivePolicyForDevice(deviceId)
            } else {
                policyDao.observeActivePolicy()
            }

        private suspend fun activePolicy(deviceId: String?): PolicyEntity? =
            if (deviceId != null) {
                policyDao.activePolicyForDevice(deviceId)
            } else {
                policyDao.activePolicy()
            }

        private fun String.isRemoteCompatibleId(): Boolean = runCatching { UUID.fromString(this) }.isSuccess

        private companion object {
            const val LogTag = "RoomPolicyRepository"
        }
    }

internal fun buildPolicyOutboxOperations(
    policy: PolicyEntity,
    rules: List<PolicyRule>,
    accountId: String?,
    revision: Long,
    requestId: String,
    deviceId: String,
): List<OutboxOperationEntity> =
    buildList {
        val plan = buildPolicyOutboxPlan(rules.size, revision)
        add(
            policy.toOutboxOperation(
                accountId = accountId,
                now = plan.first().createdAtEpochMillis,
                requestId = requestId,
                deviceId = deviceId,
                revision = revision,
            ),
        )
        rules.forEachIndexed { index, rule ->
            val entry = plan[index + 1]
            add(
                rule.toOutboxOperation(
                    policyId = policy.id,
                    accountId = accountId,
                    now = entry.createdAtEpochMillis,
                    requestId = requestId,
                    deviceId = deviceId,
                    revision = revision,
                ),
            )
        }
    }

internal fun buildPolicyOutboxPlan(
    ruleCount: Int,
    revision: Long,
): List<PolicyOutboxEntry> =
    listOf(PolicyOutboxEntry(POLICIES_TABLE, revision)) +
        List(ruleCount) { index ->
            PolicyOutboxEntry(POLICY_RULES_TABLE, revision + index + 1L)
        }

internal data class PolicyOutboxEntry(
    val tableName: String,
    val createdAtEpochMillis: Long,
)

private fun PolicyEntity.toOutboxOperation(
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
            .put("device_id", deviceId)
            .put("version", version)
            .put("active", active)
            .put("updated_at", Instant.ofEpochMilli(updatedAtEpochMillis).toString())
            .toString()
    return outboxOperation(
        tableName = POLICIES_TABLE,
        rowId = id,
        payload = payload,
        now = now,
        requestId = requestId,
        aggregateId = id,
        deviceId = deviceId,
        revision = revision,
        priority = POLICY_PARENT_PRIORITY,
    )
}

private fun PolicyRule.toOutboxOperation(
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
            .put("scope", scope.name)
            .put("target", target)
            .put("action", action.name)
            .put("priority", priority)
            .put("enabled", enabled)
            .put("active_window_start_minute", activeWindow?.startMinuteOfDay ?: org.json.JSONObject.NULL)
            .put("active_window_end_minute", activeWindow?.endMinuteOfDay ?: org.json.JSONObject.NULL)
            .put("active_days_mask", activeDaysMask)
            .put("updated_at", Instant.ofEpochMilli(now).toString())
            .toString()
    return outboxOperation(
        tableName = POLICY_RULES_TABLE,
        rowId = id,
        payload = payload,
        now = now,
        requestId = requestId,
        aggregateId = policyId,
        deviceId = deviceId,
        revision = revision,
        priority = POLICY_DEPENDENT_PRIORITY,
    )
}

private fun PolicyRule.toDeletedOutboxOperation(
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
            .put("scope", scope.name)
            .put("target", target)
            .put("action", action.name)
            .put("priority", priority)
            .put("enabled", false)
            .put("active_window_start_minute", activeWindow?.startMinuteOfDay ?: org.json.JSONObject.NULL)
            .put("active_window_end_minute", activeWindow?.endMinuteOfDay ?: org.json.JSONObject.NULL)
            .put("active_days_mask", activeDaysMask)
            .put("updated_at", deletedAt)
            .put("deleted_at", deletedAt)
            .toString()
    return outboxOperation(
        tableName = POLICY_RULES_TABLE,
        rowId = id,
        payload = payload,
        now = now,
        requestId = UUID.randomUUID().toString(),
        aggregateId = policyId,
        deviceId = "",
        revision = now,
        priority = POLICY_DEPENDENT_PRIORITY,
    )
}

private fun outboxOperation(
    tableName: String,
    rowId: String,
    payload: String,
    now: Long,
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
        deviceId = deviceId.takeIf { it.isNotBlank() },
        revision = revision,
        priority = priority,
    )

private const val POLICIES_TABLE = "policies"
private const val POLICY_RULES_TABLE = "policy_rules"
private const val UPSERT_OPERATION = "Upsert"
private const val PENDING_STATUS = "Pending"
private const val POLICY_PARENT_PRIORITY = 100
private const val POLICY_DEPENDENT_PRIORITY = 90
