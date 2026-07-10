package com.contentfilter.feature.vpn.policy

import android.util.Log
import com.contentfilter.core.domain.model.LicenseState
import com.contentfilter.core.domain.model.webNavigationBlocked
import com.contentfilter.core.domain.repository.DailyLimitRepository
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.domain.repository.PolicyRepository
import com.contentfilter.core.domain.repository.SystemStatusRepository
import com.contentfilter.feature.vpn.telemetry.VpnTelemetryReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
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
        private val telemetryReporter: VpnTelemetryReporter,
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
                        resolvedState(
                            snapshot = snapshot.copy(dailyLimits = dailyLimits),
                            health = health.withActiveLicenseIfActivated(activation != null),
                        )
                    }.collect {
                        state.value = it
                        Log.i(
                            LogTag,
                            "webNavigation vpn applied policy=${it.snapshot.id} version=${it.snapshot.version} blocked=${it.snapshot.rules.webNavigationBlocked()} strict=${it.strictWebBlockEnabled}",
                        )
                        telemetryReporter.recordSnapshotReceived(
                            snapshot = it.snapshot,
                            strictWebBlock = it.strictWebBlockEnabled,
                        )
                    }
                }
        }

        suspend fun refresh() {
            val activation = deviceActivationRepository.currentActivation()
            state.value =
                resolvedState(
                    snapshot =
                        policyRepository.getActivePolicy()
                            .copy(dailyLimits = dailyLimitRepository.observeLimits().first()),
                    health = systemStatusRepository.currentHealth().withActiveLicenseIfActivated(activation != null),
                )
            telemetryReporter.recordSnapshotReceived(
                snapshot = state.value.snapshot,
                strictWebBlock = state.value.strictWebBlockEnabled,
            )
            Log.i(
                LogTag,
                "webNavigation vpn applied policy=${state.value.snapshot.id} version=${state.value.snapshot.version} blocked=${state.value.snapshot.rules.webNavigationBlocked()} strict=${state.value.strictWebBlockEnabled}",
            )
        }

        fun current(): VpnPolicyState = state.value

        fun observe(): StateFlow<VpnPolicyState> = state

        fun stop() {
            observationJob?.cancel()
            observationJob = null
        }

        private fun com.contentfilter.core.domain.model.SystemHealthSnapshot.withActiveLicenseIfActivated(
            isActivated: Boolean,
        ): com.contentfilter.core.domain.model.SystemHealthSnapshot =
            if (isActivated) copy(licenseState = LicenseState.Active) else this

        private fun resolvedState(
            snapshot: com.contentfilter.core.domain.model.PolicySnapshot,
            health: com.contentfilter.core.domain.model.SystemHealthSnapshot,
        ): VpnPolicyState {
            val current = state.value
            val resolvedSnapshot =
                VpnPolicyState.resolveSnapshot(
                    current = current.snapshot,
                    candidate = snapshot,
                )
            return VpnPolicyState(snapshot = resolvedSnapshot, health = health)
        }

        private companion object {
            const val LogTag = "VpnPolicySnapshot"
        }
    }
