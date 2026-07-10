package com.contentfilter.feature.vpn.policy

import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.model.LicenseState
import com.contentfilter.core.domain.model.PolicySnapshot
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.model.SystemHealthSnapshot
import com.contentfilter.core.domain.model.UpdateState
import com.contentfilter.core.domain.model.webNavigationBlocked
import com.contentfilter.core.policy.SearchProtectionPolicyDefaults

data class VpnPolicyState(
    val snapshot: PolicySnapshot,
    val health: SystemHealthSnapshot,
) {
    val strictWebBlockEnabled: Boolean
        get() = snapshot.rules.webNavigationBlocked()

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
        const val SafeDefaultPolicyId = SearchProtectionPolicyDefaults.SafeDefaultPolicyId

        fun initial(): VpnPolicyState =
            VpnPolicyState(
                snapshot = safeDefaultSnapshot(),
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

        fun safeDefaultSnapshot(): PolicySnapshot = SearchProtectionPolicyDefaults.safeDefaultSnapshot()

        fun resolveSnapshot(
            current: PolicySnapshot,
            candidate: PolicySnapshot,
        ): PolicySnapshot =
            when {
                candidate.isEmptyLocalDefault() &&
                    current.rules.isNotEmpty() &&
                    current.id != SafeDefaultPolicyId -> current
                else -> candidate
            }

        private fun PolicySnapshot.isEmptyLocalDefault(): Boolean = id == LocalDefaultPolicyId && rules.isEmpty()

        private const val LocalDefaultPolicyId = "local-default"
    }
}
