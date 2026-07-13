package com.contentfilter.user.apps

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.domain.model.AccessRequest
import com.contentfilter.core.domain.model.AccessRequestType
import com.contentfilter.core.domain.model.AppGroup
import com.contentfilter.core.domain.model.DailyAppUsage
import com.contentfilter.core.domain.model.DailyLimit
import com.contentfilter.core.domain.model.ExtraTimeGrant
import com.contentfilter.core.domain.model.PolicySnapshot
import com.contentfilter.core.domain.model.PolicyTargetType
import com.contentfilter.core.domain.model.RequestStatus
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.repository.AccessRequestRepository
import com.contentfilter.core.domain.repository.AppGroupRepository
import com.contentfilter.core.domain.repository.DailyLimitRepository
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.domain.repository.ExtraTimeGrantRepository
import com.contentfilter.core.domain.repository.PolicyRepository
import com.contentfilter.core.domain.repository.UsageSessionRepository
import com.contentfilter.core.network.dto.RemoteAccessRequestDto
import com.contentfilter.core.network.remote.RemoteRequestRepository
import com.contentfilter.core.network.remote.RemoteResult
import com.contentfilter.core.sync.SyncScheduler
import com.contentfilter.core.sync.engine.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class MyAppsViewModel
    @Inject
    constructor(
        private val installedAppPublisher: InstalledAppPublisher,
        private val accessRequestRepository: AccessRequestRepository,
        private val remoteRequestRepository: RemoteRequestRepository,
        private val syncScheduler: SyncScheduler,
        private val syncEngine: SyncEngine,
        policyRepository: PolicyRepository,
        dailyLimitRepository: DailyLimitRepository,
        appGroupRepository: AppGroupRepository,
        grantRepository: ExtraTimeGrantRepository,
        private val activationRepository: DeviceActivationRepository,
        usageSessionRepository: UsageSessionRepository,
    ) : ViewModel() {
        private val detectedApps = MutableStateFlow<List<InstalledAppPublisher.DetectedApp>>(emptyList())
        private val message = MutableStateFlow("")
        private val isRefreshing = MutableStateFlow(false)
        private val searchQuery = MutableStateFlow("")
        private val pendingRequestPackages = MutableStateFlow<Set<String>>(emptySet())
        private val day = currentDay()
        private val nowTicks =
            flow {
                while (true) {
                    emit(System.currentTimeMillis())
                    delay(MinuteMillis)
                }
            }
        private val appUiOptions =
            combine(searchQuery, pendingRequestPackages, isRefreshing) { query, pendingPackages, refreshing ->
                AppUiOptions(query, pendingPackages, refreshing)
            }
        private val policyAndLimits =
            combine(
                policyRepository.observeActivePolicy(),
                dailyLimitRepository.observeLimits(),
                appGroupRepository.observeGroups(),
                grantRepository.observeGrants(),
                accessRequestRepository.observePendingRequests(),
            ) { policy, limits, appGroups, grants, requests ->
                PolicyAndLimits(policy, limits, appGroups, grants, requests)
            }
        private val appPolicyState =
            combine(
                policyAndLimits,
                activationRepository.observeActivation().flatMapLatest { activation ->
                    if (activation == null) {
                        flowOf(emptyList())
                    } else {
                        usageSessionRepository.observeDailyUsage(
                            deviceId = activation.deviceId,
                            localDate = day.localDate,
                            dayStartEpochMillis = day.startEpochMillis,
                            dayEndEpochMillis = day.endEpochMillis,
                        )
                    }
                },
            ) { policyAndLimits, usage ->
                AppPolicyState(
                    policyAndLimits.policy,
                    policyAndLimits.limits,
                    policyAndLimits.appGroups,
                    policyAndLimits.grants,
                    policyAndLimits.requests,
                    usage,
                )
            }

        val uiState =
            combine(
                detectedApps,
                appPolicyState,
                message,
                appUiOptions,
                nowTicks,
            ) { apps, policyState, currentMessage, options, now ->
                val usageByPackage = policyState.usage.associateBy { it.packageName }
                val appLimits =
                    policyState.limits
                        .filter { it.enabled && it.targetType == PolicyTargetType.App }
                        .associateBy { it.target }
                MyAppsUiState(
                    appGroups =
                        policyState.appGroups
                            .map { group ->
                                val groupPackages = group.apps.filter { it.enabled }.map { it.packageName }.toSet()
                                val usedMinutes =
                                    policyState.usage
                                        .filter { it.packageName in groupPackages }
                                        .sumOf { it.usedMinutes }
                                MyAppGroupUiState(
                                    id = group.id,
                                    name = group.name,
                                    limitMinutes = group.limitMinutes,
                                    usedMinutes = usedMinutes,
                                    appCount = groupPackages.size,
                                    color = group.color,
                                    packageNames = groupPackages.sorted(),
                                )
                            }
                            .sortedBy { it.name.lowercase() },
                    apps =
                        apps
                            .filterBySearch(options.searchQuery)
                            .map { app ->
                                val hasActiveExtraTime =
                                    policyState.grants.any {
                                        it.targetType == PolicyTargetType.App &&
                                            it.target == app.packageName &&
                                            it.validUntilEpochMillis > now
                                    }
                                val extraTimeRemainingMinutes =
                                    policyState.grants.activeExtraTimeRemainingMinutes(app.packageName, now)
                                val pendingRequests =
                                    policyState.requests.filter {
                                        it.targetType == PolicyTargetType.App &&
                                            (it.target == app.packageName || it.targetPackageName == app.packageName)
                                    }
                                val hasPendingExtraTime =
                                    pendingRequests.any {
                                        it.requestType == AccessRequestType.EXTRA_TIME
                                    }
                                val hasPendingAppAccess =
                                    pendingRequests.any {
                                        it.requestType == AccessRequestType.APP_ACCESS
                                    }
                                val matchingRules =
                                    policyState.policy.rules.filter {
                                        it.enabled && it.scope == RuleScope.App && it.target == app.packageName
                                    }
                                val dailyLimit = appLimits[app.packageName]
                                val usedMinutes = usageByPackage[app.packageName]?.usedMinutes ?: 0
                                val limitReached = dailyLimit != null && usedMinutes >= dailyLimit.limitMinutes
                                val status =
                                    when {
                                        hasPendingExtraTime -> AppAccessStatus.WaitingExtraTime
                                        hasPendingAppAccess -> AppAccessStatus.WaitingAuthorization
                                        hasActiveExtraTime -> AppAccessStatus.ExtraTime
                                        limitReached -> AppAccessStatus.LimitReached
                                        matchingRules.any { it.action == RuleAction.Block } -> AppAccessStatus.Blocked
                                        matchingRules.any { it.action == RuleAction.RequestAuthorization } -> AppAccessStatus.RequiresAuthorization
                                        dailyLimit != null -> AppAccessStatus.Limited
                                        else -> AppAccessStatus.Allowed
                                    }
                                MyAppItemUiState(
                                    name = app.name.takeIf { it.isNotBlank() && !it.startsWith("com.") } ?: "App",
                                    packageName = app.packageName,
                                    iconBase64 = app.iconBase64,
                                    status = status,
                                    dailyLimitMinutes = dailyLimit?.limitMinutes,
                                    extraTimeRemainingMinutes = extraTimeRemainingMinutes,
                                    usedMinutes = usedMinutes,
                                    isRequesting = options.pendingRequestPackages.contains(app.packageName),
                                )
                            }
                            .sortedWith(
                                compareBy<MyAppItemUiState> { it.status.sortOrder }
                                    .thenBy { it.name.lowercase() }
                                    .thenBy { it.packageName },
                            ),
                    searchQuery = options.searchQuery,
                    message = currentMessage,
                    isRefreshing = options.refreshing,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = MyAppsUiState(),
            )

        init {
            refreshApps()
        }

        fun refreshApps() {
            if (isRefreshing.value) return
            viewModelScope.launch(Dispatchers.IO) {
                isRefreshing.value = true
                try {
                    message.value = "Sincronizando reglas..."
                    val coreSyncResult = runCatching { syncEngine.syncCoreDataFull() }.getOrNull()
                    val requestResultsSyncResult = runCatching { syncEngine.syncRequestResultsFull() }.getOrNull()
                    Log.i(
                        LogTag,
                        "myAppsRefresh coreSyncSuccess=${coreSyncResult?.success} coreMessage=${coreSyncResult?.message.orEmpty()} " +
                            "requestResultsSuccess=${requestResultsSyncResult?.success} requestResultsMessage=${requestResultsSyncResult?.message.orEmpty()}",
                    )
                    detectedApps.value = installedAppPublisher.installedApps()
                    activationRepository.currentActivation()?.let { activation ->
                        runCatching { installedAppPublisher.publish(activation) }
                    }
                    message.value =
                        if (coreSyncResult?.success == false || requestResultsSyncResult?.success == false) {
                            "No se pudieron actualizar reglas. Mostrando datos guardados."
                        } else {
                            "Apps actualizadas."
                        }
                } catch (exception: Exception) {
                    Log.e(LogTag, "myAppsRefresh failed: ${exception.message}", exception)
                    message.value = "No se pudieron actualizar apps."
                } finally {
                    isRefreshing.value = false
                }
            }
        }

        fun onSearchChanged(value: String) {
            searchQuery.value = value
        }

        fun requestAccess(packageName: String) {
            saveRequest(
                requestType = AccessRequestType.APP_ACCESS,
                packageName = packageName,
                requestedMinutes = null,
                messageText = "Solicitud de acceso creada.",
            )
        }

        private fun saveRequest(
            requestType: AccessRequestType,
            packageName: String,
            requestedMinutes: Int?,
            messageText: String,
        ) {
            pendingRequestPackages.update { it + packageName }
            message.value = "Enviando solicitud..."
            viewModelScope.launch {
                val request =
                    AccessRequest(
                        id = UUID.randomUUID().toString(),
                        requestType = requestType,
                        targetType = PolicyTargetType.App,
                        target = packageName,
                        targetPackageName = packageName,
                        targetDomain = null,
                        reason = "Solicitud desde Mis aplicaciones",
                        requestedMinutes = requestedMinutes,
                        status = RequestStatus.PendingLocal,
                        createdAtEpochMillis = System.currentTimeMillis(),
                        expiresAtEpochMillis = null,
                    )
                val activation = withContext(Dispatchers.IO) { activationRepository.currentActivation() }
                val pushedDirectly =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            activation?.let {
                                remoteRequestRepository.upsertAccessRequest(
                                    request.toRemoteDto(
                                        accountId = it.accountId,
                                        deviceId = it.deviceId,
                                    ),
                                )
                            }
                        }.getOrNull() is RemoteResult.Success
                    }
                accessRequestRepository.saveRequest(
                    request.copy(
                        status = if (pushedDirectly) RequestStatus.PendingRemote else RequestStatus.PendingLocal,
                        deviceId = activation?.deviceId,
                    ),
                )
                syncScheduler.requestSync()
                val synced =
                    withContext(Dispatchers.IO) {
                        pushedDirectly ||
                            runCatching {
                                val syncOk =
                                    syncEngine.syncOnce().success &&
                                        syncEngine.syncAccessRequestsFull().success &&
                                        syncEngine.syncRequestResultsFull().success
                                syncOk
                            }.getOrDefault(false)
                    }
                pendingRequestPackages.update { it - packageName }
                message.update {
                    when {
                        activation == null -> "Solicitud guardada, pero este celular no está enlazado."
                        synced -> messageText
                        else -> "Solicitud guardada. Se enviará cuando haya conexión."
                    }
                }
            }
        }

        private fun List<InstalledAppPublisher.DetectedApp>.filterBySearch(
            query: String,
        ): List<InstalledAppPublisher.DetectedApp> {
            val normalized = query.trim().lowercase()
            if (normalized.isBlank()) return this
            return filter {
                it.name.lowercase().contains(normalized) ||
                    it.packageName.lowercase().contains(normalized)
            }
        }

        private fun currentDay(): LocalDay {
            val zone = ZoneId.systemDefault()
            val now = java.time.LocalDateTime.now(zone)
            val today = LocalDate.now(zone)
            val startDate = if (now.toLocalTime().isBefore(java.time.LocalTime.NOON)) today.minusDays(1) else today
            return LocalDay(
                localDate = startDate.toString(),
                startEpochMillis = startDate.atTime(java.time.LocalTime.NOON).atZone(zone).toInstant().toEpochMilli(),
                endEpochMillis =
                    startDate.plusDays(
                        1,
                    ).atTime(java.time.LocalTime.NOON).atZone(zone).toInstant().toEpochMilli(),
            )
        }

        private fun List<ExtraTimeGrant>.activeExtraTimeRemainingMinutes(
            packageName: String,
            nowEpochMillis: Long,
        ): Int? =
            asSequence()
                .filter {
                    it.targetType == PolicyTargetType.App &&
                        it.target == packageName &&
                        it.validUntilEpochMillis > nowEpochMillis
                }
                .map { it.validUntilEpochMillis }
                .maxOrNull()
                ?.remainingMinutesFrom(nowEpochMillis)

        private fun Long.remainingMinutesFrom(nowEpochMillis: Long): Int =
            ((this - nowEpochMillis + MinuteMillis - 1) / MinuteMillis).toInt().coerceAtLeast(1)

        private fun AccessRequest.toRemoteDto(
            accountId: String,
            deviceId: String,
        ): RemoteAccessRequestDto {
            val createdAt = Instant.ofEpochMilli(createdAtEpochMillis).toString()
            return RemoteAccessRequestDto(
                id = id,
                accountId = accountId,
                deviceId = deviceId,
                requestType = requestType.name,
                targetType = targetType.name,
                target = target,
                targetPackageName = targetPackageName,
                targetDomain = targetDomain,
                reason = reason,
                requestedMinutes = requestedMinutes,
                status = RequestStatus.PendingRemote.name,
                createdAt = createdAt,
                updatedAt = Instant.now().toString(),
                expiresAt = expiresAtEpochMillis?.let { Instant.ofEpochMilli(it).toString() },
                deletedAt = null,
            )
        }

        private data class LocalDay(
            val localDate: String,
            val startEpochMillis: Long,
            val endEpochMillis: Long,
        )

        private data class AppUiOptions(
            val searchQuery: String,
            val pendingRequestPackages: Set<String>,
            val refreshing: Boolean,
        )

        private data class PolicyAndLimits(
            val policy: PolicySnapshot,
            val limits: List<DailyLimit>,
            val appGroups: List<AppGroup>,
            val grants: List<ExtraTimeGrant>,
            val requests: List<AccessRequest>,
        )

        private data class AppPolicyState(
            val policy: PolicySnapshot,
            val limits: List<DailyLimit>,
            val appGroups: List<AppGroup>,
            val grants: List<ExtraTimeGrant>,
            val requests: List<AccessRequest>,
            val usage: List<DailyAppUsage>,
        )

        private val AppAccessStatus.sortOrder: Int
            get() =
                when (this) {
                    AppAccessStatus.Blocked,
                    AppAccessStatus.RequiresAuthorization,
                    AppAccessStatus.LimitReached,
                    AppAccessStatus.WaitingAuthorization,
                    -> 0

                    AppAccessStatus.Limited,
                    AppAccessStatus.WaitingExtraTime,
                    -> 1

                    AppAccessStatus.Allowed,
                    AppAccessStatus.ExtraTime,
                    -> 2
                }

        private companion object {
            const val LogTag = "MyAppsViewModel"
            const val MinuteMillis = 60_000L
        }
    }
