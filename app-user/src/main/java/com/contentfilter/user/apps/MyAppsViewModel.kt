package com.contentfilter.user.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.domain.model.AccessRequest
import com.contentfilter.core.domain.model.AccessRequestType
import com.contentfilter.core.domain.model.DailyAppUsage
import com.contentfilter.core.domain.model.DailyLimit
import com.contentfilter.core.domain.model.ExtraTimeGrant
import com.contentfilter.core.domain.model.PolicyTargetType
import com.contentfilter.core.domain.model.PolicySnapshot
import com.contentfilter.core.domain.model.RequestStatus
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.repository.AccessRequestRepository
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        grantRepository: ExtraTimeGrantRepository,
        private val activationRepository: DeviceActivationRepository,
        usageSessionRepository: UsageSessionRepository,
    ) : ViewModel() {
        private val detectedApps = MutableStateFlow<List<InstalledAppPublisher.DetectedApp>>(emptyList())
        private val message = MutableStateFlow("")
        private val searchQuery = MutableStateFlow("")
        private val pendingRequestPackages = MutableStateFlow<Set<String>>(emptySet())
        private val day = currentDay()
        private val appUiOptions = combine(searchQuery, pendingRequestPackages) { query, pendingPackages ->
            AppUiOptions(query, pendingPackages)
        }
        private val policyAndLimits = combine(
            policyRepository.observeActivePolicy(),
            dailyLimitRepository.observeLimits(),
            grantRepository.observeGrants(),
            accessRequestRepository.observePendingRequests(),
        ) { policy, limits, grants, requests ->
            PolicyAndLimits(policy, limits, grants, requests)
        }
        private val appPolicyState = combine(
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
                policyAndLimits.grants,
                policyAndLimits.requests,
                usage,
            )
        }

        val uiState = combine(
            detectedApps,
            appPolicyState,
            message,
            appUiOptions,
        ) { apps, policyState, currentMessage, options ->
            val usageByPackage = policyState.usage.associateBy { it.packageName }
            val appLimits = policyState.limits
                .filter { it.enabled && it.targetType == PolicyTargetType.App }
                .associateBy { it.target }
            val now = System.currentTimeMillis()
            MyAppsUiState(
                apps = apps
                    .filterBySearch(options.searchQuery)
                    .map { app ->
                        val hasActiveExtraTime = policyState.grants.any {
                            it.targetType == PolicyTargetType.App &&
                                it.target == app.packageName &&
                                it.validUntilEpochMillis > now
                        }
                        val pendingRequests = policyState.requests.filter {
                            it.targetType == PolicyTargetType.App &&
                                (it.target == app.packageName || it.targetPackageName == app.packageName)
                        }
                        val hasPendingExtraTime = pendingRequests.any {
                            it.requestType == AccessRequestType.EXTRA_TIME
                        }
                        val hasPendingAppAccess = pendingRequests.any {
                            it.requestType == AccessRequestType.APP_ACCESS
                        }
                        val matchingRules = policyState.policy.rules.filter {
                            it.enabled && it.scope == RuleScope.App && it.target == app.packageName
                        }
                        val dailyLimit = appLimits[app.packageName]
                        val usedMinutes = usageByPackage[app.packageName]?.usedMinutes ?: 0
                        val limitReached = dailyLimit != null && usedMinutes >= dailyLimit.limitMinutes
                        val status = when {
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
                            name = app.name,
                            packageName = app.packageName,
                            iconBase64 = app.iconBase64,
                            status = status,
                            dailyLimitMinutes = dailyLimit?.limitMinutes,
                            usedMinutes = usedMinutes,
                            isRequesting = options.pendingRequestPackages.contains(app.packageName),
                        )
                    }
                    .sortedWith(compareBy({ it.name.lowercase() }, { it.packageName })),
                searchQuery = options.searchQuery,
                message = currentMessage,
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
            viewModelScope.launch(Dispatchers.IO) {
                detectedApps.value = installedAppPublisher.installedApps()
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

        fun requestMoreTime(packageName: String) {
            saveRequest(
                requestType = AccessRequestType.EXTRA_TIME,
                packageName = packageName,
                requestedMinutes = DefaultExtraMinutes,
                messageText = "Solicitud de más tiempo creada.",
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
                val request = AccessRequest(
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
                val pushedDirectly = withContext(Dispatchers.IO) {
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
                    request.copy(status = if (pushedDirectly) RequestStatus.PendingRemote else RequestStatus.PendingLocal),
                )
                syncScheduler.requestSync()
                val synced = withContext(Dispatchers.IO) {
                    pushedDirectly || runCatching {
                        val syncOk = syncEngine.syncOnce().success &&
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
            val today = LocalDate.now(zone)
            return LocalDay(
                localDate = today.toString(),
                startEpochMillis = today.atStartOfDay(zone).toInstant().toEpochMilli(),
                endEpochMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            )
        }

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
        )

        private data class PolicyAndLimits(
            val policy: PolicySnapshot,
            val limits: List<DailyLimit>,
            val grants: List<ExtraTimeGrant>,
            val requests: List<AccessRequest>,
        )

        private data class AppPolicyState(
            val policy: PolicySnapshot,
            val limits: List<DailyLimit>,
            val grants: List<ExtraTimeGrant>,
            val requests: List<AccessRequest>,
            val usage: List<DailyAppUsage>,
        )

        private companion object {
            const val DefaultExtraMinutes = 15
        }
    }
