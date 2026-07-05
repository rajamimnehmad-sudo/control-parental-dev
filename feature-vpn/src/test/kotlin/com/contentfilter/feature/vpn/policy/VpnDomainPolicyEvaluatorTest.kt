package com.contentfilter.feature.vpn.policy

import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.model.LicenseState
import com.contentfilter.core.domain.model.PolicyDecision
import com.contentfilter.core.domain.model.PolicyLevel
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicySnapshot
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.model.SystemHealthSnapshot
import com.contentfilter.core.domain.model.UpdateState
import com.contentfilter.core.policy.DefaultPolicyEngine
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class VpnDomainPolicyEvaluatorTest {
    private val evaluator =
        VpnDomainPolicyEvaluator(
            policyEngine = DefaultPolicyEngine(),
            clock = FixedVpnClock,
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
    fun `blocks search engines when web is blocked and search engines are disabled`() {
        val snapshot = snapshot(rule("*", RuleAction.Block, priority = 10))

        SearchEngineDomains.forEach { domain ->
            val decision = evaluator.evaluate(domain, snapshot, activeHealth())

            assertIs<PolicyDecision.Block>(decision, "Expected $domain to be blocked")
        }
    }

    @Test
    fun `allows search engines but blocks result domains when web is blocked`() {
        val snapshot =
            snapshot(
                rule("*", RuleAction.Block, priority = 10),
                *SearchEngineDomains
                    .map { domain -> rule(domain, RuleAction.Allow, priority = 1_000) }
                    .toTypedArray(),
            )

        SearchEngineDomains.forEach { domain ->
            val decision = evaluator.evaluate(domain, snapshot, activeHealth())

            assertIs<PolicyDecision.Allow>(decision, "Expected $domain to be allowed")
        }
        assertIs<PolicyDecision.Allow>(evaluator.evaluate("www.google.com", snapshot, activeHealth()))
        assertIs<PolicyDecision.Allow>(evaluator.evaluate("www.bing.com", snapshot, activeHealth()))
        assertIs<PolicyDecision.Allow>(evaluator.evaluate("duckduckgo.com", snapshot, activeHealth()))
        assertIs<PolicyDecision.Block>(evaluator.evaluate("app.com", snapshot, activeHealth()))
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

    private companion object {
        const val FixedTime = 1_735_689_600_000L
        val SearchEngineDomains =
            listOf(
                "google.com",
                "bing.com",
                "search.yahoo.com",
                "duckduckgo.com",
            )
    }
}
