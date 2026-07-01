package com.contentfilter.feature.accessibility.policy

import com.contentfilter.core.domain.model.UsageSession
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.domain.repository.PolicyRepository
import com.contentfilter.core.domain.repository.SystemStatusRepository
import com.contentfilter.core.domain.repository.UsageSessionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Singleton
class AccessibilityPolicySnapshotProvider
    @Inject
    constructor(
        private val policyRepository: PolicyRepository,
        private val systemStatusRepository: SystemStatusRepository,
        private val usageSessionRepository: UsageSessionRepository,
        private val deviceActivationRepository: DeviceActivationRepository,
        private val localDayProvider: LocalDayProvider,
    ) {
        private val state = MutableStateFlow(AccessibilityPolicyState.initial())
        private var observationJob: Job? = null
        private var observedDay: LocalDay? = null

        fun start(scope: CoroutineScope) {
            val day = localDayProvider.currentDay()
            if (observationJob?.isActive == true && observedDay?.localDate == day.localDate) return
            observationJob?.cancel()
            observedDay = day
            observationJob = scope.launch {
                combine(
                    policyRepository.observeActivePolicy(),
                    systemStatusRepository.observeHealth(),
                    deviceActivationRepository.observeActivation().flatMapLatest { activation ->
                        usageSessionRepository.observeDailyUsage(
                            deviceId = activation?.deviceId ?: UsageSession.LocalDeviceId,
                            localDate = day.localDate,
                            dayStartEpochMillis = day.startEpochMillis,
                            dayEndEpochMillis = day.endEpochMillis,
                        )
                    },
                ) { snapshot, health, dailyUsage ->
                    AccessibilityPolicyState(
                        snapshot = snapshot.copy(dailyUsage = dailyUsage),
                        health = health,
                    )
                }.collect { state.value = it }
            }
        }

        suspend fun refresh() {
            val day = localDayProvider.currentDay()
            state.value = AccessibilityPolicyState(
                snapshot = policyRepository.getActivePolicy().copy(
                    dailyUsage = emptyList(),
                ),
                health = systemStatusRepository.currentHealth(),
            )
            observedDay = day
        }

        fun ensureCurrentDay(scope: CoroutineScope) {
            if (observedDay?.localDate != localDayProvider.currentDay().localDate) {
                start(scope)
            }
        }

        fun current(): AccessibilityPolicyState = state.value

        fun stop() {
            observationJob?.cancel()
            observationJob = null
            observedDay = null
        }
    }
