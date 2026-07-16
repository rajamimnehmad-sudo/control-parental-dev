package com.contentfilter.user.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.contentfilter.core.domain.model.AccessRequest
import com.contentfilter.core.domain.model.AccessRequestType
import com.contentfilter.core.domain.model.PolicyTargetType
import com.contentfilter.core.domain.model.RequestStatus
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.repository.AccessRequestRepository
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.domain.repository.InstallApprovalStore
import com.contentfilter.core.domain.repository.PolicyRepository
import com.contentfilter.core.sync.SyncScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class PackageChangeReceiver : BroadcastReceiver() {
    @Inject lateinit var installApprovalStore: InstallApprovalStore

    @Inject lateinit var requestRepository: AccessRequestRepository

    @Inject lateinit var activationRepository: DeviceActivationRepository

    @Inject lateinit var policyRepository: PolicyRepository

    @Inject lateinit var syncScheduler: SyncScheduler

    @Inject lateinit var installedAppPublisher: InstalledAppPublisher

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val packageName = intent.data?.schemeSpecificPart?.takeIf { it.isNotBlank() } ?: return
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
        if (intent.action == Intent.ACTION_PACKAGE_REMOVED) {
            installApprovalStore.remove(packageName)
            return
        }
        if (intent.action != Intent.ACTION_PACKAGE_ADDED) return
        val applicationInfo = context.packageManager.applicationInfo(packageName) ?: return
        if (
            !shouldRequireInstallApproval(
                packageName = packageName,
                isSystemApp = applicationInfo.isSystemApp(),
                isKnown = installApprovalStore.isKnown(packageName),
                ownPackageName = context.packageName,
            )
        ) {
            return
        }
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handleNewPackage(context.packageManager, applicationInfo)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleNewPackage(
        packageManager: PackageManager,
        applicationInfo: ApplicationInfo,
    ) {
        val activation = activationRepository.currentActivation() ?: return
        val packageName = applicationInfo.packageName
        runCatching { installedAppPublisher.publish(activation) }
        val alreadyAllowed =
            policyRepository
                .getActivePolicy(activation.deviceId)
                .rules
                .any {
                    it.enabled &&
                        it.scope == RuleScope.App &&
                        it.target == packageName &&
                        it.action == RuleAction.Allow
                }
        if (alreadyAllowed) {
            installApprovalStore.markApproved(packageName)
            return
        }
        installApprovalStore.markPending(packageName)
        val duplicate =
            requestRepository.observePendingRequests().first().any {
                it.requestType == AccessRequestType.APP_ACCESS && it.targetPackageName == packageName
            }
        if (!duplicate) {
            val appName = applicationInfo.loadLabel(packageManager).toString().ifBlank { packageName }
            requestRepository.saveRequest(
                AccessRequest(
                    id = UUID.randomUUID().toString(),
                    requestType = AccessRequestType.APP_ACCESS,
                    targetType = PolicyTargetType.App,
                    target = packageName,
                    targetPackageName = packageName,
                    targetDomain = null,
                    reason = "Aprobar instalación de $appName",
                    requestedMinutes = null,
                    status = RequestStatus.PendingLocal,
                    createdAtEpochMillis = System.currentTimeMillis(),
                    expiresAtEpochMillis = null,
                    deviceId = activation.deviceId,
                ),
            )
            syncScheduler.requestSync()
        }
    }

    private fun PackageManager.applicationInfo(packageName: String): ApplicationInfo? =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                getApplicationInfo(packageName, 0)
            }
        }.getOrNull()

    private fun ApplicationInfo.isSystemApp(): Boolean =
        flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
}

internal fun shouldRequireInstallApproval(
    packageName: String,
    isSystemApp: Boolean,
    isKnown: Boolean,
    ownPackageName: String,
): Boolean =
    !isSystemApp &&
        !isKnown &&
        packageName != ownPackageName &&
        !packageName.startsWith("com.contentfilter.admin")
