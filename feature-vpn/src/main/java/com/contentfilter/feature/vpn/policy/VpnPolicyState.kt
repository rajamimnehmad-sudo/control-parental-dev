package com.contentfilter.feature.vpn.policy

import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.model.LicenseState
import com.contentfilter.core.domain.model.PolicySnapshot
import com.contentfilter.core.domain.model.SystemHealthSnapshot
import com.contentfilter.core.domain.model.UpdateState

data class VpnPolicyState(
    val snapshot: PolicySnapshot,
    val health: SystemHealthSnapshot,
) {
    companion object {
        fun initial(): VpnPolicyState =
            VpnPolicyState(
                snapshot = PolicySnapshot(
                    id = "empty",
                    version = 0L,
                    rules = emptyList(),
                ),
                health = SystemHealthSnapshot(
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
