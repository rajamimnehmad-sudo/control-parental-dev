package com.contentfilter.core.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class SystemHealthSnapshotTest {
    @Test
    fun `unknown update state does not downgrade otherwise healthy protection`() {
        assertEquals(
            ProtectionLevel.Protected,
            healthySnapshot(updateState = UpdateState.Unknown).protectionLevel,
        )
    }

    @Test
    fun `optional update does not downgrade otherwise healthy protection`() {
        assertEquals(
            ProtectionLevel.Protected,
            healthySnapshot(updateState = UpdateState.OptionalUpdateAvailable).protectionLevel,
        )
    }

    @Test
    fun `required update keeps protection in warning`() {
        assertEquals(
            ProtectionLevel.Warning,
            healthySnapshot(updateState = UpdateState.RequiredUpdateAvailable).protectionLevel,
        )
    }

    private fun healthySnapshot(updateState: UpdateState) =
        SystemHealthSnapshot(
            vpnState = ComponentState.Enabled,
            accessibilityState = ComponentState.Enabled,
            deviceAdminState = ComponentState.Enabled,
            syncState = ComponentState.Enabled,
            integrityState = ComponentState.Enabled,
            databaseState = ComponentState.Enabled,
            licenseState = LicenseState.Active,
            updateState = updateState,
            checkedAtEpochMillis = 0L,
        )
}
