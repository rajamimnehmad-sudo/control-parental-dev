package com.contentfilter.feature.vpn.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class VpnStartupReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val permissionGranted = VpnController.prepareIntent(context) == null
        if (
            VpnStartupPolicy.shouldStart(
                action = intent.action,
                permissionGranted = permissionGranted,
                protectionDisabled = VpnController.isDevProtectionDisabled(context),
            )
        ) {
            VpnController.start(context)
        }
    }
}

internal object VpnStartupPolicy {
    fun shouldStart(
        action: String?,
        permissionGranted: Boolean,
        protectionDisabled: Boolean,
    ): Boolean =
        action in SupportedActions &&
            permissionGranted &&
            !protectionDisabled

    private val SupportedActions =
        setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
}
