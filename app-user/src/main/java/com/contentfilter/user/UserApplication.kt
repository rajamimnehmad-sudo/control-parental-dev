package com.contentfilter.user

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.network.remote.RemoteDeviceRepository
import com.contentfilter.core.network.remote.RemoteResult
import com.contentfilter.core.sync.SyncScheduler
import com.contentfilter.core.sync.engine.TargetedPolicySyncCoordinator
import com.contentfilter.core.sync.realtime.RealtimeSyncCoordinator
import com.contentfilter.feature.activation.InstalledAppVersionProvider
import com.contentfilter.feature.vpn.domainlist.WebDomainListUpdater
import com.contentfilter.feature.vpn.service.VpnController
import com.contentfilter.user.apps.InstalledAppPublisher
import com.contentfilter.user.protection.ProtectionControlCoordinator
import com.contentfilter.user.repair.UserLocalDataRepair
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class UserApplication :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncScheduler: SyncScheduler

    @Inject
    lateinit var targetedPolicySyncCoordinator: TargetedPolicySyncCoordinator

    @Inject
    lateinit var realtimeSyncCoordinator: RealtimeSyncCoordinator

    @Inject
    lateinit var deviceActivationRepository: DeviceActivationRepository

    @Inject
    lateinit var installedAppPublisher: InstalledAppPublisher

    @Inject
    lateinit var localDataRepair: UserLocalDataRepair

    @Inject
    lateinit var webDomainListUpdater: WebDomainListUpdater

    @Inject
    lateinit var remoteDeviceRepository: RemoteDeviceRepository

    @Inject
    lateinit var installedAppVersionProvider: InstalledAppVersionProvider

    @Inject
    lateinit var protectionControlCoordinator: ProtectionControlCoordinator

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() =
            Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build()

    override fun onCreate() {
        super.onCreate()
        runCatching { VpnController.enableDevProtection(this) }
            .logFailure("vpn-enable")
        runCatching { syncScheduler.schedulePeriodicSync() }
            .logFailure("periodic-sync-schedule")
        appScope.launch {
            runCatching { webDomainListUpdater.refreshIfDue() }
                .logFailure("domain-list-refresh")
            while (true) {
                delay(WebDomainListRefreshIntervalMillis)
                runCatching { webDomainListUpdater.refreshIfDue() }
                    .logFailure("domain-list-refresh")
            }
        }
        appScope.launch {
            runCatching { localDataRepair.repairIfNeeded() }
                .logFailure("local-data-repair")
            val activation = deviceActivationRepository.currentActivation()
            if (activation != null) {
                runCatching { reportInstalledVersion(activation.deviceId) }
                    .logFailure("app-version-report")
                runCatching { realtimeSyncCoordinator.start() }
                    .logFailure("realtime-start")
                runCatching {
                    targetedPolicySyncCoordinator.refresh(
                        deviceId = activation.deviceId,
                        reason = "process-start",
                    )
                }.logFailure("targeted-policy-refresh")
                runCatching { syncScheduler.requestSync() }
                    .logFailure("sync-request")
                runCatching { protectionControlCoordinator.refresh() }
                    .logFailure("protection-control-refresh")
            }
        }
        appScope.launch {
            deviceActivationRepository.observeActivation()
                .map { it?.deviceId }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    val activation = deviceActivationRepository.currentActivation()
                    if (activation != null) {
                        runCatching { reportInstalledVersion(activation.deviceId) }
                            .logFailure("activation-version-report")
                        runCatching { localDataRepair.clearStaleDataAfterActivation(activation) }
                            .logFailure("activation-data-repair")
                        runCatching { installedAppPublisher.publish(activation) }
                            .logFailure("installed-app-publish")
                        runCatching {
                            realtimeSyncCoordinator.stop()
                            realtimeSyncCoordinator.start()
                        }.logFailure("realtime-restart")
                        runCatching {
                            targetedPolicySyncCoordinator.refresh(
                                deviceId = activation.deviceId,
                                reason = "activation",
                            )
                        }.logFailure("activation-policy-refresh")
                        runCatching { syncScheduler.requestSync() }
                            .logFailure("activation-sync-request")
                        runCatching { protectionControlCoordinator.refresh() }
                            .logFailure("activation-protection-refresh")
                    }
                }
        }
        appScope.launch {
            while (true) {
                delay(DeviceLicenseValidationIntervalMillis)
                if (deviceActivationRepository.currentActivation() != null) {
                    runCatching { localDataRepair.repairIfNeeded() }
                        .logFailure("periodic-local-data-repair")
                }
            }
        }
        appScope.launch {
            while (true) {
                delay(ProtectionControlRefreshIntervalMillis)
                runCatching { protectionControlCoordinator.refresh() }
                    .logFailure("periodic-protection-refresh")
            }
        }
    }

    private suspend fun reportInstalledVersion(deviceId: String) {
        when (
            remoteDeviceRepository.updateAppVersion(
                deviceId = deviceId,
                appVersionCode = installedAppVersionProvider.versionCode(),
            )
        ) {
            is RemoteResult.Success -> Unit
            is RemoteResult.Failure -> Log.w(LogTag, "Startup step=app-version-report failed type=remote")
        }
    }

    private fun <T> Result<T>.logFailure(step: String): Result<T> =
        onFailure { error ->
            Log.w(LogTag, "Startup step=$step failed type=${error.javaClass.simpleName}")
        }

    private companion object {
        const val LogTag = "UserApplication"
        const val DeviceLicenseValidationIntervalMillis = 60_000L
        const val WebDomainListRefreshIntervalMillis = 6 * 60 * 60 * 1_000L
        const val ProtectionControlRefreshIntervalMillis = 60_000L
    }
}
