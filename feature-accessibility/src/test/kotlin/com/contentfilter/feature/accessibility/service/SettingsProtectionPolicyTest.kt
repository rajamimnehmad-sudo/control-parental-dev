package com.contentfilter.feature.accessibility.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsProtectionPolicyTest {
    @Test
    fun normalSettingsRemainAvailable() {
        assertFalse(decide(className = "com.android.settings.Settings"))
    }

    @Test
    fun ownAppInfoIsBlockedWhenArmed() {
        assertTrue(decide(className = "com.android.settings.applications.InstalledAppDetails"))
    }

    @Test
    fun otherAppInfoIsNotBlocked() {
        assertFalse(
            decide(
                className = "com.android.settings.applications.InstalledAppDetails",
                ownAppIdentityVisible = false,
            ),
        )
    }

    @Test
    fun resolvedOemUninstallerIsProtected() {
        assertTrue(
            decide(
                packageName = "com.vendor.securitycenter",
                className = "com.vendor.app.DeletePackageActivity",
                resolvedOwnUninstaller = true,
            ),
        )
    }

    @Test
    fun unrelatedPackageWithDeleteClassIsNotProtected() {
        assertFalse(
            decide(
                packageName = "com.example.app",
                className = "com.example.DeletePackageActivity",
                resolvedOwnUninstaller = false,
            ),
        )
    }

    @Test
    fun dangerousActionProtectsUnknownOemAppInfoClass() {
        assertTrue(
            decide(
                className = "com.vendor.settings.ApplicationDetails",
                dangerousSettingsActionVisible = true,
            ),
        )
    }

    @Test
    fun dangerousActionForAnotherAppDoesNotBlockSettings() {
        assertFalse(
            decide(
                className = "com.vendor.settings.ApplicationDetails",
                ownAppIdentityVisible = false,
                dangerousSettingsActionVisible = true,
            ),
        )
    }

    @Test
    fun dangerousSettingsActionMatchesStableIdsAndClickableLabels() {
        assertTrue(
            isDangerousSettingsAction(
                viewId = "com.android.settings:id/force_stop_button",
                label = null,
                clickable = false,
            ),
        )
        assertTrue(isDangerousSettingsAction(viewId = null, label = "Desinstalar", clickable = true))
        assertFalse(isDangerousSettingsAction(viewId = null, label = "Desinstalar", clickable = false))
    }

    @Test
    fun removalAuthorizationAllowsOwnAppInfo() {
        assertFalse(
            decide(
                className = "com.android.settings.applications.InstalledAppDetails",
                removalAuthorized = true,
            ),
        )
    }

    @Test
    fun guardIsOffUntilAdminArmsIt() {
        assertFalse(
            decide(
                className = "com.android.settings.applications.InstalledAppDetails",
                armed = false,
                deviceAdminEnabled = false,
            ),
        )
    }

    @Test
    fun enabledDeviceAdminProtectsRemovalWhileRemoteControlIsPending() {
        assertTrue(
            decide(
                className = "com.android.settings.applications.InstalledAppDetails",
                armed = false,
                deviceAdminEnabled = true,
            ),
        )
    }

    @Test
    fun deviceAdminActivationIsAllowedWhileAdminIsDisabled() {
        assertFalse(
            decide(
                className = "com.android.settings.DeviceAdminAdd",
                deviceAdminEnabled = false,
            ),
        )
    }

    @Test
    fun deviceAdminDeactivationIsBlockedWhileAdminIsEnabled() {
        assertTrue(decide(className = "com.android.settings.DeviceAdminAdd"))
    }

    @Test
    fun samsungDeviceAdminListIsBlockedWhileAdminIsEnabled() {
        assertTrue(decide(className = "com.android.settings.Settings\$DeviceAdminSettingsActivity"))
    }

    @Test
    fun samsungDeviceAdminScreenDoesNotWaitForOwnIdentity() {
        assertTrue(
            decide(
                className = "com.samsung.android.settings.applications.specialaccess.SecDeviceAdminAdd",
                ownAppIdentityVisible = false,
            ),
        )
    }

    @Test
    fun repeatedEventsNeverCreateAProtectionBypass() {
        val policy = SettingsProtectionPolicy()
        assertTrue(policy.decideAt(elapsedRealtimeMillis = 10_000))
        assertTrue(policy.decideAt(elapsedRealtimeMillis = 10_050))
    }

    @Test
    fun accessibilityDetailsDoNotWaitForOwnIdentityWhenArmed() {
        assertTrue(
            decide(
                className = "com.android.settings.Settings\$AccessibilityDetailsSettingsActivity",
                ownAppIdentityVisible = false,
            ),
        )
    }

    @Test
    fun vpnSettingsDoNotWaitForOwnIdentityWhenArmed() {
        assertTrue(
            decide(
                className = "com.android.settings.Settings\$VpnSettingsActivity",
                ownAppIdentityVisible = false,
            ),
        )
    }

    @Test
    fun criticalSettingsRemainAvailableWhileGuardIsDisarmed() {
        assertFalse(
            decide(
                className = "com.android.settings.Settings\$AccessibilityDetailsSettingsActivity",
                ownAppIdentityVisible = false,
                armed = false,
                deviceAdminEnabled = false,
            ),
        )
    }

    @Test
    fun enabledDeviceAdminKeepsCriticalSettingsProtectedWhileRemoteControlIsPending() {
        assertTrue(
            decide(
                className = "com.android.settings.Settings\$VpnSettingsActivity",
                ownAppIdentityVisible = false,
                armed = false,
                deviceAdminEnabled = true,
            ),
        )
    }

    @Test
    fun maintenanceAuthorizationAllowsCriticalSettings() {
        assertFalse(
            decide(
                className = "com.android.settings.Settings\$VpnSettingsActivity",
                ownAppIdentityVisible = false,
                settingsAuthorized = true,
            ),
        )
    }

    @Test
    fun protectedSettingsGoBackBeforeFallingBackHome() {
        assertEquals(SettingsEscapeAction.Back, SettingsEscapeStrategy.actionForAttempt(0))
        assertEquals(SettingsEscapeAction.Back, SettingsEscapeStrategy.actionForAttempt(1))
        assertEquals(SettingsEscapeAction.Home, SettingsEscapeStrategy.actionForAttempt(2))
    }

    @Test
    fun samsungAdminLabelIdentifiesOwnApp() {
        assertTrue(
            "Protección de Content Filter".matchesOwnAppIdentity(
                ownPackage = "com.contentfilter.user.dev",
                appLabel = "Content Filter",
            ),
        )
    }

    @Test
    fun unrelatedAdminLabelDoesNotIdentifyOwnApp() {
        assertFalse(
            "Enlace a Windows".matchesOwnAppIdentity(
                ownPackage = "com.contentfilter.user.dev",
                appLabel = "Content Filter",
            ),
        )
    }

    private fun decide(
        packageName: String = "com.android.settings",
        className: String,
        ownAppIdentityVisible: Boolean = true,
        resolvedOwnUninstaller: Boolean = false,
        dangerousSettingsActionVisible: Boolean = false,
        deviceAdminEnabled: Boolean = true,
        armed: Boolean = true,
        settingsAuthorized: Boolean = false,
        removalAuthorized: Boolean = false,
    ): Boolean =
        SettingsProtectionPolicy().shouldLeaveProtectedScreen(
            packageName = packageName,
            className = className,
            ownAppIdentityVisible = ownAppIdentityVisible,
            resolvedOwnUninstaller = resolvedOwnUninstaller,
            dangerousSettingsActionVisible = dangerousSettingsActionVisible,
            deviceAdminEnabled = deviceAdminEnabled,
            armed = armed,
            settingsAuthorized = settingsAuthorized,
            removalAuthorized = removalAuthorized,
            elapsedRealtimeMillis = 10_000,
        )

    private fun SettingsProtectionPolicy.decideAt(elapsedRealtimeMillis: Long): Boolean =
        shouldLeaveProtectedScreen(
            packageName = "com.android.settings",
            className = "com.samsung.android.settings.applications.specialaccess.SecDeviceAdminAdd",
            ownAppIdentityVisible = false,
            resolvedOwnUninstaller = false,
            dangerousSettingsActionVisible = false,
            deviceAdminEnabled = true,
            armed = false,
            settingsAuthorized = false,
            removalAuthorized = false,
            elapsedRealtimeMillis = elapsedRealtimeMillis,
        )
}
