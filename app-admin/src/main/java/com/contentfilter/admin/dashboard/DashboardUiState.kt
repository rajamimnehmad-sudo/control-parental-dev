package com.contentfilter.admin.dashboard

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
        get() = protectedUsers.count(ProtectedUserHealthUiState::isRecentlySeen)
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

    val isRecentlySeen: Boolean
        get() =
            lastSeenAtEpochMillis?.let { lastSeen ->
                System.currentTimeMillis() - lastSeen <= ACTIVE_USER_WINDOW_MILLIS
            } == true

    val requiresVerification: Boolean
        get() =
            !hasConfirmedProblem &&
                (
                    !isRecentlySeen ||
                        vpnState != ComponentState.Enabled ||
                        accessibilityState != ComponentState.Enabled ||
                        deviceAdminState != ComponentState.Enabled
                )

    private companion object {
        const val ACTIVE_USER_WINDOW_MILLIS = 15 * 60 * 1000L
    }
}
