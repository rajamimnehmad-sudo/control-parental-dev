package com.contentfilter.user

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.contentfilter.core.sync.SyncScheduler
import com.contentfilter.core.sync.realtime.RealtimeSyncCoordinator
import dagger.hilt.android.HiltAndroidApp
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
    lateinit var realtimeSyncCoordinator: RealtimeSyncCoordinator

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        runCatching { syncScheduler.schedulePeriodicSync() }
        runCatching { realtimeSyncCoordinator.start() }
    }
}
