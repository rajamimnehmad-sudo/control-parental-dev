package com.contentfilter.feature.vpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build

class VpnNotificationFactory(
    private val context: Context,
) {
    fun create(): Notification {
        ensureChannel()
        val builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, ChannelId)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }
        return builder
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Protección activa")
            .setContentText("El dispositivo está protegido")
            .setContentIntent(openUserAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setDefaults(0)
            .setSound(null)
            .setVibrate(null)
            .setPriority(Notification.PRIORITY_LOW)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun openUserAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(ActionOpenUserApp)
                .setPackage(context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel =
            NotificationChannel(
                ChannelId,
                "Protección",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Estado de protección del dispositivo"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, AudioAttributes.Builder().build())
            }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ChannelId = "content_filter_vpn"
        const val NotificationId = 2001
        private const val ActionOpenUserApp = "com.contentfilter.action.OPEN_USER_APP"
    }
}
