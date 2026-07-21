package com.contentfilter.admin.dashboard

import com.contentfilter.admin.DeviceOfflineWarningWindowMillis
import com.contentfilter.core.domain.model.ComponentState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DashboardUiStateTest {
    @Test
    fun `disabled component is a confirmed problem`() {
        val user =
            protectedUser(
                vpnState = ComponentState.Disabled,
                lastSeenAtEpochMillis = System.currentTimeMillis(),
            )

        assertTrue(user.hasConfirmedProblem)
        assertFalse(user.requiresVerification)
        assertEquals(listOf(user), DashboardUiState(protectedUsers = listOf(user)).usersRequiringAttention)
    }

    @Test
    fun `unknown component remains pending instead of appearing healthy`() {
        val user =
            protectedUser(
                accessibilityState = ComponentState.Unknown,
                lastSeenAtEpochMillis = System.currentTimeMillis(),
            )

        assertFalse(user.hasConfirmedProblem)
        assertTrue(user.requiresVerification)
        assertEquals(listOf(user), DashboardUiState(protectedUsers = listOf(user)).usersPendingVerification)
    }

    @Test
    fun `healthy telemetry remains active before one hundred hours`() {
        val user =
            protectedUser(
                lastSeenAtEpochMillis =
                    System.currentTimeMillis() - DeviceOfflineWarningWindowMillis + 60_000L,
            )
        val state = DashboardUiState(protectedUsers = listOf(user))

        assertFalse(user.requiresVerification)
        assertEquals(1, state.activeUserCount)
    }

    @Test
    fun `healthy telemetry becomes pending after one hundred hours`() {
        val user =
            protectedUser(
                lastSeenAtEpochMillis =
                    System.currentTimeMillis() - DeviceOfflineWarningWindowMillis - 60_000L,
            )
        val state = DashboardUiState(protectedUsers = listOf(user))

        assertTrue(user.requiresVerification)
        assertEquals(0, state.activeUserCount)
    }

    @Test
    fun `stale telemetry after uninstall protection disabled is a possible uninstall`() {
        val user =
            protectedUser(
                deviceAdminState = ComponentState.Disabled,
                lastSeenAtEpochMillis =
                    System.currentTimeMillis() -
                        com.contentfilter.core.domain.model.DeviceProtectionAlert.PossibleUninstallWindowMillis -
                        1L,
            )

        assertTrue(user.possibleUninstall)
        assertEquals(listOf(user), DashboardUiState(protectedUsers = listOf(user)).usersWithPossibleUninstall)
    }

    private fun protectedUser(
        vpnState: ComponentState = ComponentState.Enabled,
        accessibilityState: ComponentState = ComponentState.Enabled,
        deviceAdminState: ComponentState = ComponentState.Enabled,
        lastSeenAtEpochMillis: Long?,
    ) = ProtectedUserHealthUiState(
        id = "user-1",
        name = "Usuario",
        vpnState = vpnState,
        accessibilityState = accessibilityState,
        deviceAdminState = deviceAdminState,
        lastSeenAtEpochMillis = lastSeenAtEpochMillis,
    )
}
