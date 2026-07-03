package com.contentfilter.core.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class WorkManagerSyncScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SyncScheduler {
        override fun schedulePeriodicSync() {
            val request =
                PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    )
                    .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PeriodicSyncWorkName,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        override fun requestSync() {
            val request =
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    )
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                ImmediateSyncWorkName,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        private companion object {
            const val PeriodicSyncWorkName = "policy-sync"
            const val ImmediateSyncWorkName = "policy-sync-now"
        }
    }
