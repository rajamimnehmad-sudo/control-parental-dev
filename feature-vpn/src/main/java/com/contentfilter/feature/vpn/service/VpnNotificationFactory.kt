package com.contentfilter.feature.vpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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
    }
}
