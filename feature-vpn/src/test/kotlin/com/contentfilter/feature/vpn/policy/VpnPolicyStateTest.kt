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
    fun `external results option cannot enable strict routing while web is open`() {
        val state =
            state(
                rule(WebNavigationPolicy.ExternalSearchResultsAllowedTarget, RuleAction.Allow),
                rule("google.com", RuleAction.Block),
                rule("dns.google", RuleAction.Block),
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
    fun `blocked to allowed changes tunnel key and disables strict routing`() {
        val mainRule = rule(WebNavigationPolicy.RuleTarget, RuleAction.Block)
        val blocked = state(mainRule)
        val allowed = state(mainRule.copy(enabled = false))

        assertTrue(blocked.strictWebBlockEnabled)
        assertFalse(allowed.strictWebBlockEnabled)
        assertNotEquals(blocked.vpnReconnectKey, allowed.vpnReconnectKey)
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
    fun `Solo resultados reconnects the tunnel to cut existing connections`() {
        val restricted =
            state(
                rule(WebNavigationPolicy.ExternalSearchResultsAllowedTarget, RuleAction.Allow).copy(enabled = false),
            )
        val released =
            state(
                rule(WebNavigationPolicy.ExternalSearchResultsAllowedTarget, RuleAction.Allow),
            )

        assertNotEquals(restricted.vpnReconnectKey, released.vpnReconnectKey)
        assertTrue(restricted.encryptedDnsEnforcementEnabled)
    }

    @Test
    fun `SafeSearch reconnects open tunnel and enables encrypted DNS enforcement`() {
        val open = state()
        val safeSearch = state(rule(WebNavigationPolicy.SafeSearchTarget, RuleAction.Allow))

        assertFalse(open.encryptedDnsEnforcementEnabled)
        assertTrue(safeSearch.encryptedDnsEnforcementEnabled)
        assertNotEquals(open.vpnReconnectKey, safeSearch.vpnReconnectKey)
    }

    @Test
    fun `preference changes while globally blocked reuse strict tunnel`() {
        val main = rule(WebNavigationPolicy.RuleTarget, RuleAction.Block)
        val blocked = state(main)
        val blockedWithSafeSearch =
            state(
                main,
                rule(WebNavigationPolicy.SafeSearchTarget, RuleAction.Allow),
            )

        assertEquals(blocked.vpnReconnectKey, blockedWithSafeSearch.vpnReconnectKey)
        assertFalse(blockedWithSafeSearch.encryptedDnsEnforcementEnabled)
    }

    @Test
    fun `VPN state preserves the complete cumulative Web preference matrix`() {
        repeat(8) { bits ->
            val webBlocked = bits and 1 != 0
            val onlyResultsEnabled = bits and 2 == 0
            val safeSearchEnabled = bits and 4 != 0
            val state = state(*webRules(bits).toTypedArray())

            assertEquals(webBlocked, state.strictWebBlockEnabled)
            assertEquals(
                !webBlocked && (onlyResultsEnabled || safeSearchEnabled),
                state.encryptedDnsEnforcementEnabled,
            )
            if (webBlocked) {
                assertEquals("strict=true", state.vpnReconnectKey)
            } else {
                assertEquals(
                    "strict=false;onlyResults=$onlyResultsEnabled;safeSearch=$safeSearchEnabled",
                    state.vpnReconnectKey,
                )
            }
        }
    }

    @Test
    fun `activating Solo resultados requests a one-time connection invalidation`() {
        val open = state(rule(WebNavigationPolicy.ExternalSearchResultsAllowedTarget, RuleAction.Allow))
        val restricted =
            state(
                rule(WebNavigationPolicy.ExternalSearchResultsAllowedTarget, RuleAction.Allow).copy(enabled = false),
            )

        assertTrue(VpnPolicyState.requiresConnectionInvalidation(open.vpnReconnectKey, restricted))
        assertFalse(VpnPolicyState.requiresConnectionInvalidation(restricted.vpnReconnectKey, restricted))
        assertFalse(VpnPolicyState.requiresConnectionInvalidation(restricted.vpnReconnectKey, open))
    }

    @Test
    fun `open to strict requires connection invalidation`() {
        val open = state(rule(WebNavigationPolicy.ExternalSearchResultsAllowedTarget, RuleAction.Allow))
        val strict = state(rule(WebNavigationPolicy.RuleTarget, RuleAction.Block))

        assertTrue(VpnPolicyState.requiresConnectionInvalidation(open.vpnReconnectKey, strict))
    }

    @Test
    fun `already applied strict policy does not invalidate repeatedly`() {
        val strict = state(rule(WebNavigationPolicy.RuleTarget, RuleAction.Block))

        assertFalse(VpnPolicyState.requiresConnectionInvalidation(strict.vpnReconnectKey, strict))
    }

    @Test
    fun `cold strict start establishes strict tunnel without transition barrier`() {
        val strict = state(rule(WebNavigationPolicy.RuleTarget, RuleAction.Block))

        assertTrue(strict.strictWebBlockEnabled)
        assertFalse(VpnPolicyState.requiresConnectionInvalidation(appliedReconnectKey = null, next = strict))
    }

    @Test
    fun `strict to open does not use strict transition barrier`() {
        val strict = state(rule(WebNavigationPolicy.RuleTarget, RuleAction.Block))
        val open = state(rule(WebNavigationPolicy.ExternalSearchResultsAllowedTarget, RuleAction.Allow))

        assertFalse(VpnPolicyState.requiresConnectionInvalidation(strict.vpnReconnectKey, open))
    }

    @Test
    fun `initial state uses safe default search blocking rules`() {
        val state = VpnPolicyState.initial()

        assertTrue(
            state.snapshot.rules.any {
                it.target == "google.com" &&
                    it.action == RuleAction.Block &&
                    it.enabled
            },
        )
        assertTrue(
            state.snapshot.rules.any {
                it.target == "clients4.google.com" &&
                    it.action == RuleAction.Block &&
                    it.enabled
            },
        )
        assertTrue(
            state.snapshot.rules.any {
                it.target == "dns.google" &&
                    it.action == RuleAction.Block &&
                    it.enabled
            },
        )
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
    fun `older revision cannot erase newer cumulative Web preferences`() {
        val current =
            PolicySnapshot(
                id = "remote-policy",
                version = 3L,
                rules = webRules(14),
            )
        val stale = current.copy(version = 2L, rules = webRules(1))

        val resolved = VpnPolicyState.resolveSnapshot(current = current, candidate = stale)

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

    private fun webRules(bits: Int): List<PolicyRule> =
        listOf(
            rule(WebNavigationPolicy.RuleTarget, RuleAction.Block).copy(enabled = bits and 1 != 0),
            rule(
                WebNavigationPolicy.ExternalSearchResultsAllowedTarget,
                RuleAction.Allow,
            ).copy(enabled = bits and 2 != 0),
            rule(WebNavigationPolicy.SafeSearchTarget, RuleAction.Allow).copy(enabled = bits and 4 != 0),
        )
}
