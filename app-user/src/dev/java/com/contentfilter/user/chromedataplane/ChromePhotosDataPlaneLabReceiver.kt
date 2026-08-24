package com.contentfilter.user.chromedataplane

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.contentfilter.feature.accessibility.chromevisual.ChromePhotosProtectedSurfaceDiagnostics

class ChromePhotosDataPlaneLabReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (!context.packageName.endsWith(".dev")) return
        if (intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            ChromePhotosProtectedSurfaceDiagnostics.setMarkerEnabledForExplicitDevGate(false)
            ChromePhotosTrustedBootstrapBootGuard.blockChrome(context)
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
                else -> return
            }
        ContextCompat.startForegroundService(
            context,
            Intent(context, ChromePhotosDataPlaneLabService::class.java).setAction(serviceAction),
        )
    }

    companion object {
        const val ActionStart = "com.contentfilter.user.chromedataplane.command.START"
        const val ActionStop = "com.contentfilter.user.chromedataplane.command.STOP"
        const val ActionStatus = "com.contentfilter.user.chromedataplane.command.STATUS"
        const val ExtraSurfaceMarkerEnabled = "chrome_photos_surface_marker_enabled"
    }
}
