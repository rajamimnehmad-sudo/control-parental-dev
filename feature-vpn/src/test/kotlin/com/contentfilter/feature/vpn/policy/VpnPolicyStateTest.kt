package com.contentfilter.feature.vpn.policy

import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.model.LicenseState
import com.contentfilter.core.domain.model.PolicyLevel
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicySnapshot
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.model.SystemHealthSnapshot
import com.contentfilter.core.domain.model.UpdateState
import com.contentfilter.core.domain.model.WebNavigationPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class VpnPolicyStateTest {
    @Test
    fun `keeps dns mode for stale wildcard domain block`() {
        val state =
            state(
                rule(
                    target = "*",
                    action = RuleAction.Block,
                ),
            )

        assertFalse(state.strictWebBlockEnabled)
    }

    @Test
    fun `keeps dns mode for specific domain block`() {
        val state =
            state(
                rule(
                    target = "example.com",
                    action = RuleAction.Block,
                ),
            )

        assertFalse(state.strictWebBlockEnabled)
    }

    @Test
    fun `enables strict browser tunnel for web navigation block`() {
        val state =
            state(
                rule(
                    target = WebNavigationPolicy.RuleTarget,
                    action = RuleAction.Block,
                ),
            )

        assertTrue(state.strictWebBlockEnabled)
    }

    @Test
    fun `keeps dns mode for wildcard allow`() {
        val state =
            state(
                rule(
                    target = "*",
                    action = RuleAction.Allow,
                ),
            )

        assertFalse(state.strictWebBlockEnabled)
    }

    @Test
    fun `uses dns mode when web is blocked and search engines are allowed`() {
        val state =
            state(
                rule(
                    target = "*",
                    action = RuleAction.Block,
                ),
                rule(
                    target = "google.com",
                    action = RuleAction.Allow,
                ),
                rule(
                    target = "bing.com",
                    action = RuleAction.Allow,
                ),
                rule(
                    target = "search.yahoo.com",
                    action = RuleAction.Allow,
                ),
                rule(
                    target = "duckduckgo.com",
                    action = RuleAction.Allow,
                ),
            )

        assertFalse(state.strictWebBlockEnabled)
    }

    @Test
    fun `uses dns mode when web is blocked and whitelist has allowed domain`() {
        val state =
            state(
                rule(
                    target = "*",
                    action = RuleAction.Block,
                ),
                rule(
                    target = "example.com",
                    action = RuleAction.Allow,
                ),
            )

        assertFalse(state.strictWebBlockEnabled)
    }

    @Test
    fun `vpn reconnect key changes when search engine rule action changes`() {
        val allowed =
            state(
                rule(
                    target = "*",
                    action = RuleAction.Block,
                ),
                rule(
                    target = "google.com",
                    action = RuleAction.Allow,
                ),
            )
        val blocked =
            state(
                rule(
                    target = "*",
                    action = RuleAction.Block,
                ),
                rule(
                    target = "google.com",
                    action = RuleAction.Block,
                ),
            )

        assertNotEquals(allowed.vpnReconnectKey, blocked.vpnReconnectKey)
    }

    @Test
    fun `initial state uses safe default search blocking rules`() {
        val state = VpnPolicyState.initial()

        assertTrue(state.snapshot.rules.any { it.target == "google.com" && it.action == RuleAction.Block && it.enabled })
        assertTrue(state.snapshot.rules.any { it.target == "clients4.google.com" && it.action == RuleAction.Block && it.enabled })
        assertTrue(state.snapshot.rules.any { it.target == "dns.google" && it.action == RuleAction.Block && it.enabled })
    }

    @Test
    fun `keeps last valid policy when empty local default arrives`() {
        val current =
            PolicySnapshot(
                id = "remote-policy",
                version = 2L,
                rules = listOf(rule("google.com", RuleAction.Block)),
            )
        val emptyLocal =
            PolicySnapshot(
                id = "local-default",
                version = 1L,
                rules = emptyList(),
            )

        val resolved = VpnPolicyState.resolveSnapshot(current = current, candidate = emptyLocal)

        assertEquals(current, resolved)
    }

    @Test
    fun `uses empty local default when startup policy is open`() {
        val emptyCurrent = PolicySnapshot(id = "empty", version = 0L, rules = emptyList())
        val emptyLocal = PolicySnapshot(id = "local-default", version = 1L, rules = emptyList())

        val resolved = VpnPolicyState.resolveSnapshot(current = emptyCurrent, candidate = emptyLocal)

        assertEquals(emptyLocal, resolved)
    }

    private fun state(vararg rules: PolicyRule): VpnPolicyState =
        VpnPolicyState(
            snapshot =
                PolicySnapshot(
                    id = "test",
                    version = 1L,
                    rules = rules.toList(),
                ),
            health =
                SystemHealthSnapshot(
                    vpnState = ComponentState.Enabled,
                    accessibilityState = ComponentState.Enabled,
                    syncState = ComponentState.Enabled,
                    integrityState = ComponentState.Enabled,
                    databaseState = ComponentState.Enabled,
                    licenseState = LicenseState.Active,
                    updateState = UpdateState.Current,
                    checkedAtEpochMillis = 0L,
                ),
        )

    private fun rule(
        target: String,
        action: RuleAction,
    ): PolicyRule =
        PolicyRule(
            id = "rule-$target-$action",
            level = PolicyLevel.Device,
            scope = RuleScope.Domain,
            target = target,
            action = action,
            priority = 0,
            enabled = true,
        )
}
