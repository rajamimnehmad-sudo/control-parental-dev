package com.contentfilter.feature.vpn.policy

import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.model.LicenseState
import com.contentfilter.core.domain.model.PolicySnapshot
import com.contentfilter.core.domain.model.SystemHealthSnapshot
import com.contentfilter.core.domain.model.UpdateState
import com.contentfilter.core.domain.model.onlySearchResultsEnabled
import com.contentfilter.core.domain.model.safeSearchEnabled
import com.contentfilter.core.domain.model.webNavigationBlocked
import com.contentfilter.core.policy.SearchProtectionPolicyDefaults

data class VpnPolicyState(
    val snapshot: PolicySnapshot,
    val health: SystemHealthSnapshot,
) {
    val strictWebBlockEnabled: Boolean
        get() = snapshot.rules.webNavigationBlocked()

    val onlyResultsEnabled: Boolean
        get() = snapshot.rules.onlySearchResultsEnabled()

    val encryptedDnsEnforcementEnabled: Boolean
        get() =
            !strictWebBlockEnabled &&
                (
                    onlyResultsEnabled ||
                        snapshot.rules.safeSearchEnabled()
                )

    val vpnReconnectKey: String
        get() =
            if (strictWebBlockEnabled) {
                "strict=true"
            } else {
                "strict=false;onlyResults=$onlyResultsEnabled;" +
                    "safeSearch=${snapshot.rules.safeSearchEnabled()}"
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
                candidate.id == current.id && candidate.version < current.version -> current
                candidate.isEmptyLocalDefault() &&
                    current.rules.isNotEmpty() &&
                    current.id != SafeDefaultPolicyId -> current
                else -> candidate
            }

        fun requiresConnectionInvalidation(
            appliedReconnectKey: String?,
            next: VpnPolicyState,
        ): Boolean =
            !next.strictWebBlockEnabled &&
                next.onlyResultsEnabled &&
                appliedReconnectKey?.contains("onlyResults=false") == true

        private fun PolicySnapshot.isEmptyLocalDefault(): Boolean = id == LocalDefaultPolicyId && rules.isEmpty()

        private const val LocalDefaultPolicyId = "local-default"
    }
}
