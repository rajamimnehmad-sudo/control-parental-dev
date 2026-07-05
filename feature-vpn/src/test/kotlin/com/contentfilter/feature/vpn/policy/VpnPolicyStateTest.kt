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
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VpnPolicyStateTest {
    @Test
    fun `enables strict web block for wildcard domain block`() {
        val state =
            state(
                rule(
                    target = "*",
                    action = RuleAction.Block,
                ),
            )

        assertTrue(state.strictWebBlockEnabled)
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
    fun `keeps strict mode when web is blocked and search engines are allowed`() {
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

        assertTrue(state.strictWebBlockEnabled)
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
