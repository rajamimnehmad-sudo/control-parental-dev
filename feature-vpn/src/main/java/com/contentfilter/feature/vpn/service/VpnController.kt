package com.contentfilter.feature.vpn.service

import android.content.Context
import android.content.Intent
import android.net.VpnService

object VpnController {
    fun prepareIntent(context: Context): Intent? = VpnService.prepare(context)

    fun start(context: Context) {
        DevProtectionMode.setProtectionDisabled(context, false)
        context.startForegroundService(serviceIntent(context, FilterVpnService.ActionStart))
    }

    fun stop(context: Context) {
        context.startService(serviceIntent(context, FilterVpnService.ActionStop))
    }

    fun disableDevProtection(context: Context) {
        DevProtectionMode.setProtectionDisabled(context, true)
        stop(context)
    }

    fun enableDevProtection(context: Context) {
        DevProtectionMode.setProtectionDisabled(context, false)
    }

    fun isDevProtectionAvailable(context: Context): Boolean = DevProtectionMode.isAvailable(context)

    fun isDevProtectionDisabled(context: Context): Boolean = DevProtectionMode.isProtectionDisabled(context)

    private fun serviceIntent(
        context: Context,
        action: String,
    ): Intent =
        Intent(context, FilterVpnService::class.java).apply {
            this.action = action
        }
}
