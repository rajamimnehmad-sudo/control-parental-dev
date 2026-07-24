package com.contentfilter.user.dag2

import com.contentfilter.core.domain.model.PolicyLevel
import com.contentfilter.core.domain.model.PolicyMutationReceipt
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicySnapshot
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.repository.PolicyRepository
import com.contentfilter.feature.vpn.domainlist.DynamicDomainBlocklist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class DagV2SitePolicyTest {
    @Test
    fun `adult prohibition wins over an ordinary admin allow`() =
        runBlocking {
            val allowRule =
                PolicyRule(
                    id = "allow-controlled-fixture",
                    level = PolicyLevel.Device,
                    scope = RuleScope.Domain,
                    target = DagV2SitePolicy.ControlledAdultFixtureDomain,
                    action = RuleAction.Allow,
                    priority = 10_000,
                    enabled = true,
                )
            val policy = DagV2SitePolicy(FakePolicyRepository(listOf(allowRule)), EmptyDomainList)

            val result = policy.evaluateNavigation("https://${DagV2SitePolicy.ControlledAdultFixtureDomain}/fixture")

            assertEquals(DagV2SiteDecision.Block, result.decision)
        }

    @Test
    fun `safe https domain is allowed when no policy blocks it`() =
        runBlocking {
            val policy = DagV2SitePolicy(FakePolicyRepository(emptyList()), EmptyDomainList)

            val result = policy.evaluateNavigation("https://example.com/products")

            assertEquals(DagV2SiteDecision.Allow, result.decision)
        }

    @Test
    fun `document without textual evidence fails closed`() {
        val policy = DagV2SitePolicy(FakePolicyRepository(emptyList()), EmptyDomainList)

        val result = policy.evaluateDocument("https://example.com", "", "")

        assertEquals(DagV2SiteDecision.Block, result.decision)
    }

    private class FakePolicyRepository(
        rules: List<PolicyRule>,
    ) : PolicyRepository {
        private val snapshot = PolicySnapshot("test", version = 1, rules = rules)

        override fun observeActivePolicy(deviceId: String?): Flow<PolicySnapshot> = flowOf(snapshot)

        override suspend fun getActivePolicy(deviceId: String?): PolicySnapshot = snapshot

        override suspend fun saveRule(
            rule: PolicyRule,
            deviceId: String?,
        ) = error("Not used")

        override suspend fun saveRules(
            rules: List<PolicyRule>,
            deviceId: String?,
            requestId: String?,
        ): PolicyMutationReceipt = error("Not used")

        override suspend fun deleteRule(rule: PolicyRule) = error("Not used")
    }

    private object EmptyDomainList : DynamicDomainBlocklist {
        override fun categoryFor(domain: String): String? = null
    }
}
