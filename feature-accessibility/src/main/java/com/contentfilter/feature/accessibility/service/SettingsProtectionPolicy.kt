package com.contentfilter.feature.accessibility.service

import com.contentfilter.core.domain.model.ProtectionAuthorizationScope

class SettingsProtectionPolicy {
    fun shouldLeaveProtectedScreen(
        packageName: String,
        className: String?,
        ownAppIdentityVisible: Boolean,
        deviceAdminEnabled: Boolean,
        armed: Boolean,
        settingsAuthorized: Boolean,
        removalAuthorized: Boolean,
        elapsedRealtimeMillis: Long,
    ): Boolean {
        val requiredScope = protectedScope(packageName, className) ?: return false
        if (!armed && !deviceAdminEnabled) return false
        val deviceAdminRemovalScreen = isDeviceAdminRemovalScreen(packageName, className)
        if (deviceAdminRemovalScreen && !deviceAdminEnabled) return false
        if (!deviceAdminRemovalScreen && !ownAppIdentityVisible) return false
        if (requiredScope == ProtectionAuthorizationScope.Settings && settingsAuthorized) return false
        if (requiredScope == ProtectionAuthorizationScope.Removal && removalAuthorized) return false
        if (elapsedRealtimeMillis - lastActionAtElapsedMillis >= MinActionIntervalMillis) {
            lastActionAtElapsedMillis = elapsedRealtimeMillis
        }
        return true
    }

    private fun isDeviceAdminRemovalScreen(
        packageName: String,
        className: String?,
    ): Boolean =
        packageName == AndroidSettingsPackage &&
            DeviceAdminClassHints.any { className.orEmpty().contains(it, ignoreCase = true) }

    private fun protectedScope(
        packageName: String,
        className: String?,
    ): ProtectionAuthorizationScope? {
        val normalizedClass = className.orEmpty()
        if (packageName in PackageInstallerPackages && UninstallClassHints.any { normalizedClass.contains(it, true) }) {
            return ProtectionAuthorizationScope.Removal
        }
        if (packageName != AndroidSettingsPackage) return null
        if (RemovalClassHints.any { normalizedClass.contains(it, true) }) return ProtectionAuthorizationScope.Removal
        if (SettingsClassHints.any { normalizedClass.contains(it, true) }) return ProtectionAuthorizationScope.Settings
        return null
    }

    private var lastActionAtElapsedMillis: Long = -MinActionIntervalMillis

    private companion object {
        const val AndroidSettingsPackage = "com.android.settings"
        const val MinActionIntervalMillis = 2_000L
        val PackageInstallerPackages =
            setOf(
                "com.android.packageinstaller",
                "com.google.android.packageinstaller",
                "com.android.permissioncontroller",
                "com.google.android.permissioncontroller",
            )
        val UninstallClassHints = listOf("Uninstall", "DeletePackage")
        val DeviceAdminClassHints =
            listOf(
                "DeviceAdminAdd",
                "DeviceAdminSettings",
                "DeviceAdminWarning",
            )
        val RemovalClassHints =
            listOf(
                "InstalledAppDetails",
                "AppInfoDashboard",
                "AppInfoActivity",
            ) + DeviceAdminClassHints
        val SettingsClassHints =
            listOf(
                "AccessibilityDetails",
                "ToggleAccessibility",
                "AccessibilityServiceWarning",
                "VpnProfile",
                "VpnSettings",
                "ManageExternalSources",
                "SpecialAccessDetails",
            )
    }
}

internal enum class SettingsEscapeAction {
    Back,
    Home,
}

internal object SettingsEscapeStrategy {
    fun actionForAttempt(attempt: Int): SettingsEscapeAction =
        if (attempt < BackAttemptsBeforeHome) {
            SettingsEscapeAction.Back
        } else {
            SettingsEscapeAction.Home
        }

    private const val BackAttemptsBeforeHome = 2
}

internal fun String?.matchesOwnAppIdentity(
    ownPackage: String,
    appLabel: String,
): Boolean {
    val value = this ?: return false
    return value.contains(ownPackage, ignoreCase = true) ||
        (appLabel.isNotBlank() && value.contains(appLabel, ignoreCase = true))
}
