package com.contentfilter.user.push

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.contentfilter.core.domain.repository.PushNotificationRepository
import com.contentfilter.user.MainActivity
import com.contentfilter.user.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class UserFirebaseMessagingService : FirebaseMessagingService() {
    @Inject lateinit var repository: PushNotificationRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        if (token.isNotBlank()) scope.launch { repository.registerDeviceToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["type"] != "announcement" || !allowed()) return
        UserPushNotificationChannels.ensure(this)
        val title = message.data["title"] ?: "Nuevo aviso"
        val body = message.data["body"] ?: "Hay un nuevo mensaje para tu comunidad."
        val id = (message.data["announcement_id"] ?: message.messageId ?: "announcement").hashCode().and(Int.MAX_VALUE)
        val intent =
            Intent(this, MainActivity::class.java).apply {
                action = OpenAnnouncementsAction
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        val pending =
            PendingIntent.getActivity(
                this,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat.Builder(this, UserPushNotificationChannels.ChannelId)
                .setSmallIcon(R.drawable.ic_user_launcher).setContentTitle(title).setContentText(body)
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(body),
                ).setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(Notification.CATEGORY_MESSAGE).setContentIntent(pending).setAutoCancel(true).build()
        getSystemService(NotificationManager::class.java)?.notify(id, notification)
    }

    private fun allowed() =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

object UserPushNotificationChannels {
    const val ChannelId = "community_announcements"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(ChannelId, "Avisos", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Mensajes de la comunidad"
            },
        )
    }
}

const val OpenAnnouncementsAction = "com.contentfilter.user.action.OPEN_ANNOUNCEMENTS"
