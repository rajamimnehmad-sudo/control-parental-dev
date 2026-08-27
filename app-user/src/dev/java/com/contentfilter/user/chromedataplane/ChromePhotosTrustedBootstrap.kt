package com.contentfilter.user.chromedataplane

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import com.contentfilter.feature.accessibility.service.ProtectionDeviceAdminReceiver
import com.contentfilter.user.chromeguard.ChromeSuspensionAuthority
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

internal data class ChromePhotosTrustedBootstrapState(
    val resetGeneration: Int,
    val completeGeneration: Int,
    val resetCount: Int,
)

internal data class ChromePhotosTrustedBootstrapHealth(
    val proxyHealthy: Boolean,
    val policyConfirmed: Boolean,
    val vpnConfirmed: Boolean,
    val gloshiaReady: Boolean,
    val accessibilityBound: Boolean,
) {
    val allReady: Boolean
        get() =
            proxyHealthy &&
                policyConfirmed &&
                vpnConfirmed &&
                gloshiaReady &&
                accessibilityBound
}

internal enum class ChromePhotosTrustedBootstrapAction {
    ResetRequired,
    WaitForHealth,
    ReleaseChrome,
}

internal object ChromePhotosTrustedBootstrapPolicy {
    fun nextAction(
        state: ChromePhotosTrustedBootstrapState,
        health: ChromePhotosTrustedBootstrapHealth,
    ): ChromePhotosTrustedBootstrapAction =
        when {
            state.resetGeneration != ChromePhotosDataPlaneLabContract.TrustedBootstrapGeneration ->
                ChromePhotosTrustedBootstrapAction.ResetRequired
            !health.allReady -> ChromePhotosTrustedBootstrapAction.WaitForHealth
            else -> ChromePhotosTrustedBootstrapAction.ReleaseChrome
        }

    fun failCloseReason(
        previouslyReleased: Boolean,
        health: ChromePhotosTrustedBootstrapHealth,
    ): String? {
        if (!previouslyReleased || health.allReady) return null
        return when {
            !health.proxyHealthy -> "proxy_lost"
            !health.policyConfirmed -> "policy_lost"
            !health.vpnConfirmed -> "vpn_lost"
            !health.gloshiaReady -> "gloshia_lost"
            !health.accessibilityBound -> "accessibility_lost"
            else -> null
        }
    }
}

/** DEV-only one-time reset and Chrome availability authority for the managed lab. */
internal class ChromePhotosTrustedBootstrapController(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val devicePolicyManager = appContext.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(appContext, ProtectionDeviceAdminReceiver::class.java)
    private val suspensionAuthority = ChromeSuspensionAuthority(appContext)
    private val preferences =
        appContext.getSharedPreferences(
            ChromePhotosDataPlaneLabContract.PreferencesName,
            Context.MODE_PRIVATE,
        )

    fun requireDevOwnerAndBlockChrome(reason: String) {
        check(appContext.packageName.endsWith(".dev")) { "DEV package required" }
        check(devicePolicyManager.isDeviceOwnerApp(appContext.packageName)) { "Device Owner required" }
        check(suspensionAuthority.ensureSuspended(reason)) { "Chrome suspension failed" }
        Log.i(LogTag, "bootstrap=chrome_blocked reason=${reason.take(MaxReasonLength)}")
    }

    suspend fun ensureInitialReset() {
        val current = state()
        if (current.resetGeneration == ChromePhotosDataPlaneLabContract.TrustedBootstrapGeneration) {
            Log.i(
                LogTag,
                "bootstrap=chrome_reset_skipped generation=${current.resetGeneration} " +
                    "resetCount=${current.resetCount}",
            )
            return
        }
        check(isChromeSuspended()) { "Chrome must remain suspended before reset" }
        val result = CompletableDeferred<Boolean>()
        devicePolicyManager.clearApplicationUserData(
            admin,
            ChromePhotosDataPlaneLabContract.ChromePackage,
            appContext.mainExecutor,
        ) { packageName, succeeded ->
            if (packageName == ChromePhotosDataPlaneLabContract.ChromePackage) {
                result.complete(succeeded)
            }
        }
        check(withTimeout(ClearDataTimeoutMillis) { result.await() }) { "Chrome full reset failed" }
        check(isChromeSuspended()) { "Chrome suspension lost during reset" }
        val previous = state()
        check(
            preferences.edit()
                .putInt(
                    ChromePhotosDataPlaneLabContract.KeyTrustedBootstrapResetGeneration,
                    ChromePhotosDataPlaneLabContract.TrustedBootstrapGeneration,
                )
                .putInt(ChromePhotosDataPlaneLabContract.KeyTrustedBootstrapCompleteGeneration, 0)
                .putInt(ChromePhotosDataPlaneLabContract.KeyTrustedBootstrapResetCount, previous.resetCount + 1)
                .commit(),
        ) { "Chrome reset state did not persist" }
        Log.i(
            LogTag,
            "bootstrap=chrome_reset_complete generation=${ChromePhotosDataPlaneLabContract.TrustedBootstrapGeneration} " +
                "resetCount=${previous.resetCount + 1}",
        )
    }

    fun markChromeReleaseEligibleIfHealthy(health: ChromePhotosTrustedBootstrapHealth): Boolean {
        val current = state()
        if (ChromePhotosTrustedBootstrapPolicy.nextAction(current, health) !=
            ChromePhotosTrustedBootstrapAction.ReleaseChrome
        ) {
            return false
        }
        if (current.completeGeneration == ChromePhotosDataPlaneLabContract.TrustedBootstrapGeneration) return true
        if (!preferences.edit()
                .putInt(
                    ChromePhotosDataPlaneLabContract.KeyTrustedBootstrapCompleteGeneration,
                    ChromePhotosDataPlaneLabContract.TrustedBootstrapGeneration,
                )
                .commit()
        ) {
            return false
        }
        Log.i(
            LogTag,
            "bootstrap=chrome_release_eligible generation=${ChromePhotosDataPlaneLabContract.TrustedBootstrapGeneration} " +
                "health=verified authority=chrome_guard",
        )
        return true
    }

    fun state(): ChromePhotosTrustedBootstrapState =
        ChromePhotosTrustedBootstrapState(
            resetGeneration =
                preferences.getInt(
                    ChromePhotosDataPlaneLabContract.KeyTrustedBootstrapResetGeneration,
                    0,
                ),
            completeGeneration =
                preferences.getInt(
                    ChromePhotosDataPlaneLabContract.KeyTrustedBootstrapCompleteGeneration,
                    0,
                ),
            resetCount = preferences.getInt(ChromePhotosDataPlaneLabContract.KeyTrustedBootstrapResetCount, 0),
        )

    fun isChromeSuspended(): Boolean = suspensionAuthority.isSuspended() == true

    fun preserveAcrossSessionReset(editor: SharedPreferences.Editor): SharedPreferences.Editor {
        val current = state()
        return editor
            .putInt(
                ChromePhotosDataPlaneLabContract.KeyTrustedBootstrapResetGeneration,
                current.resetGeneration,
            )
            .putInt(
                ChromePhotosDataPlaneLabContract.KeyTrustedBootstrapCompleteGeneration,
                current.completeGeneration,
            )
            .putInt(ChromePhotosDataPlaneLabContract.KeyTrustedBootstrapResetCount, current.resetCount)
    }

    private companion object {
        const val ClearDataTimeoutMillis = 180_000L
        const val MaxReasonLength = 80
        const val LogTag = "ChromePhotosDataPlane"
    }
}

/** Direct-boot guard: no credential-encrypted state is read before the user unlocks. */
internal object ChromePhotosTrustedBootstrapBootGuard {
    fun blockChrome(context: Context): Boolean {
        val appContext = context.applicationContext
        if (!appContext.packageName.endsWith(".dev")) return false
        val blocked = ChromeSuspensionAuthority(appContext).ensureSuspended("boot_guard")
        Log.i(LogTag, "bootstrap=locked_boot_guard blocked=$blocked")
        return blocked
    }

    private const val LogTag = "ChromePhotosDataPlane"
}
