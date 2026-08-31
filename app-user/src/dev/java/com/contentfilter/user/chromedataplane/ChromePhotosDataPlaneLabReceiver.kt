package com.contentfilter.user.chromedataplane

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat
import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import com.contentfilter.feature.accessibility.chromevisual.ChromeMediaShieldActiveDocumentLabControl
import com.contentfilter.feature.accessibility.chromevisual.ChromePhotosProtectedSurfaceDiagnostics
import com.contentfilter.feature.vpn.service.VpnController
import com.contentfilter.user.chromeguard.ChromeGuardService

class ChromePhotosDataPlaneLabReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (!context.packageName.endsWith(".dev")) return
        if (intent.action == ActionActiveDocumentReplay) {
            val result = ChromeMediaShieldActiveDocumentLabControl.replayConsumedPresent()
            setResultData(result)
            Log.i(ActiveDocumentLogTag, "action=ACTIVE_DOCUMENT_REPLAY $result")
            return
        }
        if (intent.action in ActiveDocumentHoldActions) {
            val result =
                when (intent.action) {
                    ActionActiveDocumentHoldArm ->
                        ChromeMediaShieldActiveDocumentLabControl.arm(
                            intent.getStringExtra(ExtraActiveDocumentCaseId),
                            intent.getStringExtra(ExtraActiveDocumentHoldStage),
                            intent.getStringExtra(ExtraActiveDocumentHoldNonce),
                        )
                    ActionActiveDocumentHoldRelease ->
                        ChromeMediaShieldActiveDocumentLabControl.release(
                            intent.getStringExtra(ExtraActiveDocumentCaseId),
                            intent.getStringExtra(ExtraActiveDocumentHoldStage),
                            intent.getStringExtra(ExtraActiveDocumentHoldNonce),
                        )
                    else ->
                        ChromeMediaShieldActiveDocumentLabControl.cancel(
                            intent.getStringExtra(ExtraActiveDocumentCaseId),
                            intent.getStringExtra(ExtraActiveDocumentHoldStage),
                            intent.getStringExtra(ExtraActiveDocumentHoldNonce),
                        )
                }
            setResultData(result)
            Log.i(ActiveDocumentLogTag, "action=${intent.action?.substringAfterLast('.')} $result")
            return
        }
        when (intent.action) {
            ActionTrustedBootstrapResetArm -> {
                val armed = ChromePhotosTrustedBootstrapController(context).armOneTimeResetForExplicitDevGate()
                setResultData(if (armed) "chrome_reset_armed" else "chrome_reset_already_armed")
                return
            }
            ActionMainProcessKill -> {
                Process.killProcess(Process.myPid())
                return
            }
            ActionMainJavaCrash -> {
                Handler(Looper.getMainLooper()).post {
                    throw IllegalStateException("chrome_guard_dev_main_crash")
                }
                return
            }
            ActionGuardProcessKill -> {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ChromeGuardService::class.java).setAction(ChromeGuardService.ActionDevKillSelf),
                )
                return
            }
            ActionGuardStatus -> {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ChromeGuardService::class.java).setAction(ChromeGuardService.ActionStatus),
                )
                return
            }
            ActionPrepareUpdate -> {
                ChromePhotosTrustedBootstrapBootGuard.blockChrome(context)
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ChromeGuardService::class.java)
                        .setAction(ChromeGuardService.ActionPackageReplaced),
                )
                return
            }
        }
        if (intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            ChromePhotosProtectedSurfaceDiagnostics.setMarkerEnabledForExplicitDevGate(false)
            ChromePhotosTrustedBootstrapBootGuard.blockChrome(context)
            return
        }
        if (intent.action == ActionTransportStatus) {
            VpnController.logDevTransportStatus(context)
            return
        }
        if (intent.action == ActionStatus) {
            Log.i(ActiveDocumentLogTag, ChromeMediaShieldActiveDocumentLabControl.status())
        }
        if (intent.action == ActionTransportStress) {
            VpnController.runDevTransportStress(
                context,
                intent.getIntExtra(ExtraTransportStressCycles, DefaultTransportStressCycles),
            )
            return
        }
        if (intent.action == ActionFullTunnelStart || intent.action == ActionFullTunnelStop) {
            VpnController.setDevFullTunnelGate(context, intent.action == ActionFullTunnelStart)
            return
        }
        when (intent.action) {
            ActionStart ->
                ChromePhotosProtectedSurfaceDiagnostics.setMarkerEnabledForExplicitDevGate(
                    intent.getBooleanExtra(ExtraSurfaceMarkerEnabled, false),
                )
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            ActionStop,
            -> ChromePhotosProtectedSurfaceDiagnostics.setMarkerEnabledForExplicitDevGate(false)
        }
        val serviceAction =
            when (intent.action) {
                ActionStart -> ChromePhotosDataPlaneLabService.ActionStart
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                -> ChromePhotosDataPlaneLabService.ActionStart
                ActionStop -> ChromePhotosDataPlaneLabService.ActionStop
                ActionStatus -> ChromePhotosDataPlaneLabService.ActionStatus
                ActionAuditMark -> ChromePhotosDataPlaneLabService.ActionAuditMark
                else -> return
            }
        val serviceIntent = Intent(context, ChromePhotosDataPlaneLabService::class.java).setAction(serviceAction)
        if (intent.action == ActionStart) {
            serviceIntent
                .putExtra(
                    ExtraUdpFixtureGateEnabled,
                    intent.getBooleanExtra(ExtraUdpFixtureGateEnabled, false),
                )
                .putExtra(ExtraUdpFixtureAddress, intent.getStringExtra(ExtraUdpFixtureAddress))
                .putExtra(ExtraUdpFixturePort, intent.getIntExtra(ExtraUdpFixturePort, 0))
                .putExtra(
                    ExtraUdpFixtureMalformedProbeEnabled,
                    intent.getBooleanExtra(ExtraUdpFixtureMalformedProbeEnabled, false),
                )
                .putExtra(
                    ExtraFullTunnelDevGateEnabled,
                    intent.getBooleanExtra(ExtraFullTunnelDevGateEnabled, false),
                )
                .putExtra(
                    ExtraReplaceAllNetworkVisuals,
                    intent.getBooleanExtra(ExtraReplaceAllNetworkVisuals, false),
                )
                .putExtra(
                    ExtraStockMediaAuthorityEnabled,
                    intent.getBooleanExtra(ExtraStockMediaAuthorityEnabled, false),
                )
                .putExtra(
                    ExtraDocumentSelfShieldEnabled,
                    intent.getBooleanExtra(ExtraDocumentSelfShieldEnabled, false),
                )
        }
        if (intent.action == ActionAuditMark) {
            serviceIntent
                .putExtra(
                    ChromePhotosDataPlaneLabService.ExtraAuditStateLabel,
                    intent.getStringExtra(ExtraAuditStateLabel),
                )
                .putExtra(
                    ChromePhotosDataPlaneLabService.ExtraAuditNewNavigation,
                    intent.getBooleanExtra(ExtraAuditNewNavigation, false),
                )
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    companion object {
        const val ActionStart = "com.contentfilter.user.chromedataplane.command.START"
        const val ActionStop = "com.contentfilter.user.chromedataplane.command.STOP"
        const val ActionStatus = "com.contentfilter.user.chromedataplane.command.STATUS"
        const val ActionAuditMark = "com.contentfilter.user.chromedataplane.command.AUDIT_MARK"
        const val ActionTransportStatus = "com.contentfilter.user.chromedataplane.command.TRANSPORT_STATUS"
        const val ActionTransportStress = "com.contentfilter.user.chromedataplane.command.TRANSPORT_STRESS"
        const val ActionFullTunnelStart = "com.contentfilter.user.chromedataplane.command.FULL_TUNNEL_START"
        const val ActionFullTunnelStop = "com.contentfilter.user.chromedataplane.command.FULL_TUNNEL_STOP"
        const val ActionMainProcessKill = "com.contentfilter.user.chromedataplane.command.MAIN_PROCESS_KILL"
        const val ActionMainJavaCrash = "com.contentfilter.user.chromedataplane.command.MAIN_JAVA_CRASH"
        const val ActionGuardProcessKill = "com.contentfilter.user.chromedataplane.command.GUARD_PROCESS_KILL"
        const val ActionGuardStatus = "com.contentfilter.user.chromedataplane.command.GUARD_STATUS"
        const val ActionPrepareUpdate = "com.contentfilter.user.chromedataplane.command.PREPARE_UPDATE"
        const val ActionTrustedBootstrapResetArm =
            "com.contentfilter.user.chromedataplane.command.TRUSTED_BOOTSTRAP_RESET_ARM"
        const val ActionActiveDocumentHoldArm =
            "com.contentfilter.user.chromedataplane.command.ACTIVE_DOCUMENT_HOLD_ARM"
        const val ActionActiveDocumentHoldRelease =
            "com.contentfilter.user.chromedataplane.command.ACTIVE_DOCUMENT_HOLD_RELEASE"
        const val ActionActiveDocumentHoldCancel =
            "com.contentfilter.user.chromedataplane.command.ACTIVE_DOCUMENT_HOLD_CANCEL"
        const val ActionActiveDocumentReplay = ChromePhotosDataPlaneLabContract.ActionActiveDocumentReplay
        const val ExtraActiveDocumentCaseId = "active_document_case_id"
        const val ExtraActiveDocumentHoldStage = "active_document_hold_stage"
        const val ExtraActiveDocumentHoldNonce = "active_document_hold_nonce"
        const val ExtraSurfaceMarkerEnabled = "chrome_photos_surface_marker_enabled"
        const val ExtraAuditStateLabel = "chrome_coverage_audit_state_label"
        const val ExtraAuditNewNavigation = "chrome_coverage_audit_new_navigation"
        const val ExtraTransportStressCycles = "transport_stress_cycles"
        const val ExtraUdpFixtureGateEnabled = ChromePhotosDataPlaneLabContract.KeyUdpFixtureGateEnabled
        const val ExtraUdpFixtureAddress = ChromePhotosDataPlaneLabContract.KeyUdpFixtureAddress
        const val ExtraUdpFixturePort = ChromePhotosDataPlaneLabContract.KeyUdpFixturePort
        const val ExtraUdpFixtureMalformedProbeEnabled =
            ChromePhotosDataPlaneLabContract.KeyUdpFixtureMalformedProbeEnabled
        const val ExtraFullTunnelDevGateEnabled = "full_tunnel_dev_gate_enabled"
        const val ExtraReplaceAllNetworkVisuals = "replace_all_network_visuals"
        const val ExtraStockMediaAuthorityEnabled = ChromePhotosDataPlaneLabContract.KeyStockMediaAuthorityEnabled
        const val ExtraDocumentSelfShieldEnabled = ChromePhotosDataPlaneLabContract.KeyDocumentSelfShieldEnabled
        private const val DefaultTransportStressCycles = 100
        private const val ActiveDocumentLogTag = "ChromeMediaShieldActiveDocument"
        private val ActiveDocumentHoldActions =
            setOf(
                ActionActiveDocumentHoldArm,
                ActionActiveDocumentHoldRelease,
                ActionActiveDocumentHoldCancel,
            )
    }
}
