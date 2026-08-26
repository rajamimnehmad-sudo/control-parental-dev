package com.contentfilter.user.chromeextension

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class ChromeExtensionPolicyLabReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (!context.packageName.endsWith(".dev")) return
        val action = intent.action ?: return
        if (action !in SupportedActions) return
        ContextCompat.startForegroundService(
            context,
            Intent(context, ChromeExtensionPolicyLabService::class.java)
                .setAction(action)
                .putExtras(intent),
        )
    }

    companion object {
        const val ActionSnapshot = "com.contentfilter.user.chromeextension.command.SNAPSHOT"
        const val ActionApply = "com.contentfilter.user.chromeextension.command.APPLY"
        const val ActionHeartbeat = "com.contentfilter.user.chromeextension.command.HEARTBEAT"
        const val ActionStatus = "com.contentfilter.user.chromeextension.command.STATUS"
        const val ActionRestore = "com.contentfilter.user.chromeextension.command.RESTORE"
        const val ExtraExtensionId = "extension_id"
        const val ExtraUpdateUrl = "update_url"
        const val ExtraLeaseMillis = "lease_millis"

        private val SupportedActions =
            setOf(ActionSnapshot, ActionApply, ActionHeartbeat, ActionStatus, ActionRestore)
    }
}
