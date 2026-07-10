package com.contentfilter.admin.push

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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
        scope.launch { pushNotificationRepository.registerAdminToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (!notificationsAllowed()) return
        AdminPushNotificationChannels.ensureUrgentProtectionChannel(this)
        val title = message.notification?.title ?: "Protección incompleta"
        val body = message.notification?.body ?: "Un dispositivo necesita atención."
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
                .setAutoCancel(true)
                .build()
        getSystemService(NotificationManager::class.java)
            ?.notify(ProtectionAlertNotificationId, notification)
    }

    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val ProtectionAlertNotificationId = 7001
    }
}
