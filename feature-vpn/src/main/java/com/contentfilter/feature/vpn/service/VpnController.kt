package com.contentfilter.feature.vpn.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

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

    fun isRunning(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .getBoolean(KeyIsRunning, false)

    fun observeRunning(context: Context): Flow<Boolean> =
        callbackFlow {
            val appContext = context.applicationContext
            trySend(isRunning(appContext))
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context?,
                        intent: Intent?,
                    ) {
                        if (intent?.action == ActionStateChanged) {
                            trySend(isRunning(appContext))
                        }
                    }
                }
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                IntentFilter(ActionStateChanged),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            awaitClose { appContext.unregisterReceiver(receiver) }
        }

    internal fun markStarted(context: Context) {
        setRunning(context, true)
    }

    internal fun markStopped(
        context: Context,
        reason: String,
    ) {
        val appContext = context.applicationContext
        appContext
            .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KeyIsRunning, false)
            .putString(KeyLastStopReason, reason)
            .apply()
        sendStateChanged(appContext)
    }

    private fun serviceIntent(
        context: Context,
        action: String,
    ): Intent =
        Intent(context, FilterVpnService::class.java).apply {
            this.action = action
        }

    private fun setRunning(
        context: Context,
        isRunning: Boolean,
    ) {
        val appContext = context.applicationContext
        appContext
            .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KeyIsRunning, isRunning)
            .apply()
        sendStateChanged(appContext)
    }

    private fun sendStateChanged(context: Context) {
        context.sendBroadcast(
            Intent(ActionStateChanged).setPackage(context.packageName),
        )
    }

    private const val PreferencesName = "vpn_runtime_state"
    private const val KeyIsRunning = "is_running"
    private const val KeyLastStopReason = "last_stop_reason"
    private const val ActionStateChanged = "com.contentfilter.feature.vpn.STATE_CHANGED"
}
