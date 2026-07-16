package com.contentfilter.admin.push

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.contentfilter.admin.MainActivity
import com.contentfilter.admin.R
import com.contentfilter.core.domain.repository.PushNotificationRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AdminFirebaseMessagingService : FirebaseMessagingService() {
    @Inject lateinit var pushNotificationRepository: PushNotificationRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        if (token.isBlank()) return
        scope.launch { pushNotificationRepository.registerDeviceToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (!notificationsAllowed()) return
        if (message.data[DataTypeKey] == AnnouncementType) {
            showAnnouncement(message)
            return
        }
        AdminPushNotificationChannels.ensureUrgentProtectionChannel(this)
        val title = message.data[TitleKey] ?: message.notification?.title ?: "Protección incompleta"
        val body = message.data[BodyKey] ?: message.notification?.body ?: "Un dispositivo necesita atención."
        val payload = parseAdminProtectionAlertPayload(message.data)
        val notificationId = notificationId(payload?.eventId ?: message.messageId)
        val openAdminIntent =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                message.data.forEach { (key, value) -> putExtra(key, value) }
            }
        val contentIntent =
            PendingIntent.getActivity(
                this,
                notificationId,
                openAdminIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat.Builder(
                this,
                AdminPushNotificationChannels.UrgentProtectionChannelId,
            )
                .setSmallIcon(R.drawable.ic_admin_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setGroup(ProtectionAlertNotificationGroup)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build()
        getSystemService(NotificationManager::class.java)
            ?.notify(notificationId, notification)
    }

    private fun showAnnouncement(message: RemoteMessage) {
        AdminPushNotificationChannels.ensureAnnouncementsChannel(this)
        val title = message.data[TitleKey] ?: "Nuevo aviso"
        val body = message.data[BodyKey] ?: "Hay un nuevo mensaje para tu comunidad."
        val id = notificationId(message.data[AnnouncementIdKey] ?: message.messageId)
        val intent =
            Intent(this, MainActivity::class.java).apply {
                action = OpenAnnouncementsAction
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        val contentIntent =
            PendingIntent.getActivity(
                this,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat.Builder(this, AdminPushNotificationChannels.AnnouncementsChannelId)
                .setSmallIcon(R.drawable.ic_admin_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build()
        getSystemService(NotificationManager::class.java)?.notify(id, notification)
    }

    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val ProtectionAlertNotificationGroup = "device_protection_alerts"

        fun notificationId(value: String?): Int =
            value
                ?.takeIf(String::isNotBlank)
                ?.hashCode()
                ?.and(Int.MAX_VALUE)
                ?.takeIf { it != 0 }
                ?: 7001
    }
}

const val AnnouncementType = "announcement"
const val AnnouncementIdKey = "announcement_id"
const val OpenAnnouncementsAction = "com.contentfilter.admin.action.OPEN_ANNOUNCEMENTS"
