package com.contentfilter.core.sync

import android.util.Log
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.contentfilter.core.sync.engine.SyncEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted workerParameters: WorkerParameters,
        private val syncEngine: SyncEngine,
    ) : CoroutineWorker(appContext, workerParameters) {
        override suspend fun doWork(): Result {
            val result = syncEngine.syncOnce()
            Log.i(LogTag, "SyncWorker finished success=${result.success} message=${result.message}")
            return if (result.success) Result.success() else Result.retry()
        }

        private companion object {
            const val LogTag = "SyncWorker"
        }
    }
