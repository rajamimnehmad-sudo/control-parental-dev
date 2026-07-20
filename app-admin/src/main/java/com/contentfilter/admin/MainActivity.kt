package com.contentfilter.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.contentfilter.admin.push.AdminProtectionAlertPayload
import com.contentfilter.admin.push.AlertTypeKey
import com.contentfilter.admin.push.DataTypeKey
import com.contentfilter.admin.push.DeviceIdKey
import com.contentfilter.admin.push.DeviceNameKey
import com.contentfilter.admin.push.EventIdKey
import com.contentfilter.admin.push.OpenAnnouncementsAction
import com.contentfilter.admin.push.parseAdminProtectionAlertPayload
import com.contentfilter.core.ui.ContentFilterTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var protectionAlertPayload by mutableStateOf<AdminProtectionAlertPayload?>(null)
    private var announcementOpenRequest by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (shouldHandleInitialAdminIntent(savedInstanceState != null)) {
            recordLaunchIntent(intent)
        }
        setContent {
            ContentFilterTheme {
                AdminAppRoot(
                    modifier = Modifier.fillMaxSize(),
                    protectionAlertPayload = protectionAlertPayload,
                    announcementOpenRequest = announcementOpenRequest,
                    onProtectionAlertConsumed = { protectionAlertPayload = null },
                    onAnnouncementOpenConsumed = { announcementOpenRequest = 0 },
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recordLaunchIntent(intent)
    }

    private fun recordLaunchIntent(intent: android.content.Intent) {
        protectionAlertPayload = intent.adminProtectionAlertPayload()
        if (intent.action == OpenAnnouncementsAction) announcementOpenRequest += 1
    }

    private fun android.content.Intent.adminProtectionAlertPayload(): AdminProtectionAlertPayload? =
        parseAdminProtectionAlertPayload(
            mapOf(
                DataTypeKey to getStringExtra(DataTypeKey).orEmpty(),
                EventIdKey to getStringExtra(EventIdKey).orEmpty(),
                DeviceIdKey to getStringExtra(DeviceIdKey).orEmpty(),
                DeviceNameKey to getStringExtra(DeviceNameKey).orEmpty(),
                AlertTypeKey to getStringExtra(AlertTypeKey).orEmpty(),
            ),
        )
}

internal fun shouldHandleInitialAdminIntent(hasSavedInstanceState: Boolean): Boolean = !hasSavedInstanceState
