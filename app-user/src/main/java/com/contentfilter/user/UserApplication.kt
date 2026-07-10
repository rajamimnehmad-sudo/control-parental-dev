package com.contentfilter.user

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.sync.SyncScheduler
import com.contentfilter.core.sync.engine.TargetedPolicySyncCoordinator
import com.contentfilter.core.sync.realtime.RealtimeSyncCoordinator
import com.contentfilter.feature.vpn.service.VpnController
import com.contentfilter.user.apps.InstalledAppPublisher
import com.contentfilter.user.repair.UserLocalDataRepair
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.drop
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

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() =
            Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build()

    override fun onCreate() {
        super.onCreate()
        runCatching { VpnController.disableDevProtection(this) }
        runCatching { syncScheduler.schedulePeriodicSync() }
        appScope.launch {
            runCatching { localDataRepair.repairIfNeeded() }
            val activation = deviceActivationRepository.currentActivation()
            if (activation != null) {
                runCatching { realtimeSyncCoordinator.start() }
                runCatching {
                    targetedPolicySyncCoordinator.refresh(
                        deviceId = activation.deviceId,
                        reason = "process-start",
                    )
                }
                runCatching { syncScheduler.requestSync() }
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
                        runCatching { localDataRepair.clearStaleDataAfterActivation(activation) }
                        runCatching { installedAppPublisher.publish(activation) }
                        runCatching {
                            realtimeSyncCoordinator.stop()
                            realtimeSyncCoordinator.start()
                        }
                        runCatching {
                            targetedPolicySyncCoordinator.refresh(
                                deviceId = activation.deviceId,
                                reason = "activation",
                            )
                        }
                        runCatching { syncScheduler.requestSync() }
                    }
                }
        }
        appScope.launch {
            while (true) {
                delay(DeviceLicenseValidationIntervalMillis)
                if (deviceActivationRepository.currentActivation() != null) {
                    runCatching { localDataRepair.repairIfNeeded() }
                }
            }
        }
    }

    private companion object {
        const val DeviceLicenseValidationIntervalMillis = 60_000L
    }
}
