package com.contentfilter.feature.vpn.policy

import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.model.LicenseState
import com.contentfilter.core.domain.model.PolicyDecision
import com.contentfilter.core.domain.model.PolicyLevel
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicySnapshot
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.model.SearchEngineCatalog
import com.contentfilter.core.domain.model.SystemHealthSnapshot
import com.contentfilter.core.domain.model.UpdateState
import com.contentfilter.core.domain.model.WebNavigationPolicy
import com.contentfilter.core.policy.DefaultPolicyEngine
import com.contentfilter.feature.vpn.domainlist.DynamicDomainBlocklist
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class VpnDomainPolicyEvaluatorTest {
    private val domainBlocklist = FakeDomainBlocklist()
    private val evaluator =
        VpnDomainPolicyEvaluator(
            policyEngine = DefaultPolicyEngine(),
            clock = FixedVpnClock,
            domainBlocklist = domainBlocklist,
        )

    @Test
    fun `returns allow when no domain rule matches`() {
        val decision = evaluator.evaluate("example.com", snapshot(), activeHealth())

        assertIs<PolicyDecision.Allow>(decision)
    }

    @Test
    fun `returns block for blocked domain`() {
        val decision =
            evaluator.evaluate(
                domain = "blocked.example",
                snapshot =
                    snapshot(
                        rule("blocked.example", RuleAction.Block),
                    ),
                health = activeHealth(),
            )

        assertIs<PolicyDecision.Block>(decision)
    }

    @Test
    fun `matches subdomain rule`() {
        val decision =
            evaluator.evaluate(
                domain = "child.example.com",
                snapshot =
                    snapshot(
                        rule("example.com", RuleAction.Block),
                    ),
                health = activeHealth(),
            )

        assertIs<PolicyDecision.Block>(decision)
    }

    @Test
    fun `preserves safesearch extension flag`() {
        val decision =
            evaluator.evaluate(
                domain = "search.example",
                snapshot =
                    snapshot(
                        rule("search.example", RuleAction.Allow, safeSearchRequired = true),
                    ),
                health = activeHealth(),
            )

        val allow = assertIs<PolicyDecision.Allow>(decision)
        assertTrue(allow.safeSearchRequired)
    }

    @Test
    fun `blocks a domain supplied by the signed local list`() {
        domainBlocklist.blockedDomain = "dynamic.example"

        val decision = evaluator.evaluate("dynamic.example", snapshot(), activeHealth())

        assertIs<PolicyDecision.Block>(decision)
    }

    @Test
    fun `technical Google host is allowed before local list`() {
        domainBlocklist.blockedDomains +=
            setOf(
                "google.com",
                "clients4.google.com",
                "clientservices.googleapis.com",
                "fonts.googleapis.com",
                "gstatic.com",
                "googleusercontent.com",
                "forcesafesearch.google.com",
            )

        domainBlocklist.blockedDomains.forEach { host ->
            assertIs<PolicyDecision.Allow>(evaluator.evaluate(host, snapshot(), activeHealth()), host)
        }
    }

    @Test
    fun `explicit allow rule wins over local list`() {
        domainBlocklist.blockedDomain = "adult.example"

        val decision =
            evaluator.evaluate(
                "adult.example",
                snapshot(rule("adult.example", RuleAction.Allow)),
                activeHealth(),
            )

        assertIs<PolicyDecision.Allow>(decision)
    }

    @Test
    fun `manual external block keeps priority`() {
        val decision =
            evaluator.evaluate(
                "manual-block.example",
                snapshot(rule("manual-block.example", RuleAction.Block)),
                activeHealth(),
            )

        assertIs<PolicyDecision.Block>(decision)
    }

    @Test
    fun `manual search host block keeps priority over technical allowlist`() {
        val decision =
            evaluator.evaluate(
                "google.com",
                snapshot(rule("google.com", RuleAction.Block)),
                activeHealth(),
            )

        assertIs<PolicyDecision.Block>(decision)
    }

    @Test
    fun `open mode allows safe external domain and blocks UT1 adult and DEV canary`() {
        domainBlocklist.blockedDomains += setOf("adult.example", "coca.com")

        assertIs<PolicyDecision.Allow>(evaluator.evaluate("safe.example", snapshot(), activeHealth()))
        assertIs<PolicyDecision.Block>(evaluator.evaluate("adult.example", snapshot(), activeHealth()))
        assertIs<PolicyDecision.Block>(evaluator.evaluate("coca.com", snapshot(), activeHealth()))
    }

    @Test
    fun `all supported engines keep SafeSearch in open mode`() {
        SearchEngineCatalog.engines.forEach { engine ->
            engine.domains.forEach { host ->
                val decision = assertIs<PolicyDecision.Allow>(evaluator.evaluate(host, snapshot(), activeHealth()))
                assertTrue(decision.safeSearchRequired, host)
            }
        }
    }

    @Test
    fun `only results keeps search host allowed and external host blocked`() {
        val onlyResultsRule =
            rule(WebNavigationPolicy.ExternalSearchResultsAllowedTarget, RuleAction.Allow).copy(enabled = false)

        assertIs<PolicyDecision.Allow>(
            evaluator.evaluate(
                "google.com",
                snapshot(onlyResultsRule),
                activeHealth(),
            ),
        )
        assertIs<PolicyDecision.Block>(
            evaluator.evaluate(
                "external.example",
                snapshot(onlyResultsRule),
                activeHealth(),
            ),
        )
    }

    private fun snapshot(vararg rules: PolicyRule): PolicySnapshot =
        PolicySnapshot(
            id = "test",
            version = 1L,
            rules = rules.toList(),
        )

    private fun rule(
        target: String,
        action: RuleAction,
        safeSearchRequired: Boolean = false,
        priority: Int = 0,
    ): PolicyRule =
        PolicyRule(
            id = "rule-$target",
            level = PolicyLevel.Device,
            scope = RuleScope.Domain,
            target = target,
            action = action,
            priority = priority,
            enabled = true,
            safeSearchRequired = safeSearchRequired,
        )

    private fun activeHealth(): SystemHealthSnapshot =
        SystemHealthSnapshot(
            vpnState = ComponentState.Enabled,
            accessibilityState = ComponentState.Enabled,
            syncState = ComponentState.Enabled,
            integrityState = ComponentState.Enabled,
            databaseState = ComponentState.Enabled,
            licenseState = LicenseState.Active,
            updateState = UpdateState.Current,
            checkedAtEpochMillis = FixedTime,
        )

    private object FixedVpnClock : VpnClock {
        override fun nowEpochMillis(): Long = FixedTime

        override fun minuteOfDay(epochMillis: Long): Int = 12 * 60
    }

    private class FakeDomainBlocklist : DynamicDomainBlocklist {
        val blockedDomains = mutableSetOf<String>()

        var blockedDomain: String?
            get() = blockedDomains.firstOrNull()
            set(value) {
                blockedDomains.clear()
                if (value != null) blockedDomains += value
            }

        override fun categoryFor(domain: String): String? = "adult".takeIf { domain in blockedDomains }
    }

    private companion object {
        const val FixedTime = 1_735_689_600_000L
    }
}
