package com.contentfilter.feature.accessibility.service

import com.contentfilter.core.domain.model.ProtectionAuthorizationScope

class SettingsProtectionPolicy {
    fun couldContainProtectedScreen(
        packageName: String,
        resolvedOwnUninstaller: Boolean,
    ): Boolean =
        packageName == AndroidSettingsPackage ||
            packageName == SamsungAccessibilityPackage ||
            packageName in PackageInstallerPackages ||
            resolvedOwnUninstaller

    fun shouldLeaveProtectedScreen(
        packageName: String,
        className: String?,
        ownAppIdentityVisible: Boolean,
        adminAppIdentityVisible: Boolean,
        resolvedOwnUninstaller: Boolean,
        dangerousSettingsActionVisible: Boolean,
        deviceAdminEnabled: Boolean,
        armed: Boolean,
        settingsAuthorized: Boolean,
        removalAuthorized: Boolean,
        trustedInstallAuthorized: Boolean,
        elapsedRealtimeMillis: Long,
    ): Boolean {
        val requiredScope =
            protectedScope(
                packageName = packageName,
                className = className,
                resolvedOwnUninstaller = resolvedOwnUninstaller,
                dangerousSettingsActionVisible = dangerousSettingsActionVisible,
            ) ?: return false
        if (!armed && !deviceAdminEnabled) return false
        val deviceAdminRemovalScreen = isDeviceAdminRemovalScreen(packageName, className)
        val criticalSettingsScreen = isCriticalSettingsScreen(packageName, className)
        val packageInstallScreen = isPackageInstallScreen(packageName, className)
        val unknownSourcesScreen = isUnknownSourcesScreen(packageName, className)
        if (adminAppIdentityVisible && (unknownSourcesScreen || requiredScope == ProtectionAuthorizationScope.Removal)) {
            return false
        }
        if (deviceAdminRemovalScreen && !deviceAdminEnabled) return false
        if (trustedInstallAuthorized && (packageInstallScreen || unknownSourcesScreen)) return false
        if (
            !deviceAdminRemovalScreen &&
            !criticalSettingsScreen &&
            !packageInstallScreen &&
            !unknownSourcesScreen &&
            !ownAppIdentityVisible
        ) {
            return false
        }
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

    private fun isCriticalSettingsScreen(
        packageName: String,
        className: String?,
    ): Boolean {
        val normalizedClass = className.orEmpty()
        return packageName == AndroidSettingsPackage &&
            CriticalSettingsClassHints.any { normalizedClass.contains(it, ignoreCase = true) } ||
            packageName == SamsungAccessibilityPackage &&
            normalizedClass.contains(SamsungSubSettingsClassHint, ignoreCase = true)
    }

    private fun isPackageInstallScreen(
        packageName: String,
        className: String?,
    ): Boolean =
        packageName in PackageInstallerPackages &&
            InstallClassHints.any { className.orEmpty().contains(it, ignoreCase = true) }

    private fun isUnknownSourcesScreen(
        packageName: String,
        className: String?,
    ): Boolean =
        packageName == AndroidSettingsPackage &&
            UnknownSourcesClassHints.any { className.orEmpty().contains(it, ignoreCase = true) }

    private fun protectedScope(
        packageName: String,
        className: String?,
        resolvedOwnUninstaller: Boolean,
        dangerousSettingsActionVisible: Boolean,
    ): ProtectionAuthorizationScope? {
        val normalizedClass = className.orEmpty()
        if (
            (packageName in PackageInstallerPackages || resolvedOwnUninstaller) &&
            UninstallClassHints.any { normalizedClass.contains(it, true) }
        ) {
            return ProtectionAuthorizationScope.Removal
        }
        if (
            packageName in PackageInstallerPackages &&
            InstallClassHints.any { normalizedClass.contains(it, true) }
        ) {
            return ProtectionAuthorizationScope.Settings
        }
        if (
            packageName == SamsungAccessibilityPackage &&
            normalizedClass.contains(SamsungSubSettingsClassHint, ignoreCase = true)
        ) {
            return ProtectionAuthorizationScope.Settings
        }
        if (packageName != AndroidSettingsPackage) return null
        if (dangerousSettingsActionVisible) return ProtectionAuthorizationScope.Removal
        if (RemovalClassHints.any { normalizedClass.contains(it, true) }) return ProtectionAuthorizationScope.Removal
        if (SettingsClassHints.any { normalizedClass.contains(it, true) }) return ProtectionAuthorizationScope.Settings
        return null
    }

    private var lastActionAtElapsedMillis: Long = -MinActionIntervalMillis

    private companion object {
        const val AndroidSettingsPackage = "com.android.settings"
        const val SamsungAccessibilityPackage = "com.samsung.accessibility"
        const val SamsungSubSettingsClassHint = "SubSettings"
        const val MinActionIntervalMillis = 2_000L
        val PackageInstallerPackages =
            setOf(
                "com.android.packageinstaller",
                "com.google.android.packageinstaller",
                "com.android.permissioncontroller",
                "com.google.android.permissioncontroller",
                "com.samsung.android.packageinstaller",
                "com.samsung.android.permissioncontroller",
            )
        val UninstallClassHints = listOf("Uninstall", "DeletePackage")
        val InstallClassHints =
            listOf(
                "InstallStart",
                "InstallConfirm",
                "PackageInstallerActivity",
                "PackageInstaller",
                "InstallInstalling",
                "InstallStaging",
                "InstallSuccess",
            )
        val UnknownSourcesClassHints =
            listOf(
                "ManageExternalSources",
                "ExternalSources",
                "UnknownAppSources",
                "UnknownSources",
                "InstallUnknownApps",
                "RequestInstallPackages",
                "SpecialAccessDetails",
            )
        val DeviceAdminClassHints =
            listOf(
                "DeviceAdminAdd",
                "DeviceAdminSettings",
                "DeviceAdminWarning",
            )
        val CriticalSettingsClassHints =
            listOf(
                "AccessibilityDetails",
                "ToggleAccessibility",
                "AccessibilityServiceWarning",
                "VpnProfile",
                "VpnSettings",
            )
        val RemovalClassHints =
            listOf(
                "InstalledAppDetails",
                "AppInfoDashboard",
                "AppInfoActivity",
            ) + DeviceAdminClassHints
        val SettingsClassHints =
            CriticalSettingsClassHints +
                UnknownSourcesClassHints
    }
}

internal fun isDangerousSettingsAction(
    viewId: String?,
    label: String?,
    clickable: Boolean,
): Boolean {
    if (DangerousActionIdHints.any { viewId.orEmpty().contains(it, ignoreCase = true) }) return true
    if (!clickable) return false
    return DangerousActionLabels.any { label.orEmpty().trim().equals(it, ignoreCase = true) }
}

private val DangerousActionIdHints =
    listOf(
        "force_stop_button",
        "uninstall_button",
        "disable_button",
    )

private val DangerousActionLabels =
    listOf(
        "force stop",
        "uninstall",
        "disable",
        "forzar detención",
        "forzar detencion",
        "desinstalar",
        "desactivar",
    )

internal enum class SettingsEscapeAction {
    Back,
    Home,
}

internal object SettingsEscapeStrategy {
    fun actionForAttempt(
        attempt: Int,
        urgent: Boolean = false,
    ): SettingsEscapeAction =
        if (!urgent && attempt < BackAttemptsBeforeHome) {
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
    if (value.matchesAdminAppIdentity()) return false
    return value.contains(ownPackage, ignoreCase = true) ||
        (appLabel.isNotBlank() && value.contains(appLabel, ignoreCase = true))
}

internal fun String?.matchesAdminAppIdentity(): Boolean {
    val value = this ?: return false
    return AdminPackageNames.any { value.contains(it, ignoreCase = true) } ||
        value.contains(AdminAppLabel, ignoreCase = true)
}

private val AdminPackageNames =
    setOf(
        "com.contentfilter.admin",
        "com.contentfilter.admin.dev",
        "com.contentfilter.admin.beta",
    )
private const val AdminAppLabel = "Content Filter Admin"
