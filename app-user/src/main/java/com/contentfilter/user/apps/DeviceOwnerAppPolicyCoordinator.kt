package com.contentfilter.user.apps

import android.content.Context
import com.contentfilter.core.domain.model.LicenseState
import com.contentfilter.core.domain.model.PolicySnapshot
import com.contentfilter.core.domain.model.UsageSession
import com.contentfilter.core.domain.repository.AppGroupRepository
import com.contentfilter.core.domain.repository.DailyLimitRepository
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.domain.repository.ExtraTimeGrantRepository
import com.contentfilter.core.domain.repository.InstallApprovalStore
import com.contentfilter.core.domain.repository.PolicyRepository
import com.contentfilter.core.domain.repository.SystemStatusRepository
import com.contentfilter.core.domain.repository.UsageSessionRepository
import com.contentfilter.feature.accessibility.policy.AccessibilityAppPolicyEvaluator
import com.contentfilter.feature.accessibility.policy.AccessibilityPolicyState
import com.contentfilter.feature.accessibility.policy.LocalDayProvider
import com.contentfilter.feature.accessibility.service.DeviceOwnerAppVisibilityController
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps Android package visibility converged with the effective Glosh app policy.
 * Device Owner is the primary enforcement path; Accessibility remains a fallback
 * for immediate foreground eviction and anti-bypass behavior.
 */
@Singleton
class DeviceOwnerAppPolicyCoordinator
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val policyRepository: PolicyRepository,
        private val dailyLimitRepository: DailyLimitRepository,
        private val appGroupRepository: AppGroupRepository,
        private val extraTimeGrantRepository: ExtraTimeGrantRepository,
        private val usageSessionRepository: UsageSessionRepository,
        private val deviceActivationRepository: DeviceActivationRepository,
        private val systemStatusRepository: SystemStatusRepository,
        private val localDayProvider: LocalDayProvider,
        policyEvaluator: AccessibilityAppPolicyEvaluator,
        installApprovalStore: InstallApprovalStore,
    ) {
        private val visibilityController =
            DeviceOwnerAppVisibilityController(
                context = context,
                policyEvaluator = policyEvaluator,
                installApprovalStore = installApprovalStore,
            )
        private var job: Job? = null

        fun start(scope: CoroutineScope) {
            if (job?.isActive == true) return
            job =
                scope.launch {
                    launch {
                        combine(
                            policyRepository.observeActivePolicy(),
                            dailyLimitRepository.observeLimits(),
                            appGroupRepository.observeGroups(),
                            extraTimeGrantRepository.observeGrants(),
                        ) { snapshot, limits, groups, grants ->
                            PolicyParts(snapshot, limits, groups, grants)
                        }.collect { parts ->
                            reconcile(parts)
                        }
                    }
                    launch {
                        deviceActivationRepository.observeActivation().collect {
                            reconcileCurrent()
                        }
                    }
                    launch {
                        systemStatusRepository.observeHealth().collect {
                            reconcileCurrent()
                        }
                    }
                    launch {
                        deviceActivationRepository.observeActivation()
                            .flatMapLatest { activation ->
                                val day = localDayProvider.currentDay()
                                usageSessionRepository.observeDailyUsage(
                                    deviceId = activation?.deviceId ?: UsageSession.LOCAL_DEVICE_ID,
                                    localDate = day.localDate,
                                    dayStartEpochMillis = day.startEpochMillis,
                                    dayEndEpochMillis = day.endEpochMillis,
                                ).map { Unit }
                            }.collect {
                                reconcileCurrent()
                            }
                    }
                    launch {
                        while (isActive) {
                            reconcileCurrent()
                            delay(ReconcileIntervalMillis)
                        }
                    }
                }
        }

        fun hideNow(packageName: String): Boolean = visibilityController.hideNow(packageName)

        private suspend fun reconcileCurrent() {
            reconcile(
                PolicyParts(
                    snapshot = policyRepository.getActivePolicy(),
                    dailyLimits = dailyLimitRepository.observeLimits().first(),
                    appGroups = appGroupRepository.observeGroups().first(),
                    extraTimeGrants = extraTimeGrantRepository.observeGrants().first(),
                ),
            )
        }

        private suspend fun reconcile(parts: PolicyParts) {
            if (!visibilityController.isDeviceOwner()) return
            val activation = deviceActivationRepository.currentActivation()
            val day = localDayProvider.currentDay()
            val dailyUsage =
                usageSessionRepository.observeDailyUsage(
                    deviceId = activation?.deviceId ?: UsageSession.LOCAL_DEVICE_ID,
                    localDate = day.localDate,
                    dayStartEpochMillis = day.startEpochMillis,
                    dayEndEpochMillis = day.endEpochMillis,
                ).first()
            val health = systemStatusRepository.currentHealth()
            visibilityController.reconcile(
                AccessibilityPolicyState(
                    snapshot =
                        parts.snapshot.copy(
                            dailyLimits = parts.dailyLimits,
                            appGroups = parts.appGroups,
                            dailyUsage = dailyUsage,
                            extraTimeGrants = parts.extraTimeGrants,
                        ),
                    health =
                        if (activation != null && health.licenseState == LicenseState.PendingActivation) {
                            health.copy(licenseState = LicenseState.Active)
                        } else {
                            health
                        },
                ),
            )
        }

        private data class PolicyParts(
            val snapshot: PolicySnapshot,
            val dailyLimits: List<com.contentfilter.core.domain.model.DailyLimit>,
            val appGroups: List<com.contentfilter.core.domain.model.AppGroup>,
            val extraTimeGrants: List<com.contentfilter.core.domain.model.ExtraTimeGrant>,
        )

        private companion object {
            const val ReconcileIntervalMillis = 15_000L
        }
    }
