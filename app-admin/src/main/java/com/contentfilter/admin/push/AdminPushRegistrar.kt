package com.contentfilter.admin.push

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.contentfilter.admin.BuildConfig
import com.contentfilter.core.domain.repository.PushNotificationRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminPushRegistrar
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val pushNotificationRepository: PushNotificationRepository,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun registerIfReady() {
            if (!notificationsAllowed()) return
            AdminPushNotificationChannels.ensureUrgentProtectionChannel(context)
            AdminPushNotificationChannels.ensureAnnouncementsChannel(context)
            ensureFirebaseConfigured()
            if (FirebaseApp.getApps(context).isEmpty()) {
                Log.w(LogTag, "Firebase is not configured; admin push token not registered.")
                return
            }
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    if (token.isNullOrBlank()) return@addOnSuccessListener
                    scope.launch { pushNotificationRepository.registerDeviceToken(token) }
                }
                .addOnFailureListener { error ->
                    Log.w(LogTag, "FCM token read failed: ${error.message}")
                }
        }

        private fun notificationsAllowed(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

        private fun ensureFirebaseConfigured() {
            if (FirebaseApp.getApps(context).isNotEmpty()) return
            val appId = BuildConfig.FIREBASE_APPLICATION_ID
            val apiKey = BuildConfig.FIREBASE_API_KEY
            val projectId = BuildConfig.FIREBASE_PROJECT_ID
            if (appId.isBlank() || apiKey.isBlank() || projectId.isBlank()) return
            val options =
                FirebaseOptions.Builder()
                    .setApplicationId(appId)
                    .setApiKey(apiKey)
                    .setProjectId(projectId)
                    .build()
            FirebaseApp.initializeApp(context, options)
        }

        private companion object {
            const val LogTag = "AdminPush"
        }
    }
