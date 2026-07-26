package com.contentfilter.user.dag2

import com.contentfilter.core.domain.model.PolicyLevel
import com.contentfilter.core.domain.model.PolicyMutationReceipt
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicySnapshot
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.repository.PolicyRepository
import com.contentfilter.feature.vpn.domainlist.DynamicDomainBlocklist
import com.contentfilter.feature.vpn.domainlist.WebDomainList
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
                    target = ControlledAdultFixtureDomain,
                    action = RuleAction.Allow,
                    priority = 10_000,
                    enabled = true,
                )
            val policy =
                DagV2SitePolicy(
                    FakePolicyRepository(listOf(allowRule)),
                    AdultDomainList,
                    AllowingCanonicalPolicy,
                )

            val result = policy.evaluateNavigation("https://$ControlledAdultFixtureDomain/fixture")

            assertEquals(DagV2SiteDecision.Block, result.decision)
        }

    @Test
    fun `safe https domain is allowed when no policy blocks it`() =
        runBlocking {
            val policy =
                DagV2SitePolicy(
                    FakePolicyRepository(emptyList()),
                    EmptyDomainList,
                    AllowingCanonicalPolicy,
                )

            val result = policy.evaluateNavigation("https://example.com/products")

            assertEquals(DagV2SiteDecision.Allow, result.decision)
        }

    @Test
    fun `ordinary admin allow retains precedence over a non adult domain category`() =
        runBlocking {
            val allowRule =
                PolicyRule(
                    id = "allow-non-adult-fixture",
                    level = PolicyLevel.Device,
                    scope = RuleScope.Domain,
                    target = ControlledGamblingFixtureDomain,
                    action = RuleAction.Allow,
                    priority = 10_000,
                    enabled = true,
                )
            val policy =
                DagV2SitePolicy(
                    FakePolicyRepository(listOf(allowRule)),
                    GamblingDomainList,
                    GamblingDomainCanonicalPolicy,
                )

            val result = policy.evaluateNavigation("https://$ControlledGamblingFixtureDomain/")

            assertEquals(DagV2SiteDecision.Allow, result.decision)
        }

    @Test
    fun `document without textual evidence fails closed`() {
        val policy =
            DagV2SitePolicy(
                FakePolicyRepository(emptyList()),
                EmptyDomainList,
                AllowingCanonicalPolicy,
            )

        val result = policy.evaluateDocument("https://example.com", "", "")

        assertEquals(DagV2SiteDecision.Block, result.decision)
    }

    @Test
    fun `uncertain query result document and route fail closed`() {
        val policy =
            DagV2SitePolicy(
                FakePolicyRepository(emptyList()),
                EmptyDomainList,
                UncertainCanonicalPolicy,
            )
        val uncertainResultPolicy =
            DagV2SitePolicy(
                FakePolicyRepository(emptyList()),
                EmptyDomainList,
                UncertainResultCanonicalPolicy,
            )

        assertEquals(DagV2SiteDecision.Block, policy.evaluateQuery("consulta ambigua").decision)
        runBlocking {
            assertEquals(
                DagV2SiteDecision.Block,
                uncertainResultPolicy
                    .evaluateResult(
                        DagV2SearchResult(
                            title = "Resultado ambiguo",
                            url = "https://example.com/route",
                            description = "Sin aprobación suficiente",
                        ),
                    ).decision,
            )
        }
        assertEquals(
            DagV2SiteDecision.Block,
            policy.evaluateDocument("https://example.com", "Title", "Text").decision,
        )
        assertEquals(DagV2SiteDecision.Block, policy.evaluateSpaRoute("https://example.com/route").decision)
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

    private object AdultDomainList : DynamicDomainBlocklist {
        override fun categoryFor(domain: String): String? =
            WebDomainList.CategoryAdult.takeIf { domain == ControlledAdultFixtureDomain }
    }

    private object GamblingDomainList : DynamicDomainBlocklist {
        override fun categoryFor(domain: String): String? =
            WebDomainList.CategoryGambling.takeIf { domain == ControlledGamblingFixtureDomain }
    }

    private object AllowingCanonicalPolicy : DagV2CanonicalTextPolicy {
        override fun classifyQuery(query: String) = allowed()

        override fun classifyNavigation(url: String) = allowed()

        override fun classifyResult(result: DagV2SearchResult) = allowed()

        override fun classifyPage(
            url: String,
            title: String,
            visibleText: String,
        ) = allowed()
    }

    private object UncertainCanonicalPolicy : DagV2CanonicalTextPolicy by AllowingCanonicalPolicy {
        override fun classifyQuery(query: String) = uncertain()

        override fun classifyNavigation(url: String) = uncertain()

        override fun classifyResult(result: DagV2SearchResult) = uncertain()

        override fun classifyPage(
            url: String,
            title: String,
            visibleText: String,
        ) = uncertain()
    }

    private object UncertainResultCanonicalPolicy : DagV2CanonicalTextPolicy by AllowingCanonicalPolicy {
        override fun classifyResult(result: DagV2SearchResult) = uncertain()
    }

    private object GamblingDomainCanonicalPolicy : DagV2CanonicalTextPolicy by AllowingCanonicalPolicy {
        override fun classifyNavigation(url: String) =
            DagV2CanonicalTextResult(
                DagV2CanonicalTextDecision.Blocked,
                WebDomainList.CategoryGambling,
            )
    }

    companion object {
        private const val ControlledAdultFixtureDomain = "controlled-adult.invalid"
        private const val ControlledGamblingFixtureDomain = "controlled-gambling.invalid"

        private fun allowed() = DagV2CanonicalTextResult(DagV2CanonicalTextDecision.Allowed, "test_allowed")

        private fun uncertain() = DagV2CanonicalTextResult(DagV2CanonicalTextDecision.Uncertain, "test_uncertain")
    }
}
