package com.contentfilter.admin.rules

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.admin.BuildConfig
import com.contentfilter.core.domain.model.AppGroup
import com.contentfilter.core.domain.model.DailyLimit
import com.contentfilter.core.domain.model.Device
import com.contentfilter.core.domain.model.ExtraTimeGrant
import com.contentfilter.core.domain.model.PolicyLevel
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicyTargetType
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.model.WebNavigationPolicy
import com.contentfilter.core.domain.model.TechnicalDiagnostic
import com.contentfilter.core.domain.repository.AppGroupRepository
import com.contentfilter.core.domain.repository.DeviceRepository
import com.contentfilter.core.domain.repository.ExtraTimeGrantRepository
import com.contentfilter.core.domain.repository.TelemetryRepository
import com.contentfilter.core.domain.usecase.admin.DeleteDailyLimitUseCase
import com.contentfilter.core.domain.usecase.admin.DeletePolicyRuleUseCase
import com.contentfilter.core.domain.usecase.admin.ObserveDailyLimitsUseCase
import com.contentfilter.core.domain.usecase.admin.ObserveDevicesUseCase
import com.contentfilter.core.domain.usecase.admin.ObservePolicyRulesUseCase
import com.contentfilter.core.domain.usecase.admin.SaveDailyLimitUseCase
import com.contentfilter.core.domain.usecase.admin.SavePolicyRuleUseCase
import com.contentfilter.core.network.dto.RemoteInstalledAppDto
import com.contentfilter.core.network.remote.RemoteInstalledAppRepository
import com.contentfilter.core.network.remote.RemoteResult
import com.contentfilter.core.network.remote.SupabaseActivationClient
import com.contentfilter.core.network.remote.SupabaseDevMaintenanceClient
import com.contentfilter.core.sync.SyncScheduler
import com.contentfilter.core.sync.engine.SyncEngine
import com.contentfilter.core.sync.engine.SyncResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class RulesViewModel
    @Inject
    constructor(
        observePolicyRules: ObservePolicyRulesUseCase,
        observeDailyLimits: ObserveDailyLimitsUseCase,
        observeDevices: ObserveDevicesUseCase,
        private val saveRule: SavePolicyRuleUseCase,
        private val deleteRule: DeletePolicyRuleUseCase,
        private val saveDailyLimit: SaveDailyLimitUseCase,
        private val deleteDailyLimitUseCase: DeleteDailyLimitUseCase,
        private val deviceRepository: DeviceRepository,
        private val appGroupRepository: AppGroupRepository,
        grantRepository: ExtraTimeGrantRepository,
        private val remoteInstalledAppRepository: RemoteInstalledAppRepository,
        private val activationClient: SupabaseActivationClient,
        private val devMaintenanceClient: SupabaseDevMaintenanceClient,
        private val syncScheduler: SyncScheduler,
        private val syncEngine: SyncEngine,
        private val telemetryRepository: TelemetryRepository,
    ) : ViewModel() {
        private val form =
            MutableStateFlow(
                RulesUiState(),
            )
        private val installedApps = MutableStateFlow<List<RemoteInstalledAppDto>>(emptyList())
        private var internetSaveRequestId = 0L
        private val selectedPolicyDeviceId = form.map { it.selectedDeviceId }.distinctUntilChanged()
        private val policyRules = selectedPolicyDeviceId.flatMapLatest { observePolicyRules(it) }
        private val dailyLimits = selectedPolicyDeviceId.flatMapLatest { observeDailyLimits(it) }
        private val appGroups = selectedPolicyDeviceId.flatMapLatest { appGroupRepository.observeGroups(it) }
        private val extraTimeGrants = grantRepository.observeGrants()
        private val nowTicks =
            flow {
                while (true) {
                    emit(System.currentTimeMillis())
                    delay(MinuteMillis)
                }
            }
        private val policyState =
            combine(
                policyRules,
                dailyLimits,
                appGroups,
                extraTimeGrants,
                nowTicks,
            ) { rules, limits, groups, grants, nowEpochMillis ->
                RulesPolicyState(
                    rules = rules,
                    limits = limits,
                    appGroups = groups,
                    grants = grants,
                    nowEpochMillis = nowEpochMillis,
                )
            }

        val uiState =
            combine(
                policyState,
                observeDevices(),
                installedApps,
                form,
            ) { policy, devices, apps, formState ->
                val userDevices = devices.toUserDevices(apps)
                val selectedDeviceId = userDevices.selectedDeviceId(formState.selectedDeviceId)
                val savedInternetBlocked = policy.rules.internetBlocked()
                formState.copy(
                    rules = policy.rules.sortedWith(compareBy({ it.scope.name }, { it.target })),
                    limits = policy.limits.sortedWith(compareBy({ it.targetType.name }, { it.target })),
                    appGroups =
                        policy.appGroups.map { group ->
                            AppGroupUiState(
                                id = group.id,
                                name = group.name,
                                limitMinutes = group.limitMinutes,
                                resetLabel = "12 PM",
                                appPackages = group.apps.filter { it.enabled }.map { it.packageName }.sorted(),
                            )
                        },
                    userDevices = userDevices,
                    selectedDeviceId = selectedDeviceId,
                    internetBlocked = formState.pendingInternetBlocked ?: savedInternetBlocked,
                    searchEnginesAllowed = formState.pendingSearchEnginesAllowed ?: policy.rules.searchEnginesAllowed(),
                    googleResultsAllowed =
                        formState.pendingGoogleResultsAllowed ?: policy.rules.googleResultsAllowedForWeb(),
                    imagesBlocked =
                        formState.pendingImagesBlocked ?: policy.rules.imagesBlockedForWeb(),
                    safeSearchEnabled =
                        formState.pendingSafeSearchEnabled ?: policy.rules.safeSearchEnabledForWeb(),
                    appControls =
                        if (selectedDeviceId == null) {
                            emptyList()
                        } else {
                            apps
                                .forSelectedUserDevice(selectedDeviceId, devices)
                                .toAppControls(
                                    rules = policy.rules,
                                    limits = policy.limits,
                                    grants = policy.grants,
                                    appGroups = policy.appGroups,
                                    nowEpochMillis = policy.nowEpochMillis,
                                    devices = devices,
                                    pendingAllowed = formState.pendingAppAllowed,
                                )
                        }
                            .filterBySearch(formState.appSearchQuery),
                    offlineMode = false,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = form.value,
            )

        fun onAppPackageChanged(value: String) {
            form.update { it.copy(appPackageName = value, message = "") }
        }

        fun onDomainLimitDomainChanged(value: String) {
            form.update { it.copy(domainLimitDomain = value, message = "") }
        }

        fun onDomainLimitMinutesChanged(value: String) {
            form.update { it.copy(domainLimitMinutes = value.filter(Char::isDigit), message = "") }
        }

        fun onAllowDomainChanged(value: String) {
            form.update { it.copy(allowDomain = value, message = "") }
        }

        fun onAllowDomainMinutesChanged(value: String) {
            form.update { it.copy(allowDomainMinutes = value.filter(Char::isDigit), message = "") }
        }

        fun onLimitPackageChanged(value: String) {
            form.update { it.copy(limitPackageName = value, message = "") }
        }

        fun onLimitMinutesChanged(value: String) {
            form.update { it.copy(limitMinutes = value.filter(Char::isDigit), message = "") }
        }

        fun onAppSearchChanged(value: String) {
            form.update { it.copy(appSearchQuery = value) }
        }

        fun onGroupNameChanged(value: String) {
            form.update { it.copy(groupName = value, message = "") }
        }

        fun onGroupMinutesChanged(value: String) {
            form.update { it.copy(groupMinutes = value.filter(Char::isDigit), message = "") }
        }

        fun onGroupAppToggled(
            packageName: String,
            selected: Boolean,
        ) {
            form.update {
                it.copy(
                    groupSelectedPackages =
                        if (selected) {
                            it.groupSelectedPackages + packageName
                        } else {
                            it.groupSelectedPackages - packageName
                        },
                    message = "",
                )
            }
        }

        fun editAppGroup(groupId: String) {
            val group = uiState.value.appGroups.firstOrNull { it.id == groupId } ?: return
            form.update {
                it.copy(
                    editingGroupId = group.id,
                    groupName = group.name,
                    groupMinutes = group.limitMinutes.toString(),
                    groupSelectedPackages = group.appPackages.toSet(),
                    message = "",
                )
            }
        }

        fun cancelAppGroupEdit() {
            form.update {
                it.copy(
                    editingGroupId = null,
                    groupName = "",
                    groupMinutes = "",
                    groupSelectedPackages = emptySet(),
                    message = "",
                )
            }
        }

        fun onPairingUserNameChanged(value: String) {
            form.update { it.copy(pairingUserName = value, message = "") }
        }

        fun refreshApps() {
            form.update { it.copy(message = "Cargando apps...") }
            refreshInstalledApps()
        }

        fun refreshDevices() {
            form.update { it.copy(message = "Actualizando dispositivos...") }
            viewModelScope.launch(Dispatchers.IO) {
                val devicesResult = syncEngine.syncDevicesFull()
                syncEngine.syncCoreDataFull()
                refreshInstalledApps()
                form.update {
                    it.copy(message = devicesResult.message.takeIf { message -> message.isNotBlank() }.orEmpty())
                }
            }
        }

        fun generatePairingCode() {
            val userName = form.value.pairingUserName.trim()
            if (userName.isBlank()) {
                form.update { it.copy(message = "Ingresá el nombre del usuario.") }
                return
            }
            viewModelScope.launch {
                form.update { it.copy(pairingLoading = true, message = "Generando código...") }
                when (val result = activationClient.createDevicePairingCode(ttlMinutes = UserPairingTokenTtlMinutes)) {
                    is RemoteResult.Success ->
                        form.update {
                            it.copy(
                                pairingCode = result.value.code.withPairingName(userName),
                                pairingExpiresAt = result.value.expiresAt,
                                pairingLoading = false,
                                message = "Código listo. Pasalo a la App Usuario.",
                            )
                        }
                    is RemoteResult.Failure ->
                        form.update {
                            it.copy(pairingLoading = false, message = "No se pudo generar código.")
                        }
                }
            }
        }

        fun clearPairingCode() {
            form.update {
                it.copy(
                    pairingUserName = "",
                    pairingCode = "",
                    pairingExpiresAt = "",
                    message = "Código copiado.",
                )
            }
        }

        fun onDeviceSelected(deviceId: String) {
            form.update {
                it.copy(
                    selectedDeviceId = deviceId,
                    appSearchQuery = "",
                    editingGroupId = null,
                    groupName = "",
                    groupMinutes = "",
                    groupSelectedPackages = emptySet(),
                    message = "Cargando apps...",
                )
            }
            refreshInstalledApps()
        }

        fun clearDeviceSelection() {
            form.update {
                it.copy(
                    selectedDeviceId = null,
                    appSearchQuery = "",
                    editingGroupId = null,
                    groupName = "",
                    groupMinutes = "",
                    groupSelectedPackages = emptySet(),
                    message = "",
                )
            }
        }

        fun deleteDevicePermanently(deviceId: String) {
            form.update {
                it.copy(
                    pendingDeviceDeleteIds = it.pendingDeviceDeleteIds + deviceId,
                    message = "Borrando dispositivo definitivamente...",
                )
            }
            viewModelScope.launch(Dispatchers.IO) {
                when (devMaintenanceClient.purgeDevice(deviceId)) {
                    is RemoteResult.Success -> {
                        deviceRepository.deleteDevice(deviceId)
                        installedApps.update { apps -> apps.filterNot { it.deviceId == deviceId } }
                        syncEngine.syncDevicesFull()
                        form.update {
                            it.copy(
                                selectedDeviceId = it.selectedDeviceId.takeUnless { selected -> selected == deviceId },
                                pendingDeviceDeleteIds = it.pendingDeviceDeleteIds - deviceId,
                                message = "Dispositivo borrado definitivamente.",
                            )
                        }
                    }
                    is RemoteResult.Failure -> {
                        form.update {
                            it.copy(
                                pendingDeviceDeleteIds = it.pendingDeviceDeleteIds - deviceId,
                                message = "No se pudo borrar definitivamente el dispositivo.",
                            )
                        }
                    }
                }
            }
        }

        fun createBlockedAppRule() = createRule(RuleScope.App, RuleAction.Block)

        fun createAllowedDomainRule() = createRule(RuleScope.Domain, RuleAction.Allow)

        fun createAppLimit() = createLimit(PolicyTargetType.App)

        fun createDomainLimit() = createLimit(PolicyTargetType.Domain)

        fun setSearchEnginesAllowed(allowed: Boolean) {
            val targetDeviceId = selectedDeviceIdForRules() ?: return
            val previousState = uiState.value.searchEnginesAllowed
            val requestId =
                beginInternetSave(
                    action = "search-engines",
                    deviceId = targetDeviceId,
                    previousState = previousState.toString(),
                    requestedState = allowed.toString(),
                ) ?: return
            form.update {
                it.copy(
                    internetSaving = true,
                    pendingSearchEnginesAllowed = allowed,
                    message = if (allowed) "Permitiendo buscadores..." else "Bloqueando buscadores...",
                )
            }
            logSearchEngineSwitch(
                stage = "tap",
                allowed = allowed,
                deviceId = targetDeviceId,
                pending = allowed,
                roomAllowed = uiState.value.rules.searchEnginesAllowed(),
            )
            viewModelScope.launch {
                val saved =
                    runCatching {
                        setSearchEnginesAllowedRules(allowed, targetDeviceId)
                        withTimeoutOrNull(RoomConfirmTimeoutMillis) {
                            policyRules.first { it.searchEnginesStateConfirmed(allowed) }
                        } ?: error("Room no confirmó buscadores.")
                    }
                if (saved.isFailure) {
                    Log.e(
                        LogTag,
                        "searchEngineSwitch save failed requestId=$requestId allowed=$allowed deviceId=$targetDeviceId reason=${saved.exceptionOrNull()?.javaClass?.simpleName}",
                        saved.exceptionOrNull(),
                    )
                    if (isCurrentInternetSave(requestId)) {
                        recordAdminDiagnostic(
                            action = "searchEngineSwitch",
                            deviceId = targetDeviceId,
                            requestId = requestId,
                            previousState = previousState.toString(),
                            requestedState = allowed.toString(),
                            result = "save-failed",
                            reason = saved.exceptionOrNull()?.javaClass?.simpleName ?: "unknown",
                        )
                        form.update {
                            it.copy(
                                internetSaving = false,
                                pendingSearchEnginesAllowed = null,
                                message = "No se pudo cambiar buscadores. Intentá otra vez.",
                            )
                        }
                    }
                    return@launch
                }
                logSearchEngineSwitch(
                    stage = "room-confirmed",
                    allowed = allowed,
                    deviceId = targetDeviceId,
                    pending = form.value.pendingSearchEnginesAllowed,
                    roomAllowed = saved.getOrThrow().searchEnginesAllowed(),
                )
                val confirmedAllowed = saved.getOrThrow().searchEnginesAllowed()
                syncScheduler.requestSync()
                val synced = syncNowWithResult()
                Log.i(
                    LogTag,
                    "searchEngineSwitch sync finished requestId=$requestId allowed=$allowed deviceId=$targetDeviceId success=${synced.success} message=${synced.message}",
                )
                recordAdminDiagnostic(
                    action = "searchEngineSwitch",
                    deviceId = targetDeviceId,
                    requestId = requestId,
                    previousState = previousState.toString(),
                    requestedState = allowed.toString(),
                    result = if (synced.success) "synced" else "local-only",
                    reason = synced.message,
                )
                if (!isCurrentInternetSave(requestId)) {
                    Log.i(LogTag, "internetSave stale response ignored requestId=$requestId action=search-engines")
                    return@launch
                }
                form.update {
                    it.copy(
                        internetSaving = false,
                        pendingSearchEnginesAllowed = null,
                        message =
                            if (synced.success) {
                                if (confirmedAllowed) "Buscadores permitidos." else "Buscadores bloqueados."
                            } else {
                                if (confirmedAllowed) {
                                    "Buscadores permitidos localmente. Se sincronizará cuando haya conexión."
                                } else {
                                    "Buscadores bloqueados localmente. Se sincronizará cuando haya conexión."
                                }
                            },
                    )
                }
                logSearchEngineSwitch(
                    stage = "ui-final",
                    allowed = allowed,
                    deviceId = targetDeviceId,
                    pending = null,
                    roomAllowed = uiState.value.rules.searchEnginesAllowed(),
                )
            }
        }

        fun saveAllowedDomainLimit() {
            val targetDeviceId = selectedDeviceIdForRules() ?: return
            val target = normalizeLimitTarget(PolicyTargetType.Domain, form.value.allowDomain)
            val minutes = form.value.allowDomainMinutes.toIntOrNull()
            if (target == null || minutes == null || minutes <= 0) {
                form.update { it.copy(message = "Ingresá sitio permitido y minutos válidos.") }
                return
            }
            val requestId =
                beginInternetSave(
                    action = "allow-domain-limit",
                    deviceId = targetDeviceId,
                    previousState = "domain=${uiState.value.rules.any { it.target == target && it.enabled }}",
                    requestedState = "domain=$target minutes=$minutes",
                ) ?: return
            viewModelScope.launch {
                val saved =
                    runCatching {
                        saveAllowedDomain(target, targetDeviceId)
                        saveDomainDailyLimit(target, minutes, targetDeviceId)
                        syncScheduler.requestSync()
                        syncNowWithResult()
                    }
                if (!isCurrentInternetSave(requestId)) {
                    Log.i(LogTag, "internetSave stale response ignored requestId=$requestId action=allow-domain-limit")
                    return@launch
                }
                val result = saved.getOrNull()
                Log.i(
                    LogTag,
                    "internetSave finished requestId=$requestId action=allow-domain-limit deviceId=$targetDeviceId result=${saved.isSuccess} syncSuccess=${result?.success} message=${result?.message.orEmpty()}",
                )
                form.update {
                    if (saved.isSuccess) {
                        it.copy(
                            internetSaving = false,
                            allowDomain = "",
                            allowDomainMinutes = "",
                            message =
                                if (result?.success == false) {
                                    "Sitio permitido guardado localmente. Se sincronizará cuando haya conexión."
                                } else {
                                    "Sitio permitido con límite guardado."
                                },
                        )
                    } else {
                        it.copy(
                            internetSaving = false,
                            message = "No se pudo guardar el sitio permitido con límite.",
                        )
                    }
                }
            }
        }

        fun setInternetBlocked(blocked: Boolean) {
            val targetDeviceId = uiState.value.selectedDeviceId
            if (targetDeviceId == null) {
                form.update { it.copy(message = "Seleccioná un dispositivo.") }
                return
            }
            val currentState = uiState.value
            val previousRoom = currentState.rules.internetBlocked()
            val requestId =
                beginInternetSave(
                    action = "internet-mode",
                    deviceId = targetDeviceId,
                    previousState = previousRoom.toString(),
                    requestedState = blocked.toString(),
                ) ?: return
            logInternetSwitch(
                stage = "tap",
                touched = blocked,
                pending = form.value.pendingInternetBlocked,
                room = previousRoom,
                ui = currentState.internetBlocked,
            )
            Log.i(
                LogTag,
                "webNavigation admin requested deviceId=$targetDeviceId requested=$blocked storedBefore=$previousRoom",
            )
            form.update {
                it.copy(
                    internetSaving = true,
                    pendingInternetBlocked = blocked,
                    pendingSearchEnginesAllowed = if (blocked) false else it.pendingSearchEnginesAllowed,
                    pendingGoogleResultsAllowed = if (blocked) it.pendingGoogleResultsAllowed else false,
                    pendingImagesBlocked = if (blocked) it.pendingImagesBlocked else false,
                    message = "Guardando...",
                )
            }
            logInternetSwitch(
                stage = "pending",
                touched = blocked,
                pending = blocked,
                room = previousRoom,
                ui = blocked,
            )
            viewModelScope.launch {
                val saved =
                    runCatching {
                        setRulesForDomain(
                            target = WebNavigationPolicy.RuleTarget,
                            action = RuleAction.Block,
                            enabled = blocked,
                            priority = WebNavigationBlockPriority,
                            deviceId = targetDeviceId,
                        )
                        setRulesForDomain(
                            target = DomainWildcard,
                            action = RuleAction.Block,
                            enabled = false,
                            priority = InternetBlockPriority,
                            deviceId = targetDeviceId,
                        )
                        if (blocked) {
                            clearLegacyDomainBlockRules(targetDeviceId)
                            setSearchEnginesAllowedRules(allowed = false, targetDeviceId = targetDeviceId)
                            setGoogleResultsAllowedRules(allowed = false, targetDeviceId = targetDeviceId)
                            setImagesBlockedRules(blocked = uiState.value.imagesBlocked, targetDeviceId = targetDeviceId)
                            setSafeSearchRules(enabled = uiState.value.safeSearchEnabled, targetDeviceId = targetDeviceId)
                        } else {
                            clearLegacyDomainBlockRules(targetDeviceId)
                            setSearchEnginesAllowedRules(allowed = true, targetDeviceId = targetDeviceId)
                            setGoogleResultsAllowedRules(allowed = false, targetDeviceId = targetDeviceId)
                            setImagesBlockedRules(blocked = false, targetDeviceId = targetDeviceId)
                        }
                        withTimeoutOrNull(RoomConfirmTimeoutMillis) {
                            policyRules.first {
                                it.internetBlocked() == blocked &&
                                    if (blocked) {
                                        it.searchEnginesStateConfirmed(false)
                                    } else {
                                        true
                                    }
                            }
                        } ?: error("Room no confirmó el nuevo estado de Internet.")
                    }
                val roomAfterSave = saved.getOrNull()?.internetBlocked() ?: previousRoom
                Log.i(
                    LogTag,
                    "webNavigation admin stored deviceId=$targetDeviceId requested=$blocked stored=$roomAfterSave",
                )
                if (saved.isSuccess) {
                    logInternetSwitch(
                        stage = "room-confirmed",
                        touched = blocked,
                        pending = form.value.pendingInternetBlocked,
                        room = roomAfterSave,
                        ui = uiState.value.internetBlocked,
                    )
                    syncScheduler.requestSync()
                    val synced = syncNow()
                    Log.i(
                        LogTag,
                        "webNavigation admin synced deviceId=$targetDeviceId requested=$blocked stored=$roomAfterSave syncSuccess=$synced",
                    )
                    recordAdminDiagnostic(
                        action = "internetSave",
                        deviceId = targetDeviceId,
                        requestId = requestId,
                        previousState = previousRoom.toString(),
                        requestedState = blocked.toString(),
                        result = if (synced) "synced" else "local-only",
                        reason = "internet-mode",
                    )
                    if (synced) {
                        if (!isCurrentInternetSave(requestId)) {
                            Log.i(LogTag, "internetSave stale response ignored requestId=$requestId action=internet-mode")
                            return@launch
                        }
                        form.update {
                            it.copy(
                                internetSaving = false,
                                pendingInternetBlocked = null,
                                pendingSearchEnginesAllowed = null,
                                pendingGoogleResultsAllowed = null,
                                pendingImagesBlocked = null,
                                pendingSafeSearchEnabled = null,
                                message =
                                    if (blocked) {
                                        "Bloquear navegación web activado."
                                    } else {
                                        "Navegación web permitida."
                                    },
                            )
                        }
                        logInternetSwitch(
                            stage = "ui-final",
                            touched = blocked,
                            pending = null,
                            room = roomAfterSave,
                            ui = blocked,
                        )
                    } else {
                        if (!isCurrentInternetSave(requestId)) {
                            Log.i(LogTag, "internetSave stale response ignored requestId=$requestId action=internet-mode")
                            return@launch
                        }
                        form.update {
                            it.copy(
                                internetSaving = false,
                                pendingInternetBlocked = null,
                                pendingSearchEnginesAllowed = null,
                                pendingGoogleResultsAllowed = null,
                                pendingImagesBlocked = null,
                                pendingSafeSearchEnabled = null,
                                message = "Cambio guardado localmente. Se sincronizará cuando haya conexión.",
                            )
                        }
                    }
                } else {
                    if (isCurrentInternetSave(requestId)) {
                        recordAdminDiagnostic(
                            action = "internetSave",
                            deviceId = targetDeviceId,
                            requestId = requestId,
                            previousState = previousRoom.toString(),
                            requestedState = blocked.toString(),
                            result = "save-failed",
                            reason = saved.exceptionOrNull()?.javaClass?.simpleName ?: "unknown",
                        )
                        form.update {
                            it.copy(
                                internetSaving = false,
                                pendingInternetBlocked = null,
                                pendingSearchEnginesAllowed = null,
                                pendingGoogleResultsAllowed = null,
                                pendingImagesBlocked = null,
                                pendingSafeSearchEnabled = null,
                                message = "No se pudo cambiar el modo web. Intentá otra vez.",
                            )
                        }
                    }
                    logInternetSwitch(
                        stage = "save-failed",
                        touched = blocked,
                        pending = null,
                        room = previousRoom,
                        ui = previousRoom,
                    )
                }
            }
        }

        fun setGoogleResultsAllowed(allowed: Boolean) {
            setWebOption(
                action = "google-results",
                requestedState = allowed,
                pending = { state -> state.copy(pendingGoogleResultsAllowed = allowed) },
                save = { deviceId -> setGoogleResultsAllowedRules(allowed, deviceId) },
                successMessage =
                    if (allowed) {
                        "Resultados de Google permitidos."
                    } else {
                        "Resultados de Google bloqueados."
                    },
            )
        }

        fun setImagesBlocked(blocked: Boolean) {
            setWebOption(
                action = "images-blocked",
                requestedState = blocked,
                pending = { state -> state.copy(pendingImagesBlocked = blocked) },
                save = { deviceId -> setImagesBlockedRules(blocked, deviceId) },
                successMessage =
                    if (blocked) {
                        "Fotos e imágenes bloqueadas."
                    } else {
                        "Fotos e imágenes permitidas."
                    },
            )
        }

        fun setSafeSearchEnabled(enabled: Boolean) {
            setWebOption(
                action = "safe-search",
                requestedState = enabled,
                pending = { state -> state.copy(pendingSafeSearchEnabled = enabled) },
                save = { deviceId -> setSafeSearchRules(enabled, deviceId) },
                successMessage =
                    if (enabled) {
                        "SafeSearch activado."
                    } else {
                        "SafeSearch desactivado."
                    },
            )
        }

        private fun setWebOption(
            action: String,
            requestedState: Boolean,
            pending: (RulesUiState) -> RulesUiState,
            save: suspend (String) -> Unit,
            successMessage: String,
        ) {
            val targetDeviceId = uiState.value.selectedDeviceId
            if (targetDeviceId == null) {
                form.update { it.copy(message = "Seleccioná un dispositivo.") }
                return
            }
            val requestId =
                beginInternetSave(
                    action = action,
                    deviceId = targetDeviceId,
                    previousState = "web-option",
                    requestedState = requestedState.toString(),
                ) ?: return
            form.update { pending(it).copy(internetSaving = true, message = "Guardando...") }
            viewModelScope.launch {
                val saved =
                    runCatching {
                        save(targetDeviceId)
                        syncScheduler.requestSync()
                        syncNowWithResult()
                    }
                if (!isCurrentInternetSave(requestId)) return@launch
                val syncResult = saved.getOrNull()
                form.update {
                    it.copy(
                        internetSaving = false,
                        pendingGoogleResultsAllowed = null,
                        pendingImagesBlocked = null,
                        pendingSafeSearchEnabled = null,
                        message =
                            if (saved.isSuccess) {
                                if (syncResult?.success == false) {
                                    "Cambio guardado localmente. Se sincronizará cuando haya conexión."
                                } else {
                                    successMessage
                                }
                            } else {
                                "No se pudo guardar el cambio web."
                            },
                    )
                }
            }
        }

        fun toggle(rule: PolicyRule) {
            val targetDeviceId = selectedDeviceIdForRules() ?: return
            val requestId =
                if (rule.scope == RuleScope.Domain) {
                    beginInternetSave(
                        action = "toggle-domain-rule",
                        deviceId = targetDeviceId,
                        previousState = "${rule.target}:${rule.enabled}",
                        requestedState = "${rule.target}:${!rule.enabled}",
                    ) ?: return
                } else {
                    null
                }
            viewModelScope.launch {
                val saved =
                    runCatching {
                        saveRule(rule.copy(enabled = !rule.enabled), targetDeviceId)
                        syncScheduler.requestSync()
                        syncNowWithResult()
                    }
                if (requestId != null) {
                    if (!isCurrentInternetSave(requestId)) {
                        Log.i(LogTag, "internetSave stale response ignored requestId=$requestId action=toggle-domain-rule")
                        return@launch
                    }
                    val result = saved.getOrNull()
                    Log.i(
                        LogTag,
                        "internetSave finished requestId=$requestId action=toggle-domain-rule deviceId=$targetDeviceId result=${saved.isSuccess} syncSuccess=${result?.success} message=${result?.message.orEmpty()}",
                    )
                    form.update {
                        it.copy(
                            internetSaving = false,
                            message =
                                if (saved.isSuccess) {
                                    "Regla actualizada."
                                } else {
                                    "No se pudo actualizar la regla."
                                },
                        )
                    }
                }
            }
        }

        fun delete(rule: PolicyRule) {
            val targetDeviceId = selectedDeviceIdForRules() ?: return
            val requestId =
                if (rule.scope == RuleScope.Domain) {
                    beginInternetSave(
                        action = "delete-domain-rule",
                        deviceId = targetDeviceId,
                        previousState = "${rule.target}:enabled=${rule.enabled}",
                        requestedState = "${rule.target}:deleted",
                    ) ?: return
                } else {
                    null
                }
            viewModelScope.launch {
                val associatedLimit = rule.associatedDomainLimit()
                val saved =
                    runCatching {
                        deleteRule(rule)
                        if (associatedLimit != null) {
                            deleteDailyLimitUseCase(associatedLimit)
                        }
                        syncScheduler.requestSync()
                        syncNowWithResult()
                    }
                if (requestId != null && !isCurrentInternetSave(requestId)) {
                    Log.i(LogTag, "internetSave stale response ignored requestId=$requestId action=delete-domain-rule")
                    return@launch
                }
                val result = saved.getOrNull()
                Log.i(
                    LogTag,
                    "internetSave finished requestId=${requestId ?: 0} action=delete-rule deviceId=$targetDeviceId result=${saved.isSuccess} syncSuccess=${result?.success} message=${result?.message.orEmpty()}",
                )
                form.update {
                    it.copy(
                        internetSaving = if (requestId != null) false else it.internetSaving,
                        message =
                            if (!saved.isSuccess) {
                                "No se pudo eliminar la regla."
                            } else if (associatedLimit != null) {
                                "Regla y límite asociado eliminados."
                            } else {
                                "Regla eliminada."
                            },
                    )
                }
            }
        }

        fun deleteDomainLimit(limit: DailyLimit) {
            val targetDeviceId = selectedDeviceIdForRules() ?: return
            val requestId =
                beginInternetSave(
                    action = "delete-domain-limit",
                    deviceId = targetDeviceId,
                    previousState = "${limit.target}:enabled=${limit.enabled}",
                    requestedState = "${limit.target}:deleted",
                ) ?: return
            viewModelScope.launch {
                val saved =
                    runCatching {
                        deleteDailyLimitUseCase(limit)
                        syncScheduler.requestSync()
                        syncNowWithResult()
                    }
                if (!isCurrentInternetSave(requestId)) {
                    Log.i(LogTag, "internetSave stale response ignored requestId=$requestId action=delete-domain-limit")
                    return@launch
                }
                val result = saved.getOrNull()
                Log.i(
                    LogTag,
                    "internetSave finished requestId=$requestId action=delete-domain-limit deviceId=$targetDeviceId result=${saved.isSuccess} syncSuccess=${result?.success} message=${result?.message.orEmpty()}",
                )
                form.update {
                    it.copy(
                        internetSaving = false,
                        message =
                            if (saved.isSuccess) {
                                "Límite de dominio eliminado."
                            } else {
                                "No se pudo eliminar el límite de dominio."
                            },
                    )
                }
            }
        }

        fun setAppAllowed(
            packageName: String,
            allowed: Boolean,
        ) {
            val targetDeviceId = selectedDeviceIdForRules() ?: return
            val matchingRules =
                uiState.value.rules.filter {
                    it.scope == RuleScope.App && it.target == packageName
                }
            val blockRules = matchingRules.filter { it.action == RuleAction.Block }
            val allowRules = matchingRules.filter { it.action == RuleAction.Allow }
            val authorizationRules = matchingRules.filter { it.action == RuleAction.RequestAuthorization }
            val appLimits =
                uiState.value.limits.filter {
                    it.targetType == PolicyTargetType.App && it.target == packageName
                }
            form.update {
                it.copy(
                    pendingAppAllowed = it.pendingAppAllowed + (packageName to allowed),
                    message = "Guardando...",
                )
            }
            viewModelScope.launch {
                val synced =
                    runCatching {
                        appLimits.forEach { deleteDailyLimitUseCase(it) }
                        if (allowed) {
                            blockRules.filter { it.enabled }.forEach {
                                saveRule(it.copy(enabled = false), targetDeviceId)
                            }
                            authorizationRules.filter { it.enabled }.forEach {
                                saveRule(it.copy(enabled = false), targetDeviceId)
                            }
                        } else {
                            allowRules.filter { it.enabled }.forEach {
                                saveRule(it.copy(enabled = false), targetDeviceId)
                            }
                            authorizationRules.filter { it.enabled }.forEach {
                                saveRule(it.copy(enabled = false), targetDeviceId)
                            }
                            saveRule(
                                blockRules.firstOrNull()?.copy(enabled = true)
                                    ?: PolicyRule(
                                        id = UUID.randomUUID().toString(),
                                        level = PolicyLevel.Account,
                                        scope = RuleScope.App,
                                        target = packageName,
                                        action = RuleAction.Block,
                                        priority = 100,
                                        enabled = true,
                                    ),
                                targetDeviceId,
                            )
                        }
                        syncScheduler.requestSync()
                        withContext(Dispatchers.IO) { syncEngine.syncCoreDataFull().success }
                    }.getOrDefault(false)
                form.update {
                    it.copy(
                        message =
                            when {
                                synced && allowed -> "App permitida."
                                synced -> "App bloqueada."
                                else -> "No se pudo guardar en la nube. Se sincronizará cuando haya conexión."
                            },
                    )
                }
                kotlinx.coroutines.delay(SwitchHoldMillis)
                form.update { it.copy(pendingAppAllowed = it.pendingAppAllowed - packageName) }
            }
        }

        fun saveAppControlLimit(
            packageName: String,
            rawMinutes: String,
        ) {
            val targetDeviceId = selectedDeviceIdForRules() ?: return
            val minutes = rawMinutes.filter(Char::isDigit).toIntOrNull()
            val existing =
                uiState.value.limits.firstOrNull {
                    it.targetType == PolicyTargetType.App && it.target == packageName
                }
            val matchingRules =
                uiState.value.rules.filter {
                    it.scope == RuleScope.App && it.target == packageName
                }
            viewModelScope.launch {
                if (minutes == null || minutes <= 0) {
                    form.update { it.copy(message = "Ingresá minutos válidos.") }
                } else {
                    form.update { it.copy(message = "Guardando...") }
                    matchingRules
                        .filter { it.enabled && (it.action == RuleAction.Block || it.action == RuleAction.RequestAuthorization) }
                        .forEach { saveRule(it.copy(enabled = false), targetDeviceId) }
                    saveDailyLimit(
                        DailyLimit(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            targetType = PolicyTargetType.App,
                            target = packageName,
                            limitMinutes = minutes,
                            enabled = true,
                        ),
                        targetDeviceId,
                    )
                    form.update { it.copy(message = "Tiempo guardado.") }
                }
                syncScheduler.requestSync()
                syncNow()
            }
        }

        fun saveAppGroup() {
            val targetDeviceId = selectedDeviceIdForRules() ?: return
            val state = form.value
            val name = state.groupName.trim()
            val minutes = state.groupMinutes.toIntOrNull()
            val packages = state.groupSelectedPackages.toList().sorted()
            val editingGroupId = state.editingGroupId
            val existingGroups = uiState.value.appGroups
            val duplicateName =
                existingGroups.firstOrNull {
                    it.id != editingGroupId && it.name.equals(name, ignoreCase = true)
                }
            val packageInOtherGroup =
                existingGroups.firstOrNull { group ->
                    group.id != editingGroupId && group.appPackages.any { it in packages }
                }
            when {
                name.isBlank() -> {
                    form.update { it.copy(message = "Ingresá un nombre para el grupo.") }
                    return
                }
                duplicateName != null -> {
                    form.update { it.copy(message = "Ya existe un grupo con ese nombre.") }
                    return
                }
                minutes == null || minutes <= 0 -> {
                    form.update { it.copy(message = "Ingresá minutos válidos para el grupo.") }
                    return
                }
                packages.isEmpty() -> {
                    form.update { it.copy(message = "Elegí al menos una app para el grupo.") }
                    return
                }
                packageInOtherGroup != null -> {
                    form.update { it.copy(message = "Una app elegida ya está en ${packageInOtherGroup.name}. Sacala de ese grupo o editá ese grupo.") }
                    return
                }
            }
            viewModelScope.launch {
                form.update { it.copy(groupSaving = true, message = if (editingGroupId == null) "Guardando grupo..." else "Actualizando grupo...") }
                val saved =
                    runCatching {
                        val group =
                            AppGroup(
                                id = editingGroupId ?: UUID.randomUUID().toString(),
                                deviceId = targetDeviceId,
                                name = name,
                                color = "teal",
                                limitMinutes = minutes,
                                resetMinuteOfDay = NoonMinuteOfDay,
                                enabled = true,
                            )
                        appGroupRepository.replaceGroupApps(group, packages)
                        syncScheduler.requestSync()
                        syncNowWithResult()
                    }
                form.update {
                    if (saved.isSuccess) {
                        it.copy(
                            editingGroupId = null,
                            groupName = "",
                            groupMinutes = "",
                            groupSelectedPackages = emptySet(),
                            groupSaving = false,
                            message =
                                if (editingGroupId == null) {
                                    "Apps en grupo guardado."
                                } else {
                                    "Apps en grupo actualizado."
                                },
                        )
                    } else {
                        it.copy(groupSaving = false, message = "No se pudo guardar el grupo.")
                    }
                }
            }
        }

        fun deleteAppGroup(groupId: String) {
            val targetDeviceId = selectedDeviceIdForRules() ?: return
            val group = uiState.value.appGroups.firstOrNull { it.id == groupId } ?: return
            form.update {
                it.copy(
                    pendingAppGroupDeleteIds = it.pendingAppGroupDeleteIds + groupId,
                    message = "Borrando grupo...",
                )
            }
            viewModelScope.launch {
                val saved =
                    runCatching {
                        appGroupRepository.deleteGroup(
                            AppGroup(
                                id = group.id,
                                deviceId = targetDeviceId,
                                name = group.name,
                                color = "teal",
                                limitMinutes = group.limitMinutes,
                                resetMinuteOfDay = NoonMinuteOfDay,
                                enabled = false,
                            ),
                        )
                        syncScheduler.requestSync()
                        syncNowWithResult()
                    }
                form.update {
                    it.copy(
                        pendingAppGroupDeleteIds = it.pendingAppGroupDeleteIds - groupId,
                        editingGroupId = it.editingGroupId.takeUnless { editingId -> editingId == groupId },
                        groupName = if (it.editingGroupId == groupId) "" else it.groupName,
                        groupMinutes = if (it.editingGroupId == groupId) "" else it.groupMinutes,
                        groupSelectedPackages = if (it.editingGroupId == groupId) emptySet() else it.groupSelectedPackages,
                        message =
                            if (saved.isSuccess) {
                                "Grupo eliminado. Las apps vuelven a sus reglas individuales."
                            } else {
                                "No se pudo eliminar el grupo."
                            },
                    )
                }
            }
        }

        init {
            syncScheduler.requestSync()
            viewModelScope.launch {
                syncNow()
            }
            refreshInstalledApps()
        }

        private fun createRule(
            scope: RuleScope,
            action: RuleAction,
        ) {
            val targetDeviceId = selectedDeviceIdForRules() ?: return
            val state = form.value
            val rawTarget =
                when (scope) {
                    RuleScope.App -> state.appPackageName
                    RuleScope.Domain -> state.allowDomain
                    else -> ""
                }
            val target = normalizeTarget(scope, rawTarget)
            val allowDomainMinutes =
                if (scope == RuleScope.Domain && action == RuleAction.Allow && state.allowDomainMinutes.isNotBlank()) {
                    state.allowDomainMinutes.toIntOrNull()
                } else {
                    null
                }
            if (target == null) {
                form.update {
                    it.copy(
                        message =
                            when (scope) {
                                RuleScope.App -> "Ingresá un paquete válido, por ejemplo com.android.chrome."
                                RuleScope.Domain -> "Ingresá solo un dominio, por ejemplo example.com. No pegues URL completa."
                                else -> "Objetivo inválido."
                            },
                    )
                }
                return
            }
            if (scope == RuleScope.Domain &&
                action == RuleAction.Allow &&
                state.allowDomainMinutes.isNotBlank() &&
                (allowDomainMinutes == null || allowDomainMinutes <= 0)
            ) {
                form.update { it.copy(message = "Ingresá minutos válidos o dejá el campo vacío.") }
                return
            }
            val internetRequestId =
                if (scope == RuleScope.Domain && action == RuleAction.Allow) {
                    beginInternetSave(
                        action = "allow-domain",
                        deviceId = targetDeviceId,
                        previousState = "domain=${uiState.value.rules.any { it.target == target && it.enabled }}",
                        requestedState = "domain=$target minutes=${allowDomainMinutes ?: "none"}",
                    ) ?: return
                } else {
                    null
                }
            viewModelScope.launch {
                val saved =
                    runCatching {
                        if (scope == RuleScope.Domain && action == RuleAction.Allow) {
                            saveAllowedDomain(target, targetDeviceId)
                            if (allowDomainMinutes != null) {
                                saveDomainDailyLimit(target, allowDomainMinutes, targetDeviceId)
                            }
                        } else {
                            saveRule(
                                PolicyRule(
                                    id = UUID.randomUUID().toString(),
                                    level = PolicyLevel.Account,
                                    scope = scope,
                                    target = target,
                                    action = action,
                                    priority = 100,
                                    enabled = true,
                                ),
                                targetDeviceId,
                            )
                        }
                        syncScheduler.requestSync()
                        syncNowWithResult()
                    }
                if (internetRequestId != null && !isCurrentInternetSave(internetRequestId)) {
                    Log.i(LogTag, "internetSave stale response ignored requestId=$internetRequestId action=allow-domain")
                    return@launch
                }
                val result = saved.getOrNull()
                if (internetRequestId != null) {
                    Log.i(
                        LogTag,
                        "internetSave finished requestId=$internetRequestId action=allow-domain deviceId=$targetDeviceId result=${saved.isSuccess} syncSuccess=${result?.success} message=${result?.message.orEmpty()}",
                    )
                }
                form.update {
                    when (scope) {
                        RuleScope.App -> it.copy(appPackageName = "", message = "App bloqueada.")
                        RuleScope.Domain ->
                            if (action == RuleAction.Allow) {
                                if (saved.isSuccess) {
                                    it.copy(
                                        internetSaving = false,
                                        allowDomain = "",
                                        allowDomainMinutes = "",
                                        message =
                                            if (result?.success == false) {
                                                "Sitio permitido guardado localmente. Se sincronizará cuando haya conexión."
                                            } else if (allowDomainMinutes != null) {
                                                "Sitio permitido con límite guardado."
                                            } else {
                                                "Sitio permitido."
                                            },
                                    )
                                } else {
                                    it.copy(
                                        internetSaving = false,
                                        message = "No se pudo guardar el sitio permitido.",
                                    )
                                }
                            } else {
                                it.copy(message = "Regla guardada.")
                            }
                        else -> it.copy(message = "Regla guardada.")
                    }
                }
            }
        }

        private fun createLimit(targetType: PolicyTargetType) {
            val targetDeviceId = selectedDeviceIdForRules() ?: return
            val state = form.value
            val rawTarget =
                when (targetType) {
                    PolicyTargetType.App -> state.limitPackageName
                    PolicyTargetType.Domain -> state.domainLimitDomain
                    else -> ""
                }
            val rawMinutes =
                when (targetType) {
                    PolicyTargetType.App -> state.limitMinutes
                    PolicyTargetType.Domain -> state.domainLimitMinutes
                    else -> ""
                }
            val target = normalizeLimitTarget(targetType, rawTarget)
            val minutes = rawMinutes.toIntOrNull()
            if (target == null || minutes == null || minutes <= 0) {
                form.update {
                    it.copy(
                        message =
                            when (targetType) {
                                PolicyTargetType.Domain -> "Ingresá dominio y minutos válidos."
                                else -> "Ingresá paquete de app y minutos válidos."
                            },
                    )
                }
                return
            }
            val existing = uiState.value.limits.firstOrNull { it.targetType == targetType && it.target == target }
            viewModelScope.launch {
                saveDailyLimit(
                    DailyLimit(
                        id = existing?.id ?: UUID.randomUUID().toString(),
                        targetType = targetType,
                        target = target,
                        limitMinutes = minutes,
                        enabled = true,
                    ),
                    targetDeviceId,
                )
                syncScheduler.requestSync()
                syncNow()
                form.update {
                    when (targetType) {
                        PolicyTargetType.Domain ->
                            it.copy(
                                domainLimitDomain = "",
                                domainLimitMinutes = "",
                                message = "Límite de dominio guardado.",
                            )
                        else -> it.copy(limitPackageName = "", limitMinutes = "", message = "Límite de app guardado.")
                    }
                }
            }
        }

        private fun refreshInstalledApps() {
            viewModelScope.launch(Dispatchers.IO) {
                val deviceSync = syncEngine.syncCoreDataFull()
                val requestResultsSync = syncEngine.syncRequestResultsFull()
                if (BuildConfig.DEBUG) {
                    Log.i(
                        LogTag,
                        "appsRefresh device-sync result=${deviceSync.success} message=${deviceSync.message} " +
                            "requestResultsSync=${requestResultsSync.success} requestResultsMessage=${requestResultsSync.message}",
                    )
                }
                when (val result = remoteInstalledAppRepository.pullInstalledApps()) {
                    is RemoteResult.Success -> {
                        installedApps.value = result.value
                        if (BuildConfig.DEBUG) {
                            val selectedDeviceId = form.value.selectedDeviceId
                            val selectedCount =
                                selectedDeviceId
                                    ?.let { deviceId -> result.value.count { it.deviceId == deviceId } }
                                    ?: 0
                            Log.i(
                                LogTag,
                                "appsRefresh result=success remoteCount=${result.value.size} selectedDeviceId=$selectedDeviceId selectedCount=$selectedCount",
                            )
                        }
                        form.update { state ->
                            state.copy(message = state.message.takeUnless { it == "Cargando apps..." }.orEmpty())
                        }
                    }
                    is RemoteResult.Failure -> {
                        Log.w(LogTag, "appsRefresh result=failure reason=${result.reason}")
                        form.update {
                            it.copy(message = "No se pudo cargar la lista de apps: ${result.reason}")
                        }
                    }
                }
            }
        }

        private suspend fun saveAllowedDomain(
            target: String,
            deviceId: String,
        ) {
            setAllowedDomain(target, true, deviceId)
        }

        private suspend fun saveDomainDailyLimit(
            target: String,
            minutes: Int,
            deviceId: String,
        ) {
            saveDailyLimit(
                DailyLimit(
                    id =
                        uiState.value.limits.firstOrNull {
                            it.targetType == PolicyTargetType.Domain && it.target == target
                        }?.id ?: UUID.randomUUID().toString(),
                    targetType = PolicyTargetType.Domain,
                    target = target,
                    limitMinutes = minutes,
                    enabled = true,
                ),
                deviceId,
            )
        }

        private fun PolicyRule.associatedDomainLimit(): DailyLimit? =
            if (scope == RuleScope.Domain) {
                uiState.value.limits.firstOrNull {
                    it.enabled &&
                        it.targetType == PolicyTargetType.Domain &&
                        it.target == target
                }
            } else {
                null
            }

        private suspend fun setSearchEnginesAllowedRules(
            allowed: Boolean,
            targetDeviceId: String,
        ) {
            SearchEngineDomains.forEach { domain ->
                setSearchEngineDomain(domain, allowed, targetDeviceId)
            }
            SecureDnsDomains.forEach { domain ->
                setSecureDnsDomain(domain, blocked = !allowed, targetDeviceId)
            }
        }

        private suspend fun clearSearchEngineRules(deviceId: String) {
            (SearchEngineDomains + SecureDnsDomains).forEach { domain ->
                setRulesForDomain(
                    target = domain,
                    action = RuleAction.Allow,
                    enabled = false,
                    priority = AllowDomainPriority,
                    deviceId = deviceId,
                )
                setRulesForDomain(
                    target = domain,
                    action = RuleAction.Block,
                    enabled = false,
                    priority = SearchEngineBlockPriority,
                    deviceId = deviceId,
                )
            }
        }

        private suspend fun setGoogleResultsAllowedRules(
            allowed: Boolean,
            targetDeviceId: String,
        ) {
            setRulesForDomain(
                target = WebNavigationPolicy.GoogleResultsAllowedTarget,
                action = RuleAction.Allow,
                enabled = allowed,
                priority = WebNavigationBlockPriority + 10,
                deviceId = targetDeviceId,
            )
            WebNavigationPolicy.GoogleSearchDomains.forEach { domain ->
                setRulesForDomain(
                    target = domain,
                    action = RuleAction.Allow,
                    enabled = allowed,
                    priority = AllowDomainPriority,
                    deviceId = targetDeviceId,
                )
            }
            if (allowed) {
                WebNavigationPolicy.UnsafeSearchDomains.forEach { domain ->
                    setRulesForDomain(
                        target = domain,
                        action = RuleAction.Block,
                        enabled = uiState.value.safeSearchEnabled,
                        priority = SearchEngineBlockPriority,
                        deviceId = targetDeviceId,
                    )
                }
            }
        }

        private suspend fun setImagesBlockedRules(
            blocked: Boolean,
            targetDeviceId: String,
        ) {
            setRulesForDomain(
                target = WebNavigationPolicy.ImagesBlockedTarget,
                action = RuleAction.Block,
                enabled = blocked,
                priority = WebNavigationBlockPriority + 20,
                deviceId = targetDeviceId,
            )
            WebNavigationPolicy.ImageDomains.forEach { domain ->
                setRulesForDomain(
                    target = domain,
                    action = RuleAction.Block,
                    enabled = blocked,
                    priority = SearchEngineBlockPriority + 10,
                    deviceId = targetDeviceId,
                )
            }
        }

        private suspend fun setSafeSearchRules(
            enabled: Boolean,
            targetDeviceId: String,
        ) {
            setRulesForDomain(
                target = WebNavigationPolicy.SafeSearchTarget,
                action = RuleAction.Allow,
                enabled = enabled,
                priority = WebNavigationBlockPriority + 30,
                deviceId = targetDeviceId,
            )
            WebNavigationPolicy.UnsafeSearchDomains.forEach { domain ->
                setRulesForDomain(
                    target = domain,
                    action = RuleAction.Block,
                    enabled = enabled,
                    priority = SearchEngineBlockPriority,
                    deviceId = targetDeviceId,
                )
            }
        }

        private suspend fun clearLegacyDomainBlockRules(deviceId: String) {
            uiState.value.rules
                .filter {
                        it.enabled &&
                        it.scope == RuleScope.Domain &&
                        it.action == RuleAction.Block &&
                        it.target != WebNavigationPolicy.RuleTarget &&
                        it.target != WebNavigationPolicy.GoogleResultsAllowedTarget &&
                        it.target != WebNavigationPolicy.ImagesBlockedTarget &&
                        it.target != WebNavigationPolicy.SafeSearchTarget &&
                        it.target != DomainWildcard &&
                        it.target !in SearchEngineDomains &&
                        it.target !in SecureDnsDomains
                }
                .forEach { rule ->
                    saveRule(rule.copy(enabled = false), deviceId)
                }
        }

        private suspend fun setSearchEngineDomain(
            target: String,
            allowed: Boolean,
            deviceId: String,
        ) {
            setRulesForDomain(
                target = target,
                action = RuleAction.Allow,
                enabled = allowed,
                priority = AllowDomainPriority,
                deviceId = deviceId,
            )
            setRulesForDomain(
                target = target,
                action = RuleAction.Block,
                enabled = !allowed,
                priority = SearchEngineBlockPriority,
                deviceId = deviceId,
            )
        }

        private suspend fun setSecureDnsDomain(
            target: String,
            blocked: Boolean,
            deviceId: String,
        ) {
            setRulesForDomain(
                target = target,
                action = RuleAction.Allow,
                enabled = false,
                priority = AllowDomainPriority,
                deviceId = deviceId,
            )
            setRulesForDomain(
                target = target,
                action = RuleAction.Block,
                enabled = blocked,
                priority = SearchEngineBlockPriority,
                deviceId = deviceId,
            )
        }

        private suspend fun setAllowedDomain(
            target: String,
            enabled: Boolean,
            deviceId: String,
        ) {
            setRulesForDomain(
                target = target,
                action = RuleAction.Allow,
                enabled = enabled,
                priority = AllowDomainPriority,
                deviceId = deviceId,
            )
        }

        private suspend fun setRulesForDomain(
            target: String,
            action: RuleAction,
            enabled: Boolean,
            priority: Int,
            deviceId: String,
        ) {
            val existingRules =
                uiState.value.rules.filter {
                    it.scope == RuleScope.Domain &&
                        it.target == target &&
                        it.action == action
                }
            val canonicalRule =
                existingRules
                    .sortedWith(
                        compareByDescending<PolicyRule> { it.level.specificity }
                            .thenByDescending { it.priority }
                            .thenBy { it.id },
                    )
                    .firstOrNull()
                    ?: PolicyRule(
                        id = UUID.randomUUID().toString(),
                        level = PolicyLevel.Account,
                        scope = RuleScope.Domain,
                        target = target,
                        action = action,
                        priority = priority,
                        enabled = enabled,
                    )
            saveRule(canonicalRule.copy(enabled = enabled, priority = priority), deviceId)
            existingRules
                .filterNot { it.id == canonicalRule.id }
                .forEach { duplicate ->
                    saveRule(duplicate.copy(enabled = false, priority = priority), deviceId)
                }
            if (existingRules.size > 1) {
                Log.i(
                    LogTag,
                    "internetSave consolidated duplicate domain rules target=$target action=$action kept=${canonicalRule.id} disabled=${existingRules.size - 1}",
                )
            }
        }

        private fun selectedDeviceIdForRules(): String? {
            val selectedDeviceId = uiState.value.selectedDeviceId
            if (selectedDeviceId == null) {
                form.update { it.copy(message = "Seleccioná un dispositivo.") }
            }
            return selectedDeviceId
        }

        private suspend fun syncNow(): Boolean = syncNowWithResult().success

        private suspend fun syncNowWithResult(): SyncResult =
            withContext(Dispatchers.IO) { syncEngine.syncCoreDataFull() }

        private fun beginInternetSave(
            action: String,
            deviceId: String,
            previousState: String,
            requestedState: String,
        ): Long? {
            if (form.value.internetSaving) {
                Log.i(
                    LogTag,
                    "internetSave ignored action=$action deviceId=$deviceId previousState=$previousState requestedState=$requestedState reason=already-saving requestId=$internetSaveRequestId",
                )
                form.update { it.copy(message = "Guardando cambio anterior...") }
                return null
            }
            val requestId = ++internetSaveRequestId
            Log.i(
                LogTag,
                "internetSave start requestId=$requestId action=$action deviceId=$deviceId previousState=$previousState requestedState=$requestedState",
            )
            recordAdminDiagnostic(
                action = action,
                deviceId = deviceId,
                requestId = requestId,
                previousState = previousState,
                requestedState = requestedState,
                result = "start",
                reason = "tap",
            )
            form.update { it.copy(internetSaving = true, message = "Guardando...") }
            return requestId
        }

        private fun isCurrentInternetSave(requestId: Long): Boolean = requestId == internetSaveRequestId

        private fun logSearchEngineSwitch(
            stage: String,
            allowed: Boolean,
            deviceId: String,
            pending: Boolean?,
            roomAllowed: Boolean,
        ) {
            if (BuildConfig.DEBUG) {
                Log.d(
                    LogTag,
                    "searchEngineSwitch stage=$stage allowed=$allowed deviceId=$deviceId pending=$pending roomAllowed=$roomAllowed",
                )
            }
        }

        private fun logInternetSwitch(
            stage: String,
            touched: Boolean,
            pending: Boolean?,
            room: Boolean,
            ui: Boolean,
        ) {
            if (BuildConfig.DEBUG) {
                Log.d(
                    LogTag,
                    "internetSwitch stage=$stage touched=$touched pending=$pending room=$room uiFinal=$ui",
                )
            }
        }

        private fun recordAdminDiagnostic(
            action: String,
            deviceId: String,
            requestId: Long,
            previousState: String,
            requestedState: String,
            result: String,
            reason: String,
        ) {
            if (BuildConfig.FLAVOR != "dev") return
            viewModelScope.launch(Dispatchers.IO) {
                telemetryRepository.record(
                    TechnicalDiagnostic(
                        id = UUID.randomUUID().toString(),
                        type = "admin-rules",
                        message =
                            "layer=admin action=$action requestId=$requestId deviceId=${deviceId.safeDeviceId()} " +
                                "previousState=${previousState.take(MaxDiagnosticValueLength)} " +
                                "requestedState=${requestedState.take(MaxDiagnosticValueLength)} " +
                                "result=$result reason=${reason.take(MaxDiagnosticValueLength)}",
                        occurredAtEpochMillis = System.currentTimeMillis(),
                    ),
                )
            }
        }

        private data class RulesPolicyState(
            val rules: List<PolicyRule>,
            val limits: List<DailyLimit>,
            val appGroups: List<AppGroup>,
            val grants: List<ExtraTimeGrant>,
            val nowEpochMillis: Long,
        )

        private fun String.safeDeviceId(): String = take(8)

        private fun String.withPairingName(userName: String): String {
            val safeName =
                userName
                    .trim()
                    .replace(Regex("\\s+"), "-")
                    .trim('-')
                    .take(32)
            return "${safeName.ifBlank { "usuario" }}-${trim()}"
        }

        private companion object {
            const val NoonMinuteOfDay = 720
            const val UserPairingTokenTtlMinutes = 180
        }
    }
