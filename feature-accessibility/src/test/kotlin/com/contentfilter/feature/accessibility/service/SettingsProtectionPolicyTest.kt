package com.contentfilter.feature.accessibility.service

import kotlin.test.Test
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
            ),
        )
    }

    private fun decide(
        packageName: String = "com.android.settings",
        className: String,
        ownAppIdentityVisible: Boolean = true,
        armed: Boolean = true,
        settingsAuthorized: Boolean = false,
        removalAuthorized: Boolean = false,
    ): Boolean =
        SettingsProtectionPolicy().shouldLeaveProtectedScreen(
            packageName = packageName,
            className = className,
            ownAppIdentityVisible = ownAppIdentityVisible,
            armed = armed,
            settingsAuthorized = settingsAuthorized,
            removalAuthorized = removalAuthorized,
            elapsedRealtimeMillis = 10_000,
        )
}
