package com.contentfilter.feature.vpn.policy

import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.model.LicenseState
import com.contentfilter.core.domain.model.PolicySnapshot
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.model.SystemHealthSnapshot
import com.contentfilter.core.domain.model.UpdateState

data class VpnPolicyState(
    val snapshot: PolicySnapshot,
    val health: SystemHealthSnapshot,
) {
    val strictWebBlockEnabled: Boolean
        get() {
            val wildcardBlocked =
                snapshot.rules.any {
                    it.enabled &&
                        it.scope == RuleScope.Domain &&
                        it.target == DomainWildcard &&
                        it.action == RuleAction.Block
                }
            val hasAllowedDomain =
                snapshot.rules.any {
                    it.enabled &&
                        it.scope == RuleScope.Domain &&
                        it.target != DomainWildcard &&
                        it.action == RuleAction.Allow
                }
            return wildcardBlocked && !hasAllowedDomain
        }

    val vpnReconnectKey: String
        get() {
            val domainRules =
                snapshot.rules
                    .asSequence()
                    .filter { it.enabled && it.scope == RuleScope.Domain }
                    .sortedWith(compareBy({ it.target }, { it.action.name }, { it.priority }))
                    .joinToString("|") { "${it.target}:${it.action}:${it.priority}" }
            return "strict=$strictWebBlockEnabled;domains=$domainRules"
        }

    companion object {
        private const val DomainWildcard = "*"

        fun initial(): VpnPolicyState =
            VpnPolicyState(
                snapshot =
                    PolicySnapshot(
                        id = "empty",
                        version = 0L,
                        rules = emptyList(),
                    ),
                health =
                    SystemHealthSnapshot(
                        vpnState = ComponentState.Unknown,
                        accessibilityState = ComponentState.Unknown,
                        syncState = ComponentState.Unknown,
                        integrityState = ComponentState.Unknown,
                        databaseState = ComponentState.Unknown,
                        licenseState = LicenseState.PendingActivation,
                        updateState = UpdateState.Unknown,
                        checkedAtEpochMillis = 0L,
                    ),
            )
    }
}
