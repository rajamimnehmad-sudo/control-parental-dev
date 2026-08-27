package com.contentfilter.feature.accessibility.service

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsProtectionImmediateDecisionTest {
    private val policy = SettingsProtectionPolicy()

    @Test
    fun `critical accessibility vpn and device admin screens can be decided without tree identity`() {
        assertTrue(
            policy.canBlockImmediatelyWithoutTreeIdentity(
                "com.android.settings",
                "com.android.settings.accessibility.AccessibilityDetailsSettings",
            ),
        )
        assertTrue(
            policy.canBlockImmediatelyWithoutTreeIdentity(
                "com.android.settings",
                "com.android.settings.vpn2.VpnSettings",
            ),
        )
        assertTrue(
            policy.canBlockImmediatelyWithoutTreeIdentity(
                "com.android.settings",
                "com.android.settings.DeviceAdminAdd",
            ),
        )
    }

    @Test
    fun `normal package installation can be decided without app identity`() {
        assertTrue(
            policy.canBlockImmediatelyWithoutTreeIdentity(
                "com.google.android.packageinstaller",
                "com.android.packageinstaller.InstallStart",
            ),
        )
    }

    @Test
    fun `unknown sources waits for tree because admin identity is an allowed exception`() {
        assertFalse(
            policy.canBlockImmediatelyWithoutTreeIdentity(
                "com.android.settings",
                "com.android.settings.Settings\$ManageExternalSourcesActivity",
            ),
        )
    }

    @Test
    fun `app info and uninstall wait for tree because target identity changes the decision`() {
        assertFalse(
            policy.canBlockImmediatelyWithoutTreeIdentity(
                "com.android.settings",
                "com.android.settings.applications.InstalledAppDetails",
            ),
        )
        assertFalse(
            policy.canBlockImmediatelyWithoutTreeIdentity(
                "com.google.android.packageinstaller",
                "com.android.packageinstaller.UninstallerActivity",
            ),
        )
    }
}
