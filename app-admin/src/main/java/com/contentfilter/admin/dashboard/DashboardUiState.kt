package com.contentfilter.admin.dashboard

import com.contentfilter.admin.DeviceOfflineWarningWindowMillis
import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.model.DeviceProtectionAlert
import com.contentfilter.core.domain.model.LicenseState

data class DashboardUiState(
    val deviceCount: Int = 0,
    val pendingRequests: Int = 0,
    val syncState: String = "Unknown",
    val systemState: String = "Unknown",
    val lastSync: String = "Sin datos",
    val communityName: String = "",
    val guideName: String = "",
    val licenseState: LicenseState = LicenseState.PendingActivation,
    val licenseExpiresAtEpochMillis: Long? = null,
    val protectedUsers: List<ProtectedUserHealthUiState> = emptyList(),
    val offlineMode: Boolean = true,
) {
    val usersRequiringAttention: List<ProtectedUserHealthUiState>
        get() = protectedUsers.filter(ProtectedUserHealthUiState::hasConfirmedProblem)

    val usersPendingVerification: List<ProtectedUserHealthUiState>
        get() = protectedUsers.filter(ProtectedUserHealthUiState::requiresVerification)

    val usersWithPossibleUninstall: List<ProtectedUserHealthUiState>
        get() = protectedUsers.filter(ProtectedUserHealthUiState::possibleUninstall)

    val activeUserCount: Int
        get() = protectedUsers.count { user -> !user.hasCommunicationTimedOut && user.lastSeenAtEpochMillis != null }
}

data class ProtectedUserHealthUiState(
    val id: String,
    val name: String,
    val vpnState: ComponentState,
    val accessibilityState: ComponentState,
    val deviceAdminState: ComponentState,
    val lastSeenAtEpochMillis: Long?,
) {
    val vpnProblem: Boolean
        get() = vpnState == ComponentState.Disabled

    val accessibilityProblem: Boolean
        get() = accessibilityState == ComponentState.Disabled

    val deviceAdminProblem: Boolean
        get() = deviceAdminState == ComponentState.Disabled

    val hasConfirmedProblem: Boolean
        get() = vpnProblem || accessibilityProblem || deviceAdminProblem

    val possibleUninstall: Boolean
        get() =
            DeviceProtectionAlert.isPossibleUninstall(
                deviceAdminState = deviceAdminState,
                lastSeenAtEpochMillis = lastSeenAtEpochMillis,
            )

    val hasCommunicationTimedOut: Boolean
        get() =
            lastSeenAtEpochMillis?.let { lastSeen ->
                System.currentTimeMillis() - lastSeen > DeviceOfflineWarningWindowMillis
            } == true

    val requiresVerification: Boolean
        get() =
            !hasConfirmedProblem &&
                (
                    lastSeenAtEpochMillis == null ||
                        hasCommunicationTimedOut ||
                        vpnState != ComponentState.Enabled ||
                        accessibilityState != ComponentState.Enabled ||
                        deviceAdminState != ComponentState.Enabled
                )
}
