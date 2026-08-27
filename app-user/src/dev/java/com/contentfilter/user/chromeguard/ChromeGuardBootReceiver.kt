package com.contentfilter.user.chromeguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class ChromeGuardBootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (!context.packageName.endsWith(".dev")) return
        val action =
            when (intent.action) {
                Intent.ACTION_LOCKED_BOOT_COMPLETED -> ChromeGuardService.ActionLockedBoot
                Intent.ACTION_BOOT_COMPLETED -> ChromeGuardService.ActionBootCompleted
                Intent.ACTION_MY_PACKAGE_REPLACED -> ChromeGuardService.ActionPackageReplaced
                else -> return
            }
        ContextCompat.startForegroundService(
            context,
            Intent(context, ChromeGuardService::class.java).setAction(action),
        )
    }
}
