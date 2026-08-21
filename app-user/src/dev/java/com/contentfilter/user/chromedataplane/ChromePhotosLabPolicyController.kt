package com.contentfilter.user.chromedataplane

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Base64
import android.util.Log
import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import com.contentfilter.feature.accessibility.service.ProtectionDeviceAdminReceiver

internal data class ChromePhotosLabPolicyResult(
    val caFingerprint: String,
    val proxyPolicy: String,
)

/** Applies only a Chrome managed configuration; no global proxy is used. */
internal class ChromePhotosLabPolicyController(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val devicePolicyManager = appContext.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(appContext, ProtectionDeviceAdminReceiver::class.java)
    private val preferences =
        appContext.getSharedPreferences(
            ChromePhotosDataPlaneLabContract.PreferencesName,
            Context.MODE_PRIVATE,
        )

    fun verifyOwnerAndCleanOrphanedState() {
        check(appContext.packageName.endsWith(".dev")) { "DEV package required" }
        check(devicePolicyManager.isDeviceOwnerApp(appContext.packageName)) { "Device Owner required" }
        rollbackOwnedPolicyAndCa()
        val current =
            devicePolicyManager.getApplicationRestrictions(
                admin,
                ChromePhotosDataPlaneLabContract.ChromePackage,
            )
        check(current.isEmpty) { "Chrome already has managed restrictions; refusing to overwrite" }
    }

    fun apply(tls: ChromePhotosEphemeralTlsMaterial): ChromePhotosLabPolicyResult {
        check(devicePolicyManager.installCaCert(admin, tls.caCertificateDer)) { "DEV CA installation failed" }
        preferences.edit()
            .putString(
                ChromePhotosDataPlaneLabContract.KeyInstalledCaDer,
                Base64.encodeToString(tls.caCertificateDer, Base64.NO_WRAP),
            )
            .putString(ChromePhotosDataPlaneLabContract.KeyCaFingerprint, tls.caFingerprint)
            .commit()

        val proxySettings = ChromePhotosChromePolicy.proxySettingsJson()
        devicePolicyManager.setApplicationRestrictions(
            admin,
            ChromePhotosDataPlaneLabContract.ChromePackage,
            Bundle().apply { putString(ProxySettingsPolicy, proxySettings) },
        )
        val applied =
            devicePolicyManager.getApplicationRestrictions(
                admin,
                ChromePhotosDataPlaneLabContract.ChromePackage,
            ).getString(ProxySettingsPolicy)
        check(applied == proxySettings) { "Chrome proxy policy did not persist" }
        Log.i(LogTag, "policy=chrome-only proxy=fixed ca=${tls.caFingerprint.take(FingerprintLogLength)}")
        return ChromePhotosLabPolicyResult(
            caFingerprint = tls.caFingerprint,
            proxyPolicy = proxySettings,
        )
    }

    fun isApplied(caCertificateDer: ByteArray): Boolean {
        if (!devicePolicyManager.isDeviceOwnerApp(appContext.packageName)) return false
        val proxySettings =
            devicePolicyManager.getApplicationRestrictions(
                admin,
                ChromePhotosDataPlaneLabContract.ChromePackage,
            ).getString(ProxySettingsPolicy)
        return proxySettings == ChromePhotosChromePolicy.proxySettingsJson() &&
            devicePolicyManager.hasCaCertInstalled(admin, caCertificateDer)
    }

    fun rollbackOwnedPolicyAndCa() {
        preferences.edit()
            .putBoolean(ChromePhotosDataPlaneLabContract.KeyPresentationReady, false)
            .putBoolean(ChromePhotosDataPlaneLabContract.KeyActive, false)
            .commit()

        if (devicePolicyManager.isDeviceOwnerApp(appContext.packageName)) {
            val current =
                devicePolicyManager.getApplicationRestrictions(
                    admin,
                    ChromePhotosDataPlaneLabContract.ChromePackage,
                )
            if (current.containsKey(ProxySettingsPolicy)) {
                current.remove(ProxySettingsPolicy)
                devicePolicyManager.setApplicationRestrictions(
                    admin,
                    ChromePhotosDataPlaneLabContract.ChromePackage,
                    current,
                )
            }
            preferences.getString(ChromePhotosDataPlaneLabContract.KeyInstalledCaDer, null)
                ?.let { encoded ->
                    runCatching {
                        devicePolicyManager.uninstallCaCert(admin, Base64.decode(encoded, Base64.DEFAULT))
                    }.onFailure { error ->
                        Log.w(LogTag, "rollback=ca_failed error=${error.javaClass.simpleName}")
                    }
                }
        }
        preferences.edit()
            .remove(ChromePhotosDataPlaneLabContract.KeyInstalledCaDer)
            .remove(ChromePhotosDataPlaneLabContract.KeyCaFingerprint)
            .apply()
        Log.i(LogTag, "rollback=complete proxy=cleared ca=removed")
    }

    private companion object {
        const val ProxySettingsPolicy = "ProxySettings"
        const val FingerprintLogLength = 16
        const val LogTag = "ChromePhotosDataPlane"
    }
}
