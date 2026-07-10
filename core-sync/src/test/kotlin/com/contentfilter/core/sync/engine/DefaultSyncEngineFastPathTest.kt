package com.contentfilter.core.sync.engine

import com.contentfilter.core.database.dao.SyncCursorDao
import com.contentfilter.core.database.entity.SyncCursorEntity
import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.model.DeviceActivation
import com.contentfilter.core.domain.model.LicenseState
import com.contentfilter.core.domain.model.PolicyMutationReceipt
import com.contentfilter.core.domain.model.SystemHealthSnapshot
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.domain.repository.SystemStatusRepository
import com.contentfilter.core.network.dto.RemoteAccessRequestDto
import com.contentfilter.core.network.dto.RemoteAccountDto
import com.contentfilter.core.network.dto.RemoteAppGroupAppDto
import com.contentfilter.core.network.dto.RemoteAppGroupDto
import com.contentfilter.core.network.dto.RemoteDailyLimitDto
import com.contentfilter.core.network.dto.RemoteDeviceDto
import com.contentfilter.core.network.dto.RemoteExtraTimeGrantDto
import com.contentfilter.core.network.dto.RemotePolicyDto
import com.contentfilter.core.network.dto.RemotePolicyRuleDto
import com.contentfilter.core.network.remote.RemoteAccountRepository
import com.contentfilter.core.network.remote.RemoteDeviceRepository
import com.contentfilter.core.network.remote.RemoteLimitRepository
import com.contentfilter.core.network.remote.RemotePolicyRepository
import com.contentfilter.core.network.remote.RemoteRequestRepository
import com.contentfilter.core.network.remote.RemoteResult
import com.contentfilter.core.sync.outbox.OutboxBatchResult
import com.contentfilter.core.sync.outbox.OutboxProcessor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultSyncEngineFastPathTest {
    @Test
    fun `targeted pull does not query unrelated tables or process outbox`() =
        runBlocking {
            val outbox = FakeOutboxProcessor()
            val policies = FakePolicyRepository()
            val limits = FakeLimitRepository()
            val accounts = FakeAccountRepository()
            val requests = FakeRequestRepository()
            val applier = FakeRemoteApplier()
            val engine = engine(outbox, accounts, policies, limits, requests, applier)

            val result =
                engine.pullPolicyRevision(
                    requestId = "request-1",
                    deviceId = DeviceId,
                    policyId = PolicyId,
                    minimumRevision = Revision,
                    reason = "test",
                )

            assertTrue(result.success)
            assertEquals(1, policies.policyByIdCalls)
            assertEquals(1, policies.rulesForPolicyCalls)
            assertEquals(3, limits.targetedCalls)
            assertEquals(0, accounts.pullCalls)
            assertEquals(0, requests.pullCalls)
            assertEquals(0, outbox.pendingCalls)
            assertEquals(1, applier.policyBundleCalls)
        }

    @Test
    fun `long full sync does not block policy fast push`() =
        runBlocking {
            val outbox = FakeOutboxProcessor(blockGeneralSync = true)
            val engine =
                engine(
                    outbox = outbox,
                    accounts = FakeAccountRepository(),
                    policies = FakePolicyRepository(),
                    limits = FakeLimitRepository(),
                    requests = FakeRequestRepository(),
                    applier = FakeRemoteApplier(),
                )
            val fullSync = async { engine.syncCoreDataFull() }
            outbox.generalSyncStarted.await()

            val fastResult =
                withTimeout(500L) {
                    engine.syncPolicyChanges(receipt())
                }

            assertTrue(fastResult.serverConfirmed)
            assertEquals(1, outbox.mutationCalls)
            fullSync.cancelAndJoin()
        }

    private fun engine(
        outbox: OutboxProcessor,
        accounts: RemoteAccountRepository,
        policies: RemotePolicyRepository,
        limits: RemoteLimitRepository,
        requests: RemoteRequestRepository,
        applier: RemoteApplier,
    ): DefaultSyncEngine =
        DefaultSyncEngine(
            outboxProcessor = outbox,
            accountRepository = accounts,
            deviceRepository = FakeDeviceRepository(),
            policyRepository = policies,
            limitRepository = limits,
            requestRepository = requests,
            syncCursorDao = FakeSyncCursorDao(),
            applier = applier,
            systemStatusRepository = FakeSystemStatusRepository(),
            deviceActivationRepository = FakeActivationRepository(),
        )

    private fun receipt(): PolicyMutationReceipt =
        PolicyMutationReceipt("request-1", DeviceId, PolicyId, Revision, listOf("policy"))

    private class FakeOutboxProcessor(
        private val blockGeneralSync: Boolean = false,
    ) : OutboxProcessor {
        val generalSyncStarted = CompletableDeferred<Unit>()
        val releaseGeneralSync = CompletableDeferred<Unit>()
        var pendingCalls = 0
        var mutationCalls = 0

        override suspend fun processPending() {
            pendingCalls++
            generalSyncStarted.complete(Unit)
            if (blockGeneralSync) releaseGeneralSync.await()
        }

        override suspend fun processPolicyMutation(receipt: PolicyMutationReceipt): OutboxBatchResult {
            mutationCalls++
            return OutboxBatchResult(true, true, receipt.revision, emptyList())
        }
    }

    private class FakePolicyRepository : RemotePolicyRepository {
        var policyByIdCalls = 0
        var rulesForPolicyCalls = 0

        override suspend fun pullPolicies(updatedAfterIso: String?) = RemoteResult.Success(emptyList<RemotePolicyDto>())

        override suspend fun pullPolicyRules(updatedAfterIso: String?) =
            RemoteResult.Success(emptyList<RemotePolicyRuleDto>())

        override suspend fun pullPoliciesForDevice(deviceId: String) = RemoteResult.Success(listOf(policy()))

        override suspend fun pullPolicyById(policyId: String): RemoteResult<List<RemotePolicyDto>> {
            policyByIdCalls++
            return RemoteResult.Success(listOf(policy()))
        }

        override suspend fun pullPolicyRulesForPolicy(policyId: String): RemoteResult<List<RemotePolicyRuleDto>> {
            rulesForPolicyCalls++
            return RemoteResult.Success(emptyList())
        }

        override suspend fun upsertPolicy(policy: RemotePolicyDto) = RemoteResult.Success(Unit)

        override suspend fun upsertPolicyRule(rule: RemotePolicyRuleDto) = RemoteResult.Success(Unit)

        override suspend fun notifyPolicyChanged(
            requestId: String,
            deviceId: String,
            policyId: String,
            revision: Long,
        ) = RemoteResult.Success(Unit)
    }

    private class FakeLimitRepository : RemoteLimitRepository {
        var targetedCalls = 0

        override suspend fun pullDailyLimits(updatedAfterIso: String?) =
            RemoteResult.Success(emptyList<RemoteDailyLimitDto>())

        override suspend fun pullDailyLimitsForPolicy(policyId: String): RemoteResult<List<RemoteDailyLimitDto>> {
            targetedCalls++
            return RemoteResult.Success(emptyList())
        }

        override suspend fun upsertDailyLimit(limit: RemoteDailyLimitDto) = RemoteResult.Success(Unit)

        override suspend fun pullAppGroups(updatedAfterIso: String?) =
            RemoteResult.Success(emptyList<RemoteAppGroupDto>())

        override suspend fun pullAppGroupsForDevice(deviceId: String): RemoteResult<List<RemoteAppGroupDto>> {
            targetedCalls++
            return RemoteResult.Success(emptyList())
        }

        override suspend fun pullAppGroupApps(updatedAfterIso: String?) =
            RemoteResult.Success(emptyList<RemoteAppGroupAppDto>())

        override suspend fun pullAppGroupAppsForDevice(deviceId: String): RemoteResult<List<RemoteAppGroupAppDto>> {
            targetedCalls++
            return RemoteResult.Success(emptyList())
        }

        override suspend fun upsertAppGroup(group: RemoteAppGroupDto) = RemoteResult.Success(Unit)

        override suspend fun upsertAppGroupApp(app: RemoteAppGroupAppDto) = RemoteResult.Success(Unit)
    }

    private class FakeAccountRepository : RemoteAccountRepository {
        var pullCalls = 0

        override suspend fun pullAccounts(updatedAfterIso: String?): RemoteResult<List<RemoteAccountDto>> {
            pullCalls++
            return RemoteResult.Success(emptyList())
        }
    }

    private class FakeRequestRepository : RemoteRequestRepository {
        var pullCalls = 0

        override suspend fun pullAccessRequests(updatedAfterIso: String?): RemoteResult<List<RemoteAccessRequestDto>> {
            pullCalls++
            return RemoteResult.Success(emptyList())
        }

        override suspend fun pullExtraTimeGrants(
            updatedAfterIso: String?,
        ): RemoteResult<List<RemoteExtraTimeGrantDto>> {
            pullCalls++
            return RemoteResult.Success(emptyList())
        }

        override suspend fun upsertAccessRequest(request: RemoteAccessRequestDto) = RemoteResult.Success(Unit)

        override suspend fun upsertExtraTimeGrant(grant: RemoteExtraTimeGrantDto) = RemoteResult.Success(Unit)
    }

    private class FakeDeviceRepository : RemoteDeviceRepository {
        override suspend fun pullDevices(updatedAfterIso: String?) = RemoteResult.Success(emptyList<RemoteDeviceDto>())

        override suspend fun pullDevice(deviceId: String) = RemoteResult.Success(emptyList<RemoteDeviceDto>())

        override suspend fun markDeviceSeen(
            deviceId: String,
            health: SystemHealthSnapshot?,
        ) = RemoteResult.Success(Unit)

        override suspend fun acknowledgePolicyApplied(
            deviceId: String,
            policyId: String,
            revision: Long,
        ) = RemoteResult.Success(Unit)
    }

    private class FakeRemoteApplier : RemoteApplier {
        var policyBundleCalls = 0

        override suspend fun applyPolicyBundle(
            policy: RemotePolicyDto,
            rules: List<RemotePolicyRuleDto>,
            limits: List<RemoteDailyLimitDto>,
            groups: List<RemoteAppGroupDto>,
            groupApps: List<RemoteAppGroupAppDto>,
        ): Boolean {
            policyBundleCalls++
            return true
        }

        override suspend fun applyAccounts(values: List<RemoteAccountDto>) = Unit

        override suspend fun applyPolicies(values: List<RemotePolicyDto>) = Unit

        override suspend fun applyDevices(values: List<RemoteDeviceDto>) = Unit

        override suspend fun applyPolicyRules(values: List<RemotePolicyRuleDto>) = Unit

        override suspend fun applyDailyLimits(values: List<RemoteDailyLimitDto>) = Unit

        override suspend fun applyAppGroups(values: List<RemoteAppGroupDto>) = Unit

        override suspend fun applyAppGroupApps(values: List<RemoteAppGroupAppDto>) = Unit

        override suspend fun applyAccessRequests(values: List<RemoteAccessRequestDto>) = Unit

        override suspend fun applyExtraTimeGrants(values: List<RemoteExtraTimeGrantDto>) = Unit
    }

    private class FakeSyncCursorDao : SyncCursorDao {
        override suspend fun cursorFor(tableName: String): SyncCursorEntity? = null

        override suspend fun upsert(cursor: SyncCursorEntity) = Unit

        override suspend fun deleteAll() = Unit
    }

    private class FakeActivationRepository : DeviceActivationRepository {
        override fun observeActivation(): Flow<DeviceActivation?> = flowOf(null)

        override suspend fun currentActivation(): DeviceActivation? = null

        override suspend fun saveActivation(activation: DeviceActivation) = Unit
    }

    private class FakeSystemStatusRepository : SystemStatusRepository {
        override fun observeHealth(): Flow<SystemHealthSnapshot> = flowOf(error("unused"))

        override suspend fun currentHealth(): SystemHealthSnapshot = error("unused")

        override suspend fun updateVpnState(state: ComponentState) = Unit

        override suspend fun updateAccessibilityState(state: ComponentState) = Unit

        override suspend fun updateSyncState(state: ComponentState) = Unit

        override suspend fun updateLicenseState(state: LicenseState) = Unit
    }

    private companion object {
        const val DeviceId = "device-1"
        const val PolicyId = "policy-1"
        const val Revision = 200L

        fun policy(): RemotePolicyDto =
            RemotePolicyDto(
                id = PolicyId,
                accountId = "account-1",
                deviceId = DeviceId,
                version = Revision,
                active = true,
                updatedAt = "2026-07-10T00:00:00Z",
                deletedAt = null,
            )
    }
}
