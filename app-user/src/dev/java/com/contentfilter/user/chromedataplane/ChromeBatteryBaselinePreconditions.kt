package com.contentfilter.user.chromedataplane

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.provider.Settings
import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentHandshakeBridge
import com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentAuthorityRegistry
import com.contentfilter.core.domain.chrome.ChromeMediaShieldParserBarrierBridge
import com.contentfilter.core.domain.chrome.ChromeMediaShieldReadyHandshakeBridge
import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneRuntimeAttestation
import com.contentfilter.feature.vpn.service.VpnController
import com.contentfilter.user.chromeguard.ChromeBatteryBaselinePreconditionSnapshot
import com.contentfilter.user.chromeguard.ChromeGuardContract

internal object ChromeBatteryBaselinePreconditions {
    fun capture(context: Context): ChromeBatteryBaselinePreconditionSnapshot {
        val appContext = context.applicationContext
        val preferences =
            appContext.getSharedPreferences(
                ChromePhotosDataPlaneLabContract.PreferencesName,
                Context.MODE_PRIVATE,
            )
        val runtime = ChromePhotosDataPlaneRuntimeAttestation.snapshot()
        val documents = ChromeMediaShieldDocumentAuthorityRegistry.snapshot()
        val ready = ChromeMediaShieldReadyHandshakeBridge.snapshot()
        val parser = ChromeMediaShieldParserBarrierBridge.snapshot()
        val activeDocument = ChromeMediaShieldActiveDocumentHandshakeBridge.snapshot()
        val policy = ChromePhotosLabPolicyController(appContext).batteryBaselineResidualState()
        val devicePolicyManager = appContext.getSystemService(DevicePolicyManager::class.java)
        val chromePackageExact =
            runCatching {
                appContext.packageManager.getApplicationInfo(ChromeGuardContract.ChromePackage, 0).packageName ==
                    ChromeGuardContract.ChromePackage
            }.getOrDefault(false)

        return ChromeBatteryBaselinePreconditionSnapshot(
            devPackage = appContext.packageName.endsWith(".dev"),
            deviceOwner = devicePolicyManager.isDeviceOwnerApp(appContext.packageName),
            chromePackageExact = chromePackageExact,
            chromeSuspended =
                runCatching {
                    appContext.packageManager.isPackageSuspended(ChromeGuardContract.ChromePackage)
                }.getOrDefault(false),
            labInactive = !preferences.getBoolean(ChromePhotosDataPlaneLabContract.KeyActive, false),
            presentationNotReady =
                !preferences.getBoolean(ChromePhotosDataPlaneLabContract.KeyPresentationReady, false),
            realWebAuthorityClosed =
                !preferences.getBoolean(ChromePhotosDataPlaneLabContract.KeyRealWebScopeConfirmed, false) &&
                    runtime.sessionId.isBlank() &&
                    !runtime.proxyHealthy &&
                    !runtime.policyConfirmed &&
                    !runtime.vpnConfirmed &&
                    !runtime.realWebScopeConfirmed,
            labProxyAbsent = policy.labProxyAbsent,
            globalProxyAbsent =
                Settings.Global.getString(appContext.contentResolver, GlobalHttpProxySetting)
                    .isNullOrBlank(),
            ephemeralCaAbsent = policy.ephemeralCaAbsent,
            fullTunnelAbsent = !VpnController.isDevFullTunnelGateActive(appContext),
            outstandingAuthorityTokensZero =
                documents.issuedDocuments == 0 &&
                    documents.readyClaims == 0 &&
                    ready.pendingRequests == 0 &&
                    parser.pendingRequests == 0 &&
                    activeDocument.pendingRequests == 0,
            svgRegistryClosed = ChromeOriginalUiSvgRegistry.activeRegistryCount() == 0,
            resetCount =
                preferences.getInt(
                    ChromePhotosDataPlaneLabContract.KeyTrustedBootstrapResetCount,
                    0,
                ),
        )
    }

    private const val GlobalHttpProxySetting = "http_proxy"
}
