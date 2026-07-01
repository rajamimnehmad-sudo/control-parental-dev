package com.contentfilter.core.data

import com.contentfilter.core.database.dao.PolicyDao
import com.contentfilter.core.database.dao.OutboxOperationDao
import com.contentfilter.core.database.dao.DeviceActivationDao
import com.contentfilter.core.database.dao.ExtraTimeGrantDao
import com.contentfilter.core.database.entity.OutboxOperationEntity
import com.contentfilter.core.database.entity.PolicyEntity
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicySnapshot
import com.contentfilter.core.domain.repository.PolicyRepository
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class RoomPolicyRepository
    @Inject
    constructor(
        private val policyDao: PolicyDao,
        private val outboxDao: OutboxOperationDao,
        private val deviceActivationDao: DeviceActivationDao,
        private val extraTimeGrantDao: ExtraTimeGrantDao,
    ) : PolicyRepository {
        @OptIn(ExperimentalCoroutinesApi::class)
        override fun observeActivePolicy(): Flow<PolicySnapshot> =
            policyDao.observeActivePolicy().flatMapLatest { policy ->
                val activeGrants = extraTimeGrantDao.observeActive(System.currentTimeMillis())
                if (policy == null) {
                    activeGrants.map { grants ->
                        PolicySnapshot(
                            id = "local-default",
                            version = 1L,
                            rules = emptyList(),
                            extraTimeGrants = grants.map { it.toDomain() },
                        )
                    }
                } else {
                    combine(policyDao.observeRulesForPolicy(policy.id), activeGrants) { rules, grants ->
                        PolicySnapshot(
                            id = policy.id,
                            version = policy.version,
                            rules = rules.map { it.toDomain() },
                            extraTimeGrants = grants.map { it.toDomain() },
                        )
                    }
                }
            }

        override suspend fun getActivePolicy(): PolicySnapshot {
            val policy = policyDao.activePolicy()
            val grants = extraTimeGrantDao.active(System.currentTimeMillis()).map { it.toDomain() }
            return if (policy == null) {
                PolicySnapshot(id = "local-default", version = 1L, rules = emptyList(), extraTimeGrants = grants)
            } else {
                PolicySnapshot(
                    id = policy.id,
                    version = policy.version,
                    rules = policyDao.rulesForPolicy(policy.id).map { it.toDomain() },
                    extraTimeGrants = grants,
                )
            }
        }

        override suspend fun saveRule(rule: PolicyRule) {
            val currentPolicy = policyDao.activePolicy()
            if (currentPolicy != null && !currentPolicy.id.isRemoteCompatibleId()) {
                policyDao.deactivatePolicy(currentPolicy.id)
            }
            val policy = currentPolicy
                ?.takeIf { it.id.isRemoteCompatibleId() }
                ?: createDefaultPolicy()
            policyDao.upsertRule(rule.toEntity(policy.id))
            val accountId = deviceActivationDao.latest()?.accountId
            outboxDao.upsert(policy.toOutboxOperation(accountId))
            outboxDao.upsert(rule.toOutboxOperation(policy.id, accountId))
        }

        private suspend fun createDefaultPolicy(): PolicyEntity {
            val policy = PolicyEntity(
                id = UUID.randomUUID().toString(),
                version = System.currentTimeMillis(),
                active = true,
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
            policyDao.upsertPolicy(policy)
            return policy
        }

        private fun String.isRemoteCompatibleId(): Boolean =
            runCatching { UUID.fromString(this) }.isSuccess

        private fun PolicyEntity.toOutboxOperation(accountId: String?): OutboxOperationEntity {
            val now = System.currentTimeMillis()
            val payload = org.json.JSONObject()
                .put("id", id)
                .put("account_id", accountId)
                .put("version", version)
                .put("active", active)
                .put("updated_at", Instant.ofEpochMilli(updatedAtEpochMillis).toString())
                .toString()
            return outboxOperation(PoliciesTable, payload, now)
        }

        private fun PolicyRule.toOutboxOperation(
            policyId: String,
            accountId: String?,
        ): OutboxOperationEntity {
            val now = System.currentTimeMillis()
            val payload = org.json.JSONObject()
                .put("id", id)
                .put("account_id", accountId)
                .put("policy_id", policyId)
                .put("scope", scope.name)
                .put("target", target)
                .put("action", action.name)
                .put("priority", priority)
                .put("enabled", enabled)
                .put("updated_at", Instant.ofEpochMilli(now).toString())
                .toString()
            return outboxOperation(PolicyRulesTable, payload, now)
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
            const val PoliciesTable = "policies"
            const val PolicyRulesTable = "policy_rules"
            const val UpsertOperation = "Upsert"
            const val PendingStatus = "Pending"
        }
    }
