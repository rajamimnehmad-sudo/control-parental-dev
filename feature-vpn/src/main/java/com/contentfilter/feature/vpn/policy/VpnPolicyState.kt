package com.contentfilter.feature.vpn.policy

import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.model.LicenseState
import com.contentfilter.core.domain.model.PolicySnapshot
import com.contentfilter.core.domain.model.SystemHealthSnapshot
import com.contentfilter.core.domain.model.UpdateState
import com.contentfilter.core.domain.model.safeSearchEnabled
import com.contentfilter.core.domain.model.webImagesBlocked
import com.contentfilter.core.domain.model.webNavigationBlocked
import com.contentfilter.core.policy.SearchProtectionPolicyDefaults

data class VpnPolicyState(
    val snapshot: PolicySnapshot,
    val health: SystemHealthSnapshot,
) {
    val strictWebBlockEnabled: Boolean
        get() = snapshot.rules.webNavigationBlocked()

    val encryptedDnsEnforcementEnabled: Boolean
        get() = !strictWebBlockEnabled && (snapshot.rules.safeSearchEnabled() || snapshot.rules.webImagesBlocked())

    val vpnReconnectKey: String
        get() =
            if (strictWebBlockEnabled) {
                "strict=true"
            } else {
                "strict=false;safeSearch=${snapshot.rules.safeSearchEnabled()};images=${snapshot.rules.webImagesBlocked()}"
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

        private fun PolicySnapshot.isEmptyLocalDefault(): Boolean = id == LocalDefaultPolicyId && rules.isEmpty()

        private const val LocalDefaultPolicyId = "local-default"
    }
}
