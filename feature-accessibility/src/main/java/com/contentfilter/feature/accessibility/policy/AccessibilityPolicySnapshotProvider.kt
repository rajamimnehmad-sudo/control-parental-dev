package com.contentfilter.feature.accessibility.policy

import com.contentfilter.core.domain.model.LicenseState
import com.contentfilter.core.domain.model.AppGroup
import com.contentfilter.core.domain.model.DailyLimit
import com.contentfilter.core.domain.model.ExtraTimeGrant
import com.contentfilter.core.domain.model.PolicySnapshot
import com.contentfilter.core.domain.model.UsageSession
import com.contentfilter.core.domain.repository.DailyLimitRepository
import com.contentfilter.core.domain.repository.AppGroupRepository
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.domain.repository.ExtraTimeGrantRepository
import com.contentfilter.core.domain.repository.PolicyRepository
import com.contentfilter.core.domain.repository.SystemStatusRepository
import com.contentfilter.core.domain.repository.UsageSessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Singleton
class AccessibilityPolicySnapshotProvider
    @Inject
    constructor(
        private val policyRepository: PolicyRepository,
        private val dailyLimitRepository: DailyLimitRepository,
        private val appGroupRepository: AppGroupRepository,
        private val extraTimeGrantRepository: ExtraTimeGrantRepository,
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
            observationJob =
                scope.launch {
                    val policyParts =
                        combine(
                            policyRepository.observeActivePolicy(),
                            dailyLimitRepository.observeLimits(),
                            appGroupRepository.observeGroups(),
                            extraTimeGrantRepository.observeGrants(),
                        ) { snapshot, dailyLimits, appGroups, extraTimeGrants ->
                            PolicyParts(snapshot, dailyLimits, appGroups, extraTimeGrants)
                        }
                    combine(
                        policyParts,
                        systemStatusRepository.observeHealth(),
                        deviceActivationRepository.observeActivation().flatMapLatest { activation ->
                            usageSessionRepository.observeDailyUsage(
                                deviceId = activation?.deviceId ?: UsageSession.LOCAL_DEVICE_ID,
                                localDate = day.localDate,
                                dayStartEpochMillis = day.startEpochMillis,
                                dayEndEpochMillis = day.endEpochMillis,
                            ).map { dailyUsage -> activation to dailyUsage }
                        },
                    ) { parts, health, activationAndDailyUsage ->
                        val (activation, dailyUsage) = activationAndDailyUsage
                        AccessibilityPolicyState(
                            snapshot =
                                parts.snapshot.copy(
                                    dailyLimits = parts.dailyLimits,
                                    appGroups = parts.appGroups,
                                    dailyUsage = dailyUsage,
                                    extraTimeGrants = parts.extraTimeGrants,
                                ),
                            health = health.withActiveLicenseIfActivated(activation != null),
                        )
                    }.collect { state.value = it }
                }
        }

        suspend fun refresh() {
            val day = localDayProvider.currentDay()
            val activation = deviceActivationRepository.currentActivation()
            val dailyUsage =
                usageSessionRepository.observeDailyUsage(
                    deviceId = activation?.deviceId ?: UsageSession.LOCAL_DEVICE_ID,
                    localDate = day.localDate,
                    dayStartEpochMillis = day.startEpochMillis,
                    dayEndEpochMillis = day.endEpochMillis,
                ).first()
            val dailyLimits = dailyLimitRepository.observeLimits().first()
            val appGroups = appGroupRepository.observeGroups().first()
            val extraTimeGrants = extraTimeGrantRepository.observeGrants().first()
            state.value =
                AccessibilityPolicyState(
                    snapshot =
                        policyRepository.getActivePolicy().copy(
                            dailyLimits = dailyLimits,
                            appGroups = appGroups,
                            dailyUsage = dailyUsage,
                            extraTimeGrants = extraTimeGrants,
                        ),
                    health = systemStatusRepository.currentHealth().withActiveLicenseIfActivated(activation != null),
                )
            observedDay = day
        }

        fun ensureCurrentDay(scope: CoroutineScope): Boolean {
            val dayChanged = observedDay?.localDate != localDayProvider.currentDay().localDate
            if (dayChanged) {
                start(scope)
            }
            return dayChanged
        }

        fun current(): AccessibilityPolicyState = state.value

        fun stop() {
            observationJob?.cancel()
            observationJob = null
            observedDay = null
        }

        private fun com.contentfilter.core.domain.model.SystemHealthSnapshot.withActiveLicenseIfActivated(
            isActivated: Boolean,
        ): com.contentfilter.core.domain.model.SystemHealthSnapshot =
            if (isActivated) copy(licenseState = LicenseState.Active) else this

        private data class PolicyParts(
            val snapshot: PolicySnapshot,
            val dailyLimits: List<DailyLimit>,
            val appGroups: List<AppGroup>,
            val extraTimeGrants: List<ExtraTimeGrant>,
        )
    }
