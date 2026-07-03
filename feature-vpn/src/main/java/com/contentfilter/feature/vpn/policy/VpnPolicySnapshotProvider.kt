package com.contentfilter.feature.vpn.policy

import com.contentfilter.core.domain.model.LicenseState
import com.contentfilter.core.domain.repository.DailyLimitRepository
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.domain.repository.PolicyRepository
import com.contentfilter.core.domain.repository.SystemStatusRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps a fast in-memory snapshot for packet-time evaluation.
 */
@Singleton
class VpnPolicySnapshotProvider
    @Inject
    constructor(
        private val policyRepository: PolicyRepository,
        private val dailyLimitRepository: DailyLimitRepository,
        private val systemStatusRepository: SystemStatusRepository,
        private val deviceActivationRepository: DeviceActivationRepository,
    ) {
        private val state = MutableStateFlow(VpnPolicyState.initial())
        private var observationJob: Job? = null

        fun start(scope: CoroutineScope) {
            if (observationJob?.isActive == true) return
            observationJob =
                scope.launch {
                    combine(
                        policyRepository.observeActivePolicy(),
                        dailyLimitRepository.observeLimits(),
                        systemStatusRepository.observeHealth(),
                        deviceActivationRepository.observeActivation(),
                    ) { snapshot, dailyLimits, health, activation ->
                        VpnPolicyState(
                            snapshot = snapshot.copy(dailyLimits = dailyLimits),
                            health = health.withActiveLicenseIfActivated(activation != null),
                        )
                    }.collect { state.value = it }
                }
        }

        suspend fun refresh() {
            val activation = deviceActivationRepository.currentActivation()
            state.value =
                VpnPolicyState(
                    snapshot =
                        policyRepository.getActivePolicy().copy(
                            dailyLimits = dailyLimitRepository.observeLimits().first(),
                        ),
                    health = systemStatusRepository.currentHealth().withActiveLicenseIfActivated(activation != null),
                )
        }

        fun current(): VpnPolicyState = state.value

        fun stop() {
            observationJob?.cancel()
            observationJob = null
        }

        private fun com.contentfilter.core.domain.model.SystemHealthSnapshot.withActiveLicenseIfActivated(
            isActivated: Boolean,
        ): com.contentfilter.core.domain.model.SystemHealthSnapshot =
            if (isActivated) copy(licenseState = LicenseState.Active) else this
    }
