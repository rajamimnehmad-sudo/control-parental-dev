package com.contentfilter.core.domain.usecase.admin

import com.contentfilter.core.domain.model.AccessRequest
import com.contentfilter.core.domain.model.AccessRequestType
import com.contentfilter.core.domain.model.DailyLimit
import com.contentfilter.core.domain.model.PolicyLevel
import com.contentfilter.core.domain.model.PolicyMutationReceipt
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicySnapshot
import com.contentfilter.core.domain.model.PolicyTargetType
import com.contentfilter.core.domain.model.RequestStatus
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.model.WebNavigationPolicy
import com.contentfilter.core.domain.repository.AccessRequestRepository
import com.contentfilter.core.domain.repository.DailyLimitRepository
import com.contentfilter.core.domain.repository.PolicyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApproveAccessRequestUseCaseTest {
    @Test
    fun `app access approval disables block rule, saves allow rule and deletes app limit`() =
        runBlocking {
            val requestRepository = FakeAccessRequestRepository()
            val policyRepository =
                FakePolicyRepository(
                    PolicySnapshot(
                        id = "policy",
                        deviceId = "device-1",
                        version = 1L,
                        rules =
                            listOf(
                                PolicyRule(
                                    id = "block-airbnb",
                                    level = PolicyLevel.Account,
                                    scope = RuleScope.App,
                                    target = AirbnbPackage,
                                    action = RuleAction.Block,
                                    priority = 10,
                                    enabled = true,
                                ),
                            ),
                    ),
                )
            val limitRepository =
                FakeDailyLimitRepository(
                    listOf(
                        DailyLimit(
                            id = "limit-airbnb",
                            targetType = PolicyTargetType.App,
                            target = AirbnbPackage,
                            limitMinutes = 2,
                            enabled = true,
                        ),
                        DailyLimit(
                            id = "limit-other",
                            targetType = PolicyTargetType.App,
                            target = "com.example.other",
                            limitMinutes = 2,
                            enabled = true,
                        ),
                    ),
                )
            val useCase = ApproveAccessRequestUseCase(requestRepository, policyRepository, limitRepository)

            useCase(appAccessRequest())

            assertFalse(policyRepository.savedRules.first { it.id == "block-airbnb" }.enabled)
            assertTrue(
                policyRepository.savedRules.any {
                    it.scope == RuleScope.App &&
                        it.target == AirbnbPackage &&
                        it.action == RuleAction.Allow &&
                        it.enabled
                },
            )
            assertEquals(listOf("limit-airbnb"), limitRepository.deletedIds)
            assertEquals(RequestStatus.Approved, requestRepository.updatedStatus)
        }

    @Test
    fun `domain approval does not delete app limits`() =
        runBlocking {
            val requestRepository = FakeAccessRequestRepository()
            val policyRepository = FakePolicyRepository(dagPolicy())
            val limitRepository =
                FakeDailyLimitRepository(
                    listOf(
                        DailyLimit(
                            id = "limit-airbnb",
                            targetType = PolicyTargetType.App,
                            target = AirbnbPackage,
                            limitMinutes = 2,
                            enabled = true,
                        ),
                    ),
                )
            val useCase = ApproveAccessRequestUseCase(requestRepository, policyRepository, limitRepository)

            useCase(domainAccessRequest())

            assertEquals(emptyList(), limitRepository.deletedIds)
            assertTrue(
                policyRepository.savedRules.any {
                    it.target == WebNavigationPolicy.DagEnabledTarget && it.enabled
                },
            )
            assertTrue(
                policyRepository.savedRules.any {
                    it.scope == RuleScope.Domain && it.target == "airbnb.com" && it.action == RuleAction.Allow && it.enabled
                },
            )
            assertEquals(RequestStatus.Approved, requestRepository.updatedStatus)
        }

    @Test
    fun `domain approval reuses an existing allow rule`() =
        runBlocking {
            val requestRepository = FakeAccessRequestRepository()
            val existingRule =
                PolicyRule(
                    id = "existing-domain-allow",
                    level = PolicyLevel.Account,
                    scope = RuleScope.Domain,
                    target = "example.com",
                    action = RuleAction.Allow,
                    priority = 10,
                    enabled = false,
                )
            val policyRepository =
                FakePolicyRepository(
                    dagPolicy().copy(rules = dagPolicy().rules + existingRule),
                )
            val useCase =
                ApproveAccessRequestUseCase(requestRepository, policyRepository, FakeDailyLimitRepository(emptyList()))

            useCase(domainAccessRequest(target = "example.com"))

            val savedAllow = policyRepository.savedRules.single { it.target == "example.com" }
            assertEquals("existing-domain-allow", savedAllow.id)
            assertTrue(savedAllow.enabled)
            assertEquals(1_000, savedAllow.priority)
            assertTrue(
                policyRepository.savedRules.any {
                    it.target == WebNavigationPolicy.DagEnabledTarget && it.enabled
                },
            )
        }

    @Test
    fun `domain approval refuses to replace an incomplete policy`() =
        runBlocking {
            val requestRepository = FakeAccessRequestRepository()
            val policyRepository = FakePolicyRepository(emptyPolicy())
            val useCase =
                ApproveAccessRequestUseCase(requestRepository, policyRepository, FakeDailyLimitRepository(emptyList()))

            assertFailsWith<IllegalArgumentException> { useCase(domainAccessRequest()) }

            assertTrue(policyRepository.savedRules.isEmpty())
            assertEquals(null, requestRepository.updatedStatus)
        }

    private fun appAccessRequest(): AccessRequest =
        AccessRequest(
            id = "request-1",
            requestType = AccessRequestType.APP_ACCESS,
            targetType = PolicyTargetType.App,
            target = AirbnbPackage,
            targetPackageName = AirbnbPackage,
            targetDomain = null,
            reason = "",
            requestedMinutes = null,
            status = RequestStatus.PendingRemote,
            createdAtEpochMillis = 1L,
            expiresAtEpochMillis = null,
            deviceId = "device-1",
        )

    private fun domainAccessRequest(target: String = "airbnb.com"): AccessRequest =
        AccessRequest(
            id = "request-2",
            requestType = AccessRequestType.DOMAIN_ACCESS,
            targetType = PolicyTargetType.Domain,
            target = target,
            targetPackageName = null,
            targetDomain = target,
            reason = "",
            requestedMinutes = null,
            status = RequestStatus.PendingRemote,
            createdAtEpochMillis = 1L,
            expiresAtEpochMillis = null,
            deviceId = "device-1",
        )

    private fun emptyPolicy(): PolicySnapshot =
        PolicySnapshot(
            id = "policy",
            deviceId = "device-1",
            version = 1L,
            rules = emptyList(),
        )

    private fun dagPolicy(): PolicySnapshot =
        emptyPolicy().copy(
            rules =
                listOf(
                    PolicyRule(
                        id = "dag-enabled",
                        level = PolicyLevel.Account,
                        scope = RuleScope.Domain,
                        target = WebNavigationPolicy.DagEnabledTarget,
                        action = RuleAction.Allow,
                        priority = WebNavigationPolicy.RulePriority,
                        enabled = true,
                    ),
                ),
        )

    private class FakeAccessRequestRepository : AccessRequestRepository {
        var updatedStatus: RequestStatus? = null

        override fun observeRequests(): Flow<List<AccessRequest>> = flowOf(emptyList())

        override fun observePendingRequests(): Flow<List<AccessRequest>> = flowOf(emptyList())

        override suspend fun saveRequest(request: AccessRequest) = Unit

        override suspend fun updateStatus(
            requestId: String,
            status: RequestStatus,
        ) {
            updatedStatus = status
        }
    }

    private class FakePolicyRepository(
        private val snapshot: PolicySnapshot,
    ) : PolicyRepository {
        val savedRules = mutableListOf<PolicyRule>()

        override fun observeActivePolicy(deviceId: String?): Flow<PolicySnapshot> = flowOf(snapshot)

        override suspend fun getActivePolicy(deviceId: String?): PolicySnapshot = snapshot

        override suspend fun saveRule(
            rule: PolicyRule,
            deviceId: String?,
        ) {
            savedRules += rule
        }

        override suspend fun saveRules(
            rules: List<PolicyRule>,
            deviceId: String?,
            requestId: String?,
        ): PolicyMutationReceipt {
            savedRules += rules
            return receipt(deviceId, requestId)
        }

        override suspend fun deleteRule(rule: PolicyRule) = Unit

        private fun receipt(
            deviceId: String?,
            requestId: String?,
        ) = PolicyMutationReceipt(
            requestId = requestId ?: "request",
            deviceId = deviceId ?: "device-1",
            policyId = snapshot.id,
            revision = snapshot.version,
            operationIds = emptyList(),
        )
    }

    private class FakeDailyLimitRepository(
        private val limits: List<DailyLimit>,
    ) : DailyLimitRepository {
        val deletedIds = mutableListOf<String>()

        override fun observeLimits(deviceId: String?): Flow<List<DailyLimit>> = flowOf(limits)

        override suspend fun saveLimit(
            limit: DailyLimit,
            deviceId: String?,
            requestId: String?,
        ) = receipt(deviceId, requestId)

        override suspend fun deleteLimit(
            limit: DailyLimit,
            deviceId: String?,
            requestId: String?,
        ): PolicyMutationReceipt {
            deletedIds += limit.id
            return receipt(deviceId, requestId)
        }

        private fun receipt(
            deviceId: String?,
            requestId: String?,
        ) = PolicyMutationReceipt(
            requestId = requestId ?: "request",
            deviceId = deviceId ?: "device-1",
            policyId = "policy",
            revision = 1L,
            operationIds = emptyList(),
        )
    }

    private companion object {
        const val AirbnbPackage = "com.airbnb.android"
    }
}
