package com.contentfilter.core.sync.realtime

import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.network.realtime.RealtimeChange
import com.contentfilter.core.network.realtime.RealtimeSubscription
import com.contentfilter.core.sync.engine.SyncEngine
import com.contentfilter.core.sync.engine.TargetedPolicySyncCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultRealtimeSyncCoordinator
    @Inject
    constructor(
        private val realtimeSubscription: RealtimeSubscription,
        private val syncEngine: SyncEngine,
        private val activationRepository: DeviceActivationRepository,
        private val targetedPolicySyncCoordinator: TargetedPolicySyncCoordinator,
    ) : RealtimeSyncCoordinator {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var job: Job? = null

        override fun start() {
            if (job?.isActive == true) return
            job =
                scope.launch {
                    val activation = activationRepository.currentActivation()
                    realtimeSubscription.connect(activation?.deviceId)
                    realtimeSubscription.observeChanges().collectLatest { change ->
                        when (change) {
                            is RealtimeChange.PolicyRevision ->
                                targetedPolicySyncCoordinator.refresh(
                                    deviceId = change.deviceId,
                                    policyId = change.policyId,
                                    minimumRevision = change.revision,
                                    requestId = change.requestId,
                                    reason = "realtime",
                                )
                            is RealtimeChange.Table -> syncEngine.syncOnce()
                        }
                    }
                }
        }

        override fun stop() {
            job?.cancel()
            job = null
            realtimeSubscription.disconnect()
        }
    }
