package com.contentfilter.user

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.sync.engine.TargetedPolicySyncCoordinator
import com.contentfilter.core.ui.ContentFilterTheme
import com.contentfilter.user.dag.DagShortcutController
import com.contentfilter.user.protection.ProtectionControlCoordinator
import com.contentfilter.user.push.OpenAnnouncementsAction
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val dagLaunchRequests = MutableStateFlow(0)
    private val announcementOpenRequests = MutableStateFlow(0)

    @javax.inject.Inject
    lateinit var activationRepository: DeviceActivationRepository

    @javax.inject.Inject
    lateinit var targetedPolicySyncCoordinator: TargetedPolicySyncCoordinator

    @javax.inject.Inject
    lateinit var protectionControlCoordinator: ProtectionControlCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(UserHomeHeaderTop.toArgb()))
        if (shouldHandleInitialUserIntent(savedInstanceState != null)) {
            recordDagLaunch(intent)
            recordAnnouncementLaunch(intent)
        }
        setContent {
            ContentFilterTheme {
                val dagLaunchRequest by dagLaunchRequests.collectAsStateWithLifecycle()
                val announcementOpenRequest by announcementOpenRequests.collectAsStateWithLifecycle()
                UserAppRoot(
                    modifier = Modifier.fillMaxSize(),
                    dagLaunchRequest = dagLaunchRequest,
                    announcementOpenRequest = announcementOpenRequest,
                    onDagLaunchConsumed = { dagLaunchRequests.value = 0 },
                    onAnnouncementOpenConsumed = { announcementOpenRequests.value = 0 },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recordDagLaunch(intent)
        recordAnnouncementLaunch(intent)
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch(Dispatchers.IO) {
            val activation = activationRepository.currentActivation() ?: return@launch
            targetedPolicySyncCoordinator.refresh(
                deviceId = activation.deviceId,
                reason = "foreground",
            )
            protectionControlCoordinator.refresh()
        }
    }

    private fun recordDagLaunch(intent: Intent?) {
        if (intent?.action == DagShortcutController.OpenDagAction) {
            dagLaunchRequests.value += 1
        }
    }

    private fun recordAnnouncementLaunch(intent: Intent?) {
        if (intent?.action == OpenAnnouncementsAction) announcementOpenRequests.value += 1
    }
}

internal fun shouldHandleInitialUserIntent(hasSavedInstanceState: Boolean): Boolean = !hasSavedInstanceState
