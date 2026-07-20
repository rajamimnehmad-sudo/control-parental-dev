package com.contentfilter.core.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceProtectionAlertTest {
    @Test
    fun `disabled uninstall protection plus stale heartbeat is possible uninstall`() {
        assertTrue(
            DeviceProtectionAlert.isPossibleUninstall(
                deviceAdminState = ComponentState.Disabled,
                lastSeenAtEpochMillis = Now - DeviceProtectionAlert.PossibleUninstallWindowMillis - 1L,
                nowEpochMillis = Now,
            ),
        )
    }

    @Test
    fun `recent disabled protection remains a normal protection alert`() {
        assertFalse(
            DeviceProtectionAlert.isPossibleUninstall(
                deviceAdminState = ComponentState.Disabled,
                lastSeenAtEpochMillis = Now - DeviceProtectionAlert.PossibleUninstallWindowMillis + 1L,
                nowEpochMillis = Now,
            ),
        )
    }

    @Test
    fun `offline protected device is not reported as uninstalled`() {
        assertFalse(
            DeviceProtectionAlert.isPossibleUninstall(
                deviceAdminState = ComponentState.Enabled,
                lastSeenAtEpochMillis = Now - DeviceProtectionAlert.PossibleUninstallWindowMillis - 1L,
                nowEpochMillis = Now,
            ),
        )
    }

    private companion object {
        const val Now = 2_000_000_000_000L
    }
}
