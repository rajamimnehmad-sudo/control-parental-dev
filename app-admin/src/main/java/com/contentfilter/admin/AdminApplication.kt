package com.contentfilter.admin

import android.app.Application
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.contentfilter.core.domain.repository.AppFeedbackRepository
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.sync.SyncScheduler
import com.contentfilter.core.sync.realtime.RealtimeSyncCoordinator
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AdminApplication :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var syncScheduler: SyncScheduler

    @Inject lateinit var realtimeSyncCoordinator: RealtimeSyncCoordinator

    @Inject lateinit var deviceActivationRepository: DeviceActivationRepository

    @Inject lateinit var appFeedbackRepository: AppFeedbackRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() =
            Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build()

    override fun onCreate() {
        super.onCreate()
        runCatching { syncScheduler.schedulePeriodicSync() }
        runCatching { realtimeSyncCoordinator.start() }
        appScope.launch {
            deviceActivationRepository.currentActivation()?.let { activation ->
                appFeedbackRepository.reportDeviceMetadata(
                    deviceId = activation.deviceId,
                    manufacturer = Build.MANUFACTURER,
                    model = Build.MODEL,
                    androidVersion = Build.VERSION.RELEASE,
                    androidSdk = Build.VERSION.SDK_INT,
                )
            }
        }
    }
}
