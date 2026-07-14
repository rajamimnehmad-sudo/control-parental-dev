package com.contentfilter.feature.accessibility.service

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.model.ProtectionAlertType
import com.contentfilter.core.domain.model.ProtectionAuthorizationScope
import com.contentfilter.core.domain.repository.ProtectionStateStore
import com.contentfilter.core.domain.repository.PushNotificationRepository
import com.contentfilter.core.domain.repository.SystemStatusRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ProtectionDeviceAdminReceiver : DeviceAdminReceiver() {
    @Inject lateinit var systemStatusRepository: SystemStatusRepository

    @Inject lateinit var pushNotificationRepository: PushNotificationRepository

    @Inject lateinit var protectionStateStore: ProtectionStateStore

    override fun onEnabled(
        context: Context,
        intent: Intent,
    ) {
        super.onEnabled(context, intent)
        persistState(ComponentState.Enabled, reportDisabled = false)
    }

    override fun onDisabled(
        context: Context,
        intent: Intent,
    ) {
        super.onDisabled(context, intent)
        persistState(ComponentState.Disabled, reportDisabled = true)
    }

    private fun persistState(
        state: ComponentState,
        reportDisabled: Boolean,
    ) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                systemStatusRepository.updateDeviceAdminState(state)
                if (
                    reportDisabled &&
                    !protectionStateStore.isAuthorized(ProtectionAuthorizationScope.Removal)
                ) {
                    pushNotificationRepository.reportProtectionAlert(ProtectionAlertType.AdminDisabled)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
