package com.contentfilter.core.sync.engine

import com.contentfilter.core.database.dao.SyncCursorDao
import com.contentfilter.core.database.entity.SyncCursorEntity
import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.model.DeviceActivation
import com.contentfilter.core.domain.model.LicenseEntitlement
import com.contentfilter.core.domain.model.LicenseState
import com.contentfilter.core.domain.model.SystemHealthSnapshot
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.domain.repository.SystemStatusRepository
import com.contentfilter.core.network.config.DeviceTokenProvider
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
import com.contentfilter.core.network.remote.RemoteLicenseRepository
import com.contentfilter.core.network.remote.RemoteLimitRepository
import com.contentfilter.core.network.remote.RemotePolicyRepository
import com.contentfilter.core.network.remote.RemoteRequestRepository
import com.contentfilter.core.network.remote.RemoteResult
import com.contentfilter.core.sync.outbox.OutboxBatchResult
import com.contentfilter.core.sync.outbox.OutboxProcessor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultSyncEngineAtomicPolicySyncTest {
    @Test
    fun `revision N stays active when policy rules pull fails before bundle apply`(): Unit =
        runBlocking {
            val policies =
                FakePolicyRepository(
                    policiesResult = RemoteResult.Success(listOf(activePolicy(revision = 200L))),
                    policyRulesResult = RemoteResult.Failure(reason = "rules failed", retryable = true),
                )
            val limits =
                FakeLimitRepository(
                    dailyLimitsForPolicyResult =
                        RemoteResult.Success(
                            listOf(limit(policyId = PolicyId, revision = 6L)),
                        ),
                    appGroupsForDeviceResult =
                        RemoteResult.Success(
                            listOf(group(deviceId = DeviceId)),
                        ),
                    appGroupAppsForDeviceResult =
                        RemoteResult.Success(
                            listOf(groupApp(groupId = "group-1")),
                        ),
                )
            val applier = StatefulRemoteApplier(initialRevision = 100L)
            val engine =
                engine(
                    policies = policies,
                    limits = limits,
                    applier = applier,
                )

            val result = engine.syncCoreDataFull()

            assertFalse(result.success)
            assertEquals(100L, applier.activeRevision)
            assertEquals(0, applier.policyBundleCalls)
            assertEquals(1, policies.pullPolicyRulesForPolicyCalls)
            assertEquals(0, limits.pullDailyLimitsForPolicyCalls)
            assertEquals(0, limits.pullAppGroupsCalls)
            assertEquals(0, limits.pullAppGroupAppsCalls)
        }

    @Test
    fun `revision N stays active when activation deviceId is null`(): Unit =
        runBlocking {
            val policies =
                FakePolicyRepository(
                    policiesResult =
                        RemoteResult.Success(
                            listOf(
                                activePolicy(
                                    revision = 200L,
                                    deviceId = null,
                                ),
                            ),
                        ),
                    policyRulesResult = RemoteResult.Success(listOf(rule(policyId = PolicyId, revision = 5L))),
                )
            val limits =
                FakeLimitRepository(
                    dailyLimitsForPolicyResult =
                        RemoteResult.Success(
                            listOf(limit(policyId = PolicyId, revision = 6L)),
                        ),
                    appGroupsForDeviceResult =
                        RemoteResult.Success(
                            listOf(group(deviceId = DeviceId)),
                        ),
                    appGroupAppsForDeviceResult =
                        RemoteResult.Success(
                            listOf(groupApp(groupId = "group-1")),
                        ),
                )
            val applier = StatefulRemoteApplier(initialRevision = 100L)
            val engine =
                engine(
                    policies = policies,
                    limits = limits,
                    applier = applier,
                    activation = FakeActivationRepository(activation = null),
                )

            val result = engine.syncCoreDataFull()

            assertFalse(result.success)
            assertEquals(100L, applier.activeRevision)
            assertEquals(0, applier.policyBundleCalls)
            assertEquals(0, policies.pullPolicyRulesForPolicyCalls)
            assertEquals(0, limits.pullDailyLimitsForPolicyCalls)
            assertEquals(0, limits.pullAppGroupsCalls)
            assertEquals(0, limits.pullAppGroupAppsCalls)
            assertEquals(0, applier.policiesCalls)
        }

    @Test
    fun `revision N stays active when active policy has unresolved device target`(): Unit =
        runBlocking {
            val policies =
                FakePolicyRepository(
                    policiesResult =
                        RemoteResult.Success(
                            listOf(
                                activePolicy(
                                    revision = 200L,
                                    deviceId = "other-device",
                                ),
                            ),
                        ),
                    policyRulesResult = RemoteResult.Success(listOf(rule(policyId = PolicyId, revision = 5L))),
                )
            val limits = FakeLimitRepository()
            val applier = StatefulRemoteApplier(initialRevision = 100L)
            val engine =
                engine(
                    policies = policies,
                    limits = limits,
                    applier = applier,
                )

            val result = engine.syncCoreDataFull()

            assertFalse(result.success)
            assertEquals(100L, applier.activeRevision)
            assertEquals(0, applier.policyBundleCalls)
            assertEquals(0, policies.pullPolicyRulesForPolicyCalls)
            assertEquals(0, limits.pullDailyLimitsForPolicyCalls)
            assertEquals(0, limits.pullAppGroupsCalls)
            assertEquals(0, limits.pullAppGroupAppsCalls)
            assertEquals(0, applier.policiesCalls)
        }

    @Test
    fun `no active policy does not publish partial snapshot`(): Unit =
        runBlocking {
            val policies =
                FakePolicyRepository(
                    policiesResult =
                        RemoteResult.Success(
                            listOf(
                                inactivePolicy(
                                    revision = 200L,
                                    deviceId = DeviceId,
                                ),
                            ),
                        ),
                )
            val limits = FakeLimitRepository()
            val applier = StatefulRemoteApplier(initialRevision = 100L)
            val engine =
                engine(
                    policies = policies,
                    limits = limits,
                    applier = applier,
                )

            val result = engine.syncCoreDataFull()

            assertFalse(result.success)
            assertEquals(100L, applier.activeRevision)
            assertEquals(0, applier.policiesCalls)
            assertEquals(0, applier.policyBundleCalls)
            assertEquals(0, policies.pullPolicyRulesForPolicyCalls)
            assertEquals(0, limits.pullDailyLimitsForPolicyCalls)
        }

    @Test
    fun `revision N stays active when daily limits pull fails before groups`(): Unit =
        runBlocking {
            val policies =
                FakePolicyRepository(
                    policiesResult = RemoteResult.Success(listOf(activePolicy(revision = 200L))),
                    policyRulesResult = RemoteResult.Success(listOf(rule(policyId = PolicyId, revision = 5L))),
                )
            val limits =
                FakeLimitRepository(
                    dailyLimitsForPolicyResult = RemoteResult.Failure(reason = "limits failed", retryable = true),
                )
            val applier = StatefulRemoteApplier(initialRevision = 100L)
            val engine =
                engine(
                    policies = policies,
                    limits = limits,
                    applier = applier,
                )

            val result = engine.syncCoreDataFull()

            assertFalse(result.success)
            assertEquals(100L, applier.activeRevision)
            assertEquals(0, applier.policyBundleCalls)
            assertEquals(1, policies.pullPolicyRulesForPolicyCalls)
            assertEquals(1, limits.pullDailyLimitsForPolicyCalls)
            assertEquals(0, limits.pullAppGroupsCalls)
            assertEquals(0, limits.pullAppGroupAppsCalls)
        }

    @Test
    fun `revision N advances once when full bundle is applied`(): Unit =
        runBlocking {
            val policies =
                FakePolicyRepository(
                    policiesResult = RemoteResult.Success(listOf(activePolicy(revision = 200L))),
                    policyRulesResult = RemoteResult.Success(listOf(rule(policyId = PolicyId, revision = 5L))),
                )
            val limits =
                FakeLimitRepository(
                    dailyLimitsForPolicyResult =
                        RemoteResult.Success(
                            listOf(limit(policyId = PolicyId, revision = 6L)),
                        ),
                    appGroupsForDeviceResult = RemoteResult.Success(listOf(group(deviceId = DeviceId))),
                    appGroupAppsForDeviceResult = RemoteResult.Success(listOf(groupApp(groupId = "group-1"))),
                )
            val applier = StatefulRemoteApplier(initialRevision = 100L)
            val engine =
                engine(
                    policies = policies,
                    limits = limits,
                    applier = applier,
                )

            val result = engine.syncCoreDataFull()

            assertTrue(result.success)
            assertEquals(200L, applier.activeRevision)
            assertEquals(1, applier.policyBundleCalls)
            assertEquals(1, policies.pullPolicyRulesForPolicyCalls)
            assertEquals(1, limits.pullDailyLimitsForPolicyCalls)
            assertEquals(1, limits.pullAppGroupsCalls)
            assertEquals(1, limits.pullAppGroupAppsCalls)
        }

    @Test
    fun `retry succeeds when activation becomes resolvable`(): Unit =
        runBlocking {
            val activation = FakeActivationRepository(activation = null)
            val policies =
                FakePolicyRepository(
                    policiesResult =
                        RemoteResult.Success(
                            listOf(
                                activePolicy(
                                    revision = 200L,
                                    deviceId = null,
                                ),
                            ),
                        ),
                    policyRulesResult = RemoteResult.Success(listOf(rule(policyId = PolicyId, revision = 5L))),
                )
            val limits = FakeLimitRepository()
            val applier = StatefulRemoteApplier(initialRevision = 100L)
            val engine =
                engine(
                    policies = policies,
                    limits = limits,
                    applier = applier,
                    activation = activation,
                )

            val first = engine.syncCoreDataFull()
            assertFalse(first.success)
            assertEquals(100L, applier.activeRevision)
            assertEquals(0, applier.policyBundleCalls)
            assertEquals(0, policies.pullPolicyRulesForPolicyCalls)
            assertEquals(0, limits.pullDailyLimitsForPolicyCalls)

            activation.setActivation(DeviceId)
            policies.policiesResult = RemoteResult.Success(listOf(activePolicy(revision = 200L)))
            val second = engine.syncCoreDataFull()
            assertTrue(second.success)
            assertEquals(200L, applier.activeRevision)
            assertEquals(1, applier.policyBundleCalls)
            assertEquals(1, policies.pullPolicyRulesForPolicyCalls)
            assertEquals(1, limits.pullDailyLimitsForPolicyCalls)
            assertEquals(1, limits.pullAppGroupsCalls)
            assertEquals(1, limits.pullAppGroupAppsCalls)
        }

    @Test
    fun `bundle does not mix revision old children`(): Unit =
        runBlocking {
            val policies =
                FakePolicyRepository(
                    policiesResult = RemoteResult.Success(listOf(activePolicy(revision = 200L))),
                    policyRulesResult = RemoteResult.Success(listOf(rule(policyId = "policy-old", revision = 5L))),
                )
            val limits =
                FakeLimitRepository(
                    dailyLimitsForPolicyResult =
                        RemoteResult.Success(
                            listOf(limit(policyId = PolicyId, revision = 6L)),
                        ),
                    appGroupsForDeviceResult = RemoteResult.Success(listOf(group(deviceId = DeviceId))),
                    appGroupAppsForDeviceResult = RemoteResult.Success(listOf(groupApp(groupId = "group-1"))),
                )
            val applier = StatefulRemoteApplier(initialRevision = 100L)
            val engine =
                engine(
                    policies = policies,
                    limits = limits,
                    applier = applier,
                )

            val result = engine.syncCoreDataFull()

            assertFalse(result.success)
            assertEquals(100L, applier.activeRevision)
            assertEquals(0, applier.policyBundleCalls)
        }

    @Test
    fun `revision change during bundle pull preserves last known good and cursors`(): Unit =
        runBlocking {
            val policies =
                FakePolicyRepository(
                    policiesResult = RemoteResult.Success(listOf(activePolicy(revision = 200L))),
                    policyRulesResult = RemoteResult.Success(listOf(rule(policyId = PolicyId, revision = 5L))),
                    policyByIdResult = RemoteResult.Success(listOf(activePolicy(revision = 201L))),
                )
            val limits = FakeLimitRepository()
            val applier = StatefulRemoteApplier(initialRevision = 100L)
            val cursors = FakeSyncCursorDao()
            val engine = engine(policies, limits, applier, cursors = cursors)

            val result = engine.syncCoreDataFull()

            assertFalse(result.success)
            assertEquals(100L, applier.activeRevision)
            assertEquals(0, applier.policyBundleCalls)
            assertTrue(cursors.upserts.isEmpty())
        }

    private fun engine(
        policies: FakePolicyRepository,
        limits: FakeLimitRepository,
        applier: StatefulRemoteApplier,
        activation: FakeActivationRepository = FakeActivationRepository(),
        cursors: FakeSyncCursorDao = FakeSyncCursorDao(),
    ): DefaultSyncEngine =
        DefaultSyncEngine(
            outboxProcessor = FakeOutboxProcessor(),
            accountRepository = FakeAccountRepository(),
            deviceRepository = FakeDeviceRepository(),
            policyRepository = policies,
            limitRepository = limits,
            licenseRepository = FakeLicenseRepository(),
            requestRepository = FakeRequestRepository(),
            syncCursorDao = cursors,
            applier = applier,
            systemStatusRepository = FakeSystemStatusRepository(),
            deviceActivationRepository = activation,
            deviceTokenProvider = FakeDeviceTokenProvider(),
        )

    private class FakeDeviceRepository : RemoteDeviceRepository {
        override suspend fun pullDevices(updatedAfterIso: String?) = RemoteResult.Success(emptyList<RemoteDeviceDto>())

        override suspend fun pullDevice(
            deviceId: String,
        ): RemoteResult<List<com.contentfilter.core.network.dto.RemoteDeviceDto>> = RemoteResult.Success(emptyList())

        override suspend fun markDeviceSeen(
            deviceId: String,
            health: SystemHealthSnapshot?,
        ) = RemoteResult.Success(Unit)

        override suspend fun updateAppVersion(
            deviceId: String,
            appVersionCode: Int,
        ) = RemoteResult.Success(Unit)

        override suspend fun acknowledgePolicyApplied(
            deviceId: String,
            policyId: String,
            revision: Long,
        ) = RemoteResult.Success(Unit)

        override suspend fun completeOwnRelink() = RemoteResult.Success(Unit)
    }

    private class FakePolicyRepository(
        var policiesResult: RemoteResult<List<RemotePolicyDto>> = RemoteResult.Success(emptyList()),
        var policyRulesResult: RemoteResult<List<RemotePolicyRuleDto>> = RemoteResult.Success(emptyList()),
        var policyByIdResult: RemoteResult<List<RemotePolicyDto>>? = null,
    ) : RemotePolicyRepository {
        var pullPoliciesCalls = 0
        var pullPolicyRulesForPolicyCalls = 0

        override suspend fun pullPolicies(updatedAfterIso: String?): RemoteResult<List<RemotePolicyDto>> {
            pullPoliciesCalls++
            return policiesResult
        }

        override suspend fun pullPolicyRules(updatedAfterIso: String?): RemoteResult<List<RemotePolicyRuleDto>> =
            RemoteResult.Success(emptyList())

        override suspend fun pullPoliciesForDevice(deviceId: String): RemoteResult<List<RemotePolicyDto>> {
            return policiesResult
        }

        override suspend fun pullPolicyById(policyId: String): RemoteResult<List<RemotePolicyDto>> =
            policyByIdResult ?: policiesResult

        override suspend fun pullPolicyRulesForPolicy(policyId: String): RemoteResult<List<RemotePolicyRuleDto>> {
            pullPolicyRulesForPolicyCalls++
            return policyRulesResult
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

    private class FakeLimitRepository(
        var dailyLimitsForPolicyResult: RemoteResult<List<RemoteDailyLimitDto>> = RemoteResult.Success(emptyList()),
        var appGroupsForDeviceResult: RemoteResult<List<RemoteAppGroupDto>> = RemoteResult.Success(emptyList()),
        var appGroupAppsForDeviceResult: RemoteResult<List<RemoteAppGroupAppDto>> = RemoteResult.Success(emptyList()),
    ) : RemoteLimitRepository {
        var pullDailyLimitsForPolicyCalls = 0
        var pullAppGroupsCalls = 0
        var pullAppGroupAppsCalls = 0
        var pullDailyLimitsForPolicySequence: MutableList<RemoteResult<List<RemoteDailyLimitDto>>>? = null

        override suspend fun pullDailyLimits(updatedAfterIso: String?): RemoteResult<List<RemoteDailyLimitDto>> =
            RemoteResult.Success(
                emptyList(),
            )

        override suspend fun pullDailyLimitsForPolicy(policyId: String): RemoteResult<List<RemoteDailyLimitDto>> {
            pullDailyLimitsForPolicyCalls++
            return if (pullDailyLimitsForPolicySequence != null && pullDailyLimitsForPolicySequence!!.isNotEmpty()) {
                pullDailyLimitsForPolicySequence!!.removeAt(0)
            } else {
                dailyLimitsForPolicyResult
            }
        }

        override suspend fun upsertDailyLimit(limit: RemoteDailyLimitDto) = RemoteResult.Success(Unit)

        override suspend fun pullAppGroups(updatedAfterIso: String?): RemoteResult<List<RemoteAppGroupDto>> =
            RemoteResult.Success(emptyList())

        override suspend fun pullAppGroupsForDevice(deviceId: String): RemoteResult<List<RemoteAppGroupDto>> {
            pullAppGroupsCalls++
            return appGroupsForDeviceResult
        }

        override suspend fun pullAppGroupApps(updatedAfterIso: String?): RemoteResult<List<RemoteAppGroupAppDto>> =
            RemoteResult.Success(emptyList())

        override suspend fun pullAppGroupAppsForDevice(deviceId: String): RemoteResult<List<RemoteAppGroupAppDto>> {
            pullAppGroupAppsCalls++
            return appGroupAppsForDeviceResult
        }

        override suspend fun upsertAppGroup(group: RemoteAppGroupDto) = RemoteResult.Success(Unit)

        override suspend fun upsertAppGroupApp(app: RemoteAppGroupAppDto) = RemoteResult.Success(Unit)
    }

    private class FakeAccountRepository : RemoteAccountRepository {
        override suspend fun pullAccounts(updatedAfterIso: String?): RemoteResult<List<RemoteAccountDto>> =
            RemoteResult.Success(emptyList())
    }

    private class FakeRequestRepository : RemoteRequestRepository {
        override suspend fun pullAccessRequests(updatedAfterIso: String?): RemoteResult<List<RemoteAccessRequestDto>> =
            RemoteResult.Success(emptyList())

        override suspend fun pullExtraTimeGrants(
            updatedAfterIso: String?,
        ): RemoteResult<List<RemoteExtraTimeGrantDto>> = RemoteResult.Success(emptyList())

        override suspend fun upsertAccessRequest(request: RemoteAccessRequestDto) = RemoteResult.Success(Unit)

        override suspend fun upsertExtraTimeGrant(grant: RemoteExtraTimeGrantDto) = RemoteResult.Success(Unit)
    }

    private class StatefulRemoteApplier(initialRevision: Long) : RemoteApplier {
        var policyBundleCalls = 0
        var policiesCalls = 0
        var activeRevision = initialRevision

        override suspend fun applyPolicyBundle(
            policy: RemotePolicyDto,
            rules: List<RemotePolicyRuleDto>,
            limits: List<RemoteDailyLimitDto>,
            groups: List<RemoteAppGroupDto>,
            groupApps: List<RemoteAppGroupAppDto>,
        ): Boolean {
            policyBundleCalls++
            if (rules.any { it.policyId != policy.id } || limits.any { it.policyId != policy.id }) {
                return false
            }
            if (policy.active && policy.deletedAt == null) {
                activeRevision = policy.version
            }
            return true
        }

        override suspend fun applyAccounts(values: List<RemoteAccountDto>) = Unit

        override suspend fun applyPolicies(values: List<RemotePolicyDto>) {
            policiesCalls += values.size
        }

        override suspend fun applyDevices(values: List<RemoteDeviceDto>) = Unit

        override suspend fun applyPolicyRules(values: List<RemotePolicyRuleDto>) = Unit

        override suspend fun applyDailyLimits(values: List<RemoteDailyLimitDto>) = Unit

        override suspend fun applyAppGroups(values: List<RemoteAppGroupDto>) = Unit

        override suspend fun applyAppGroupApps(values: List<RemoteAppGroupAppDto>) = Unit

        override suspend fun applyAccessRequests(values: List<RemoteAccessRequestDto>) = Unit

        override suspend fun applyExtraTimeGrants(values: List<RemoteExtraTimeGrantDto>) = Unit
    }

    private class FakeOutboxProcessor : OutboxProcessor {
        override suspend fun processPending() = Unit

        override suspend fun processPolicyMutation(
            receipt: com.contentfilter.core.domain.model.PolicyMutationReceipt,
        ): OutboxBatchResult =
            OutboxBatchResult(
                serverConfirmed = true,
                notificationDelivered = true,
                revision = receipt.revision,
                pendingOperationIds = emptyList(),
            )
    }

    private class FakeSyncCursorDao : SyncCursorDao {
        val upserts = mutableListOf<SyncCursorEntity>()

        override suspend fun cursorFor(tableName: String): SyncCursorEntity? = null

        override suspend fun upsert(cursor: SyncCursorEntity) {
            upserts += cursor
        }

        override suspend fun deleteAll() = Unit
    }

    private class FakeActivationRepository(
        private var activation: DeviceActivation? =
            DeviceActivation("activation", "account-1", DeviceId, 1L),
    ) : DeviceActivationRepository {
        override fun observeActivation(): Flow<DeviceActivation?> {
            return flowOf(activation)
        }

        override suspend fun currentActivation(): DeviceActivation? {
            return activation
        }

        override suspend fun saveActivation(activation: DeviceActivation) = Unit

        fun setActivation(deviceId: String?) {
            activation = deviceId?.let { DeviceActivation("activation", "account-1", deviceId, 1L) }
        }
    }

    private class FakeLicenseRepository : RemoteLicenseRepository {
        override suspend fun getDeviceEntitlement(deviceId: String) =
            RemoteResult.Success(
                LicenseEntitlement(
                    state = LicenseState.Active,
                    startsAtEpochMillis = null,
                    expiresAtEpochMillis = null,
                    verifiedAtEpochMillis = 0L,
                ),
            )
    }

    private class FakeDeviceTokenProvider : DeviceTokenProvider {
        override fun currentDeviceToken(): String? = null

        override fun saveDeviceToken(token: String) = Unit

        override fun isDeviceRelinkPending(): Boolean = false

        override fun markDeviceRelinkPending() = Unit

        override fun clearDeviceRelinkPending() = Unit

        override fun clearDeviceToken() = Unit
    }

    private class FakeSystemStatusRepository : SystemStatusRepository {
        override fun observeHealth(): Flow<SystemHealthSnapshot> =
            flowOf(
                SystemHealthSnapshot(
                    vpnState = ComponentState.Enabled,
                    accessibilityState = ComponentState.Enabled,
                    deviceAdminState = ComponentState.Enabled,
                    syncState = ComponentState.Enabled,
                    integrityState = ComponentState.Enabled,
                    databaseState = ComponentState.Enabled,
                    licenseState = LicenseState.Active,
                    updateState = com.contentfilter.core.domain.model.UpdateState.Unknown,
                    checkedAtEpochMillis = 0L,
                ),
            )

        override suspend fun currentHealth(): SystemHealthSnapshot =
            SystemHealthSnapshot(
                vpnState = ComponentState.Enabled,
                accessibilityState = ComponentState.Enabled,
                deviceAdminState = ComponentState.Enabled,
                syncState = ComponentState.Enabled,
                integrityState = ComponentState.Enabled,
                databaseState = ComponentState.Enabled,
                licenseState = LicenseState.Active,
                updateState = com.contentfilter.core.domain.model.UpdateState.Unknown,
                checkedAtEpochMillis = 0L,
            )

        override suspend fun updateVpnState(state: ComponentState) = Unit

        override suspend fun updateAccessibilityState(state: ComponentState) = Unit

        override suspend fun updateDeviceAdminState(state: ComponentState) = Unit

        override suspend fun updateSyncState(state: ComponentState) = Unit

        override suspend fun updateLicenseState(state: LicenseState) = Unit

        override suspend fun updateLicenseEntitlement(entitlement: LicenseEntitlement) = Unit

        override suspend fun refreshLicenseState() = Unit
    }

    private companion object {
        const val DeviceId = "device-1"
        const val PolicyId = "policy-1"

        fun activePolicy(
            revision: Long,
            deviceId: String? = DeviceId,
        ): RemotePolicyDto =
            RemotePolicyDto(
                id = PolicyId,
                accountId = "account-1",
                deviceId = deviceId,
                version = revision,
                active = true,
                updatedAt = "2026-07-10T00:00:00Z",
                deletedAt = null,
            )

        fun inactivePolicy(
            revision: Long,
            deviceId: String? = DeviceId,
        ): RemotePolicyDto =
            RemotePolicyDto(
                id = PolicyId,
                accountId = "account-1",
                deviceId = deviceId,
                version = revision,
                active = false,
                updatedAt = "2026-07-10T00:00:00Z",
                deletedAt = null,
            )

        fun rule(
            policyId: String,
            revision: Long,
        ): RemotePolicyRuleDto =
            RemotePolicyRuleDto(
                id = "rule-$revision",
                accountId = "account-1",
                policyId = policyId,
                scope = "Domain",
                target = "target.com",
                action = "Block",
                priority = 1,
                enabled = true,
                updatedAt = "2026-07-10T00:00:00Z",
                deletedAt = null,
            )

        fun limit(
            policyId: String,
            revision: Long,
        ): RemoteDailyLimitDto =
            RemoteDailyLimitDto(
                id = "limit-$revision",
                accountId = "account-1",
                policyId = policyId,
                targetType = "App",
                target = "com.app",
                limitMinutes = 10,
                enabled = true,
                updatedAt = "2026-07-10T00:00:00Z",
                deletedAt = null,
            )

        fun group(deviceId: String): RemoteAppGroupDto =
            RemoteAppGroupDto(
                id = "group-1",
                accountId = "account-1",
                deviceId = deviceId,
                name = "group",
                color = "blue",
                limitMinutes = 10,
                resetMinuteOfDay = 10,
                enabled = true,
                updatedAt = "2026-07-10T00:00:00Z",
                deletedAt = null,
            )

        fun groupApp(groupId: String): RemoteAppGroupAppDto =
            RemoteAppGroupAppDto(
                id = "app-1",
                accountId = "account-1",
                deviceId = DeviceId,
                groupId = groupId,
                packageName = "com.sample",
                enabled = true,
                updatedAt = "2026-07-10T00:00:00Z",
                deletedAt = null,
            )
    }
}
