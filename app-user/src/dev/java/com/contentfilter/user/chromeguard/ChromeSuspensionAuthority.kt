package com.contentfilter.user.chromeguard

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.contentfilter.feature.accessibility.service.ProtectionDeviceAdminReceiver

internal class ChromeSuspensionAuthority(
    context: Context,
) : ChromeSuspensionPort {
    private val appContext = context.applicationContext
    private val devicePolicyManager = appContext.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(appContext, ProtectionDeviceAdminReceiver::class.java)

    override fun ensureSuspended(reason: String): Boolean = setSuspended(true, reason)

    override fun ensureReleased(): Boolean = setSuspended(false, "lease_current")

    fun isSuspended(): Boolean? =
        runCatching {
            appContext.packageManager.isPackageSuspended(ChromeGuardContract.ChromePackage)
        }.getOrNull()

    fun verifyDevOwner(): Boolean =
        appContext.packageName.endsWith(".dev") && devicePolicyManager.isDeviceOwnerApp(appContext.packageName)

    private fun setSuspended(
        suspended: Boolean,
        reason: String,
    ): Boolean {
        if (!verifyDevOwner()) return false
        if (!suspensionMutationRequired(isSuspended(), suspended)) return true
        val succeeded =
            boundedSuspensionAttempt(MaxAttempts) { attempt ->
                val changed =
                    runCatching {
                        devicePolicyManager.setPackagesSuspended(
                            admin,
                            arrayOf(ChromeGuardContract.ChromePackage),
                            suspended,
                        ).isEmpty()
                    }.getOrDefault(false)
                val verified = isSuspended() == suspended
                if (!changed || !verified) return@boundedSuspensionAttempt false
                Log.i(
                    LogTag,
                    "action=${if (suspended) "suspend" else "release"} verified=true " +
                        "reason=${reason.take(MaxReasonLength)} attempt=${attempt + 1}",
                )
                true
            }
        if (succeeded) return true
        Log.e(
            LogTag,
            "action=${if (suspended) "suspend" else "release"} verified=false " +
                "reason=${reason.take(MaxReasonLength)}",
        )
        return false
    }

    private companion object {
        const val MaxAttempts = 3
        const val MaxReasonLength = 80
        const val LogTag = "ChromeProcessGuard"
    }
}

internal fun suspensionMutationRequired(
    current: Boolean?,
    desired: Boolean,
): Boolean = current != desired

internal inline fun boundedSuspensionAttempt(
    maxAttempts: Int,
    attempt: (index: Int) -> Boolean,
): Boolean {
    require(maxAttempts > 0)
    repeat(maxAttempts) { index ->
        if (attempt(index)) return true
        if (index + 1 < maxAttempts) Thread.sleep(50L)
    }
    return false
}
