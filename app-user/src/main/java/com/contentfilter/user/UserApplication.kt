package com.contentfilter.user

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.sync.SyncScheduler
import com.contentfilter.core.sync.engine.SyncEngine
import com.contentfilter.core.sync.realtime.RealtimeSyncCoordinator
import com.contentfilter.user.apps.InstalledAppPublisher
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
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
    lateinit var syncEngine: SyncEngine

    @Inject
    lateinit var realtimeSyncCoordinator: RealtimeSyncCoordinator

    @Inject
    lateinit var deviceActivationRepository: DeviceActivationRepository

    @Inject
    lateinit var installedAppPublisher: InstalledAppPublisher

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() =
            Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build()

    override fun onCreate() {
        super.onCreate()
        runCatching { syncScheduler.schedulePeriodicSync() }
        runCatching { syncScheduler.requestSync() }
        appScope.launch { runCatching { syncEngine.syncCoreDataFull() } }
        appScope.launch {
            deviceActivationRepository.observeActivation()
                .map { it?.deviceId }
                .distinctUntilChanged()
                .collect {
                    val activation = deviceActivationRepository.currentActivation()
                    if (activation != null) {
                        runCatching { installedAppPublisher.publish(activation) }
                    }
                }
        }
        runCatching { realtimeSyncCoordinator.start() }
    }
}
