package com.contentfilter.user.updates

import kotlin.test.Test
import kotlin.test.assertEquals

class ProtectionPrimaryActionTest {
    @Test
    fun `healthy protection offers temporary access request`() {
        assertEquals(
            ProtectionPrimaryAction.RequestTemporaryAccess,
            protectionPrimaryAction(
                protectionArmed = true,
                vpnState = "Activa",
                accessibilityState = "Activa",
                deviceAdminState = "Activa",
                batteryOptimizationExempt = true,
            ),
        )
    }

    @Test
    fun `any degraded component offers repair`() {
        assertEquals(
            ProtectionPrimaryAction.Repair,
            protectionPrimaryAction(
                protectionArmed = true,
                vpnState = "Activa",
                accessibilityState = "Inactiva",
                deviceAdminState = "Activa",
                batteryOptimizationExempt = true,
            ),
        )
    }
}
