package com.contentfilter.admin.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object AdminPushNotificationChannels {
    const val UrgentProtectionChannelId = "urgent_protection_alerts"
    const val AnnouncementsChannelId = "community_announcements"

    fun ensureUrgentProtectionChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel =
            NotificationChannel(
                UrgentProtectionChannelId,
                "Alertas urgentes",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Alertas cuando la protección de un dispositivo se desactiva"
                enableVibration(true)
            }
        manager.createNotificationChannel(channel)
    }

    fun ensureAnnouncementsChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(AnnouncementsChannelId, "Avisos", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Mensajes de la comunidad"
            },
        )
    }
}
