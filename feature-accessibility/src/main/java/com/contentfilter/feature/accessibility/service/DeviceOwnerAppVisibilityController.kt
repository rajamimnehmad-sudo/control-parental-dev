package com.contentfilter.feature.accessibility.service

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.contentfilter.core.domain.model.PolicyDecision
import com.contentfilter.core.domain.model.PolicyTargetType
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.repository.InstallApprovalStore
import com.contentfilter.feature.accessibility.policy.AccessibilityAppPolicyEvaluator
import com.contentfilter.feature.accessibility.policy.AccessibilityPolicyState

/**
 * Applies effective app policy through Device Owner visibility controls.
 *
 * Glosh only unhides packages it previously hid itself. This avoids undoing
 * package visibility decisions made by Android, the OEM, or another policy.
 */
class DeviceOwnerAppVisibilityController(
    context: Context,
    private val policyEvaluator: AccessibilityAppPolicyEvaluator,
    private val installApprovalStore: InstallApprovalStore,
) {
    private val appContext = context.applicationContext
    private val devicePolicyManager = appContext.getSystemService(DevicePolicyManager::class.java)
    private val adminComponent = DeviceAdminController.component(appContext)
    private val preferences = appContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
    private val stateLock = Any()

    fun isDeviceOwner(): Boolean = devicePolicyManager.isDeviceOwnerApp(appContext.packageName)

    fun hideNow(packageName: String): Boolean {
        if (!isEligiblePackage(packageName) || !isDeviceOwner()) return false
        if (packageName in hiddenByGlosh()) return true
        return applyHidden(packageName, hidden = true)
    }

    fun reconcile(state: AccessibilityPolicyState) {
        if (!isDeviceOwner()) return
        val hiddenPackages = hiddenByGlosh()
        managedPackageCandidates(state, hiddenPackages).forEach { packageName ->
            val persistedMinutes =
                state.snapshot.dailyUsage
                    .firstOrNull { it.packageName == packageName }
                    ?.usedMinutes ?: 0
            val shouldHide =
                installApprovalStore.isPending(packageName) ||
                    when (policyEvaluator.evaluate(packageName, persistedMinutes, state.snapshot, state.health)) {
                        is PolicyDecision.Block,
                        is PolicyDecision.RequestAuthorization,
                        -> true
                        else -> false
                    }
            when {
                shouldHide && packageName !in hiddenPackages -> applyHidden(packageName, hidden = true)
                !shouldHide && packageName in hiddenPackages -> applyHidden(packageName, hidden = false)
            }
        }
    }

    private fun managedPackageCandidates(
        state: AccessibilityPolicyState,
        hiddenPackages: Set<String>,
    ): Set<String> =
        buildSet {
            addAll(installedPackages())
            state.snapshot.rules
                .filter { it.enabled && it.scope == RuleScope.App && it.target != "*" }
                .mapTo(this) { it.target }
            state.snapshot.dailyLimits
                .filter { it.enabled && it.targetType == PolicyTargetType.App }
                .mapTo(this) { it.target }
            state.snapshot.appGroups
                .filter { it.enabled }
                .flatMap { it.apps }
                .filter { it.enabled }
                .mapTo(this) { it.packageName }
            addAll(hiddenPackages)
        }.filterTo(mutableSetOf(), ::isEligiblePackage)

    private fun installedPackages(): Set<String> =
        installedApplications()
            .asSequence()
            .map { it.packageName }
            .filter(::isEligiblePackage)
            .toSet()

    private fun installedApplications(): List<ApplicationInfo> =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.getInstalledApplications(0)
            }
        }.getOrDefault(emptyList())

    private fun isEligiblePackage(packageName: String): Boolean =
        packageName.isNotBlank() &&
            packageName != appContext.packageName &&
            !packageName.startsWith("com.contentfilter.admin") &&
            packageName !in CriticalSystemPackages &&
            packageName != defaultHomePackage()

    private fun defaultHomePackage(): String? =
        runCatching {
            appContext.packageManager
                .resolveActivity(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                    PackageManager.MATCH_DEFAULT_ONLY,
                )
                ?.activityInfo
                ?.packageName
        }.getOrNull()

    private fun applyHidden(
        packageName: String,
        hidden: Boolean,
    ): Boolean =
        synchronized(stateLock) {
            val currentHidden = hiddenByGlosh()
            if (hidden == (packageName in currentHidden)) return@synchronized true
            val success =
                runCatching {
                    devicePolicyManager.setApplicationHidden(adminComponent, packageName, hidden)
                }.onFailure { error ->
                    Log.w(LogTag, "Device Owner visibility failed package=$packageName hidden=$hidden", error)
                }.getOrDefault(false)
            if (!success) return@synchronized false
            val next = currentHidden.toMutableSet()
            if (hidden) next += packageName else next -= packageName
            preferences.edit().putStringSet(HiddenPackagesKey, next).apply()
            Log.i(LogTag, "Device Owner visibility applied package=$packageName hidden=$hidden")
            true
        }

    private fun hiddenByGlosh(): Set<String> =
        preferences.getStringSet(HiddenPackagesKey, emptySet()).orEmpty().toSet()

    private companion object {
        const val LogTag = "DeviceOwnerAppVisibility"
        const val PreferencesName = "device_owner_app_visibility"
        const val HiddenPackagesKey = "hidden_by_glosh"
        val CriticalSystemPackages =
            setOf(
                "android",
                "com.android.settings",
                "com.android.systemui",
                "com.android.permissioncontroller",
                "com.google.android.permissioncontroller",
                "com.android.packageinstaller",
                "com.google.android.packageinstaller",
            )
    }
}
