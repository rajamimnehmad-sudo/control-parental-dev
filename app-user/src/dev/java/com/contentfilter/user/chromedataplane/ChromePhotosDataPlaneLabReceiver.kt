package com.contentfilter.user.chromedataplane

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class ChromePhotosDataPlaneLabReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (!context.packageName.endsWith(".dev")) return
        val serviceAction =
            when (intent.action) {
                ActionStart -> ChromePhotosDataPlaneLabService.ActionStart
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
    }
}
