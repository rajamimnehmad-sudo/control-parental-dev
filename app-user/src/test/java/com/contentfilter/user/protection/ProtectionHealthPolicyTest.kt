package com.contentfilter.user.protection

import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.model.ProtectionAlertType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProtectionHealthPolicyTest {
    @Test
    fun healthyProtectionNeedsNoRepairOrAlert() {
        val decision = decide()

        assertEquals(ComponentState.Enabled, decision.vpnState)
        assertEquals(ComponentState.Enabled, decision.accessibilityState)
        assertEquals(ComponentState.Enabled, decision.deviceAdminState)
        assertFalse(decision.shouldRestartVpn)
        assertTrue(decision.canAutoArm)
        assertTrue(decision.alerts.isEmpty())
    }

    @Test
    fun stoppedVpnIsReportedAndRestartedWhenPermissionExists() {
        val decision = decide(vpnActive = false)

        assertEquals(ComponentState.Disabled, decision.vpnState)
        assertTrue(decision.shouldRestartVpn)
        assertEquals(setOf(ProtectionAlertType.WebDisabled), decision.alerts)
    }

    @Test
    fun intentionallyDisabledVpnIsReportedButNotRestarted() {
        val decision =
            decide(
                vpnActive = false,
                vpnProtectionDisabled = true,
            )

        assertFalse(decision.shouldRestartVpn)
        assertEquals(setOf(ProtectionAlertType.WebDisabled), decision.alerts)
    }

    @Test
    fun missingPermissionDoesNotStartVpnWithoutConsent() {
        val decision =
            decide(
                vpnActive = false,
                vpnPermissionGranted = false,
            )

        assertFalse(decision.shouldRestartVpn)
    }

    @Test
    fun disabledLocalBarriersProduceSpecificAlerts() {
        val decision =
            decide(
                accessibilityEnabled = false,
                deviceAdminEnabled = false,
            )

        assertEquals(ComponentState.Disabled, decision.accessibilityState)
        assertEquals(ComponentState.Disabled, decision.deviceAdminState)
        assertEquals(
            setOf(
                ProtectionAlertType.AppsDisabled,
                ProtectionAlertType.AdminDisabled,
            ),
            decision.alerts,
        )
        assertFalse(decision.canAutoArm)
    }

    @Test
    fun intentionallyDisabledVpnDoesNotAutoArmEvenIfTunnelIsStillStopping() {
        val decision = decide(vpnProtectionDisabled = true)

        assertFalse(decision.canAutoArm)
    }

    private fun decide(
        vpnActive: Boolean = true,
        accessibilityEnabled: Boolean = true,
        deviceAdminEnabled: Boolean = true,
        vpnPermissionGranted: Boolean = true,
        vpnProtectionDisabled: Boolean = false,
    ): ProtectionHealthDecision =
        ProtectionHealthPolicy.evaluate(
            vpnActive = vpnActive,
            accessibilityEnabled = accessibilityEnabled,
            deviceAdminEnabled = deviceAdminEnabled,
            vpnPermissionGranted = vpnPermissionGranted,
            vpnProtectionDisabled = vpnProtectionDisabled,
        )
}
