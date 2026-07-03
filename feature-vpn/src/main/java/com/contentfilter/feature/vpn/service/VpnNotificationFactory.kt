package com.contentfilter.feature.vpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Content Filter")
            .setContentText("Proteccion DNS local activa")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel =
            NotificationChannel(
                ChannelId,
                "Proteccion local",
                NotificationManager.IMPORTANCE_LOW,
            )
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ChannelId = "content_filter_vpn"
        const val NotificationId = 2001
    }
}
