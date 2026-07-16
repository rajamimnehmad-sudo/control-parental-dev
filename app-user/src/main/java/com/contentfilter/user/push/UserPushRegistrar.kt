package com.contentfilter.user.push

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.contentfilter.core.domain.repository.PushNotificationRepository
import com.contentfilter.user.BuildConfig
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
class UserPushRegistrar
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val repository: PushNotificationRepository,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun registerIfReady() {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            UserPushNotificationChannels.ensure(context)
            ensureFirebase()
            if (FirebaseApp.getApps(context).isEmpty()) return
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener {
                        token ->
                    if (!token.isNullOrBlank()) scope.launch { repository.registerDeviceToken(token) }
                }
                .addOnFailureListener { Log.w("UserPush", "FCM token read failed: ${it.message}") }
        }

        private fun ensureFirebase() {
            if (FirebaseApp.getApps(context).isNotEmpty()) return
            if (BuildConfig.FIREBASE_APPLICATION_ID.isBlank() || BuildConfig.FIREBASE_API_KEY.isBlank() || BuildConfig.FIREBASE_PROJECT_ID.isBlank()) return
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId(BuildConfig.FIREBASE_APPLICATION_ID)
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .build(),
            )
        }
    }
