package com.contentfilter.core.sync.realtime

import com.contentfilter.core.network.realtime.RealtimeSubscription
import com.contentfilter.core.sync.engine.SyncEngine
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Singleton
class DefaultRealtimeSyncCoordinator
    @Inject
    constructor(
        private val realtimeSubscription: RealtimeSubscription,
        private val syncEngine: SyncEngine,
    ) : RealtimeSyncCoordinator {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var job: Job? = null

        override fun start() {
            if (job != null) return
            realtimeSubscription.connect()
            job = scope.launch {
                realtimeSubscription.observeChanges().collectLatest {
                    syncEngine.syncOnce()
                }
            }
        }

        override fun stop() {
            job?.cancel()
            job = null
            realtimeSubscription.disconnect()
        }
    }
