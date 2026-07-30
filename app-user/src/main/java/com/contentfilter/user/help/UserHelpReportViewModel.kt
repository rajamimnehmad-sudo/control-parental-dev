package com.contentfilter.user.help

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.domain.help.HelpContext
import com.contentfilter.core.domain.help.HelpReportDraft
import com.contentfilter.core.domain.repository.AppFeedbackRepository
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.user.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserHelpReportViewModel
    @Inject
    constructor(
        private val activationRepository: DeviceActivationRepository,
        private val feedbackRepository: AppFeedbackRepository,
    ) : ViewModel() {
        private val submitted = mutableSetOf<String>()

        fun report(
            draft: HelpReportDraft,
            context: HelpContext,
        ) {
            val reportKey = "${draft.category.wireValue}:${draft.safeSummary}"
            if (!submitted.add(reportKey)) return
            viewModelScope.launch {
                val activation = activationRepository.currentActivation()
                if (activation == null) {
                    submitted.remove(reportKey)
                    return@launch
                }
                val result =
                    feedbackRepository.submitSupportReport(
                        deviceId = activation.deviceId,
                        category = draft.category.wireValue,
                        safeSummary = draft.safeSummary,
                        appVersionCode = BuildConfig.VERSION_CODE,
                        manufacturer = Build.MANUFACTURER.take(MaxDeviceFieldLength),
                        model = Build.MODEL.take(MaxDeviceFieldLength),
                        androidVersion = Build.VERSION.RELEASE.take(MaxDeviceFieldLength),
                        diagnosticCodes = context.diagnosticCodes(),
                    )
                if (result.isFailure) submitted.remove(reportKey)
            }
        }
    }

private fun HelpContext.diagnosticCodes(): List<String> =
    buildList {
        if (offline) add("sync-inactive")
        if (!vpnActive) add("vpn-inactive")
        if (!accessibilityActive) add("accessibility-inactive")
        if (!uninstallProtectionActive) add("device-admin-inactive")
        if (!dagInstalled) add("dag-not-installed")
        if (protectionNeedsAttention) add("protection-needs-attention")
    }

private const val MaxDeviceFieldLength = 100
