package com.contentfilter.feature.vpn.policy

import com.contentfilter.core.domain.repository.PolicyRepository
import com.contentfilter.core.domain.repository.SystemStatusRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Keeps a fast in-memory snapshot for packet-time evaluation.
 */
@Singleton
class VpnPolicySnapshotProvider
    @Inject
    constructor(
        private val policyRepository: PolicyRepository,
        private val systemStatusRepository: SystemStatusRepository,
    ) {
        private val state = MutableStateFlow(VpnPolicyState.initial())
        private var observationJob: Job? = null

        fun start(scope: CoroutineScope) {
            if (observationJob?.isActive == true) return
            observationJob = scope.launch {
                combine(
                    policyRepository.observeActivePolicy(),
                    systemStatusRepository.observeHealth(),
                ) { snapshot, health ->
                    VpnPolicyState(snapshot = snapshot, health = health)
                }.collect { state.value = it }
            }
        }

        suspend fun refresh() {
            state.value = VpnPolicyState(
                snapshot = policyRepository.getActivePolicy(),
                health = systemStatusRepository.currentHealth(),
            )
        }

        fun current(): VpnPolicyState = state.value

        fun stop() {
            observationJob?.cancel()
            observationJob = null
        }
    }
