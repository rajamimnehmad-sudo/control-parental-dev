package com.contentfilter.admin.rules

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.admin.BuildConfig
import com.contentfilter.core.domain.model.DailyLimit
import com.contentfilter.core.domain.model.Device
import com.contentfilter.core.domain.model.PolicyLevel
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicyTargetType
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.repository.DeviceRepository
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
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
        private val remoteInstalledAppRepository: RemoteInstalledAppRepository,
        private val activationClient: SupabaseActivationClient,
        private val devMaintenanceClient: SupabaseDevMaintenanceClient,
        private val syncScheduler: SyncScheduler,
        private val syncEngine: SyncEngine,
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

        val uiState =
            combine(
                policyRules,
                dailyLimits,
                observeDevices(),
                installedApps,
                form,
            ) { rules, limits, devices, apps, formState ->
                val userDevices = devices.toUserDevices(apps)
                val selectedDeviceId = userDevices.selectedDeviceId(formState.selectedDeviceId)
                val savedInternetBlocked = rules.internetBlocked()
                formState.copy(
                    rules = rules.sortedWith(compareBy({ it.scope.name }, { it.target })),
                    limits = limits.sortedWith(compareBy({ it.targetType.name }, { it.target })),
                    userDevices = userDevices,
                    selectedDeviceId = selectedDeviceId,
                    internetBlocked = formState.pendingInternetBlocked ?: savedInternetBlocked,
                    searchEnginesAllowed =
                        if (formState.pendingInternetBlocked ?: savedInternetBlocked) {
                            formState.pendingSearchEnginesAllowed ?: rules.searchEnginesAllowed()
                        } else {
                            true
                        },
                    appControls =
                        if (selectedDeviceId == null) {
                            emptyList()
                        } else {
                            apps
                                .forSelectedUserDevice(selectedDeviceId, devices)
                                .toAppControls(rules, limits, devices, formState.pendingAppAllowed)
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

        fun refreshApps() {
            form.update { it.copy(message = "Cargando apps...") }
            refreshInstalledApps()
        }

        fun generatePairingCode() {
            viewModelScope.launch {
                form.update { it.copy(pairingLoading = true, message = "Generando código...") }
                when (val result = activationClient.createDevicePairingCode()) {
                    is RemoteResult.Success ->
                        form.update {
                            it.copy(
                                pairingCode = result.value.code,
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

        fun onDeviceSelected(deviceId: String) {
            form.update { it.copy(selectedDeviceId = deviceId, appSearchQuery = "", message = "Cargando apps...") }
            refreshInstalledApps()
        }

        fun clearDeviceSelection() {
            form.update { it.copy(selectedDeviceId = null, appSearchQuery = "", message = "") }
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
                            policyRules.first { it.searchEnginesAllowed() == allowed }
                        } ?: error("Room no confirmó buscadores.")
                    }
                if (saved.isFailure) {
                    Log.e(
                        LogTag,
                        "searchEngineSwitch save failed requestId=$requestId allowed=$allowed deviceId=$targetDeviceId reason=${saved.exceptionOrNull()?.javaClass?.simpleName}",
                        saved.exceptionOrNull(),
                    )
                    if (isCurrentInternetSave(requestId)) {
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
                syncScheduler.requestSync()
                val synced = syncNowWithResult()
                Log.i(
                    LogTag,
                    "searchEngineSwitch sync finished requestId=$requestId allowed=$allowed deviceId=$targetDeviceId success=${synced.success} message=${synced.message}",
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
                                if (allowed) "Buscadores permitidos." else "Buscadores bloqueados."
                            } else {
                                if (allowed) {
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
            val existingRules = currentState.rules.internetBlockRules()
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
            form.update {
                it.copy(
                    internetSaving = true,
                    pendingInternetBlocked = blocked,
                    pendingSearchEnginesAllowed = if (blocked) false else it.pendingSearchEnginesAllowed,
                    message = if (blocked) "Activando lista blanca..." else "Abriendo Internet...",
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
                        val rulesToSave =
                            existingRules.ifEmpty {
                                listOf(
                                    PolicyRule(
                                        id = UUID.randomUUID().toString(),
                                        level = PolicyLevel.Account,
                                        scope = RuleScope.Domain,
                                        target = DomainWildcard,
                                        action = RuleAction.Block,
                                        priority = InternetBlockPriority,
                                        enabled = blocked,
                                    ),
                                )
	                            }
                        rulesToSave.forEach { rule ->
                            saveRule(rule.copy(enabled = blocked), targetDeviceId)
                        }
                        if (blocked) {
                            clearLegacyDomainBlockRules(targetDeviceId)
                            setSearchEnginesAllowedRules(allowed = false, targetDeviceId = targetDeviceId)
                        } else {
                            clearLegacyDomainBlockRules(targetDeviceId)
                            clearSearchEngineRules(targetDeviceId)
                        }
                        withTimeoutOrNull(RoomConfirmTimeoutMillis) {
                            policyRules.first {
                                it.internetBlocked() == blocked &&
                                    if (blocked) {
                                        !it.searchEnginesAllowed()
                                    } else {
                                        it.searchEngineBlockRules().none { rule -> rule.enabled }
                                    }
                            }
                        } ?: error("Room no confirmó el nuevo estado de Internet.")
                    }
                val roomAfterSave = saved.getOrNull()?.internetBlocked() ?: previousRoom
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
                    if (synced) {
                        if (!isCurrentInternetSave(requestId)) {
                            Log.i(LogTag, "internetSave stale response ignored requestId=$requestId action=internet-mode")
                            return@launch
                        }
                        form.update {
                            it.copy(
                                internetSaving = false,
                                pendingInternetBlocked = null,
                                pendingSearchEnginesAllowed = if (blocked) null else it.pendingSearchEnginesAllowed,
                                message =
                                    if (blocked) {
                                        "Modo web: bloquear todo excepto permitidos. Buscadores bloqueados."
                                    } else {
                                        "Modo web: Internet abierto."
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
                                pendingSearchEnginesAllowed = if (blocked) null else it.pendingSearchEnginesAllowed,
                                message = "Cambio guardado localmente. Se sincronizará cuando haya conexión.",
                            )
                        }
                    }
                } else {
                    if (isCurrentInternetSave(requestId)) {
                        form.update {
                            it.copy(
                                internetSaving = false,
                                pendingInternetBlocked = null,
                                pendingSearchEnginesAllowed = if (blocked) null else it.pendingSearchEnginesAllowed,
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
            form.update {
                it.copy(
                    pendingAppAllowed = it.pendingAppAllowed + (packageName to allowed),
                    message = if (allowed) "Permitiendo app..." else "Bloqueando app...",
                )
            }
            viewModelScope.launch {
                val synced =
                    runCatching {
                        if (allowed) {
                            blockRules.filter { it.enabled }.forEach {
                                saveRule(it.copy(enabled = false), targetDeviceId)
                            }
                        } else {
                            allowRules.filter { it.enabled }.forEach {
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
                                else -> "Cambio guardado localmente. Se sincronizará cuando haya conexión."
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
            viewModelScope.launch {
                if (minutes == null || minutes <= 0) {
                    if (existing != null) {
                        saveDailyLimit(existing.copy(enabled = false), targetDeviceId)
                    }
                    form.update { it.copy(message = "Límite de app desactivado.") }
                } else {
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
                    form.update { it.copy(message = "Límite de app guardado.") }
                }
                syncScheduler.requestSync()
                syncNow()
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
                when (val result = remoteInstalledAppRepository.pullInstalledApps()) {
                    is RemoteResult.Success -> {
                        installedApps.value = result.value
                        form.update { state ->
                            state.copy(message = state.message.takeUnless { it == "Cargando apps..." }.orEmpty())
                        }
                    }
                    is RemoteResult.Failure ->
                        form.update {
                            it.copy(message = "No se pudo cargar la lista de apps: ${result.reason}")
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

        private suspend fun clearLegacyDomainBlockRules(deviceId: String) {
            uiState.value.rules
                .filter {
                    it.enabled &&
                        it.scope == RuleScope.Domain &&
                        it.action == RuleAction.Block &&
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
            val rulesToSave =
                existingRules.ifEmpty {
                    listOf(
                        PolicyRule(
                            id = UUID.randomUUID().toString(),
                            level = PolicyLevel.Account,
                            scope = RuleScope.Domain,
                            target = target,
                            action = action,
                            priority = priority,
                            enabled = enabled,
                        ),
                    )
                }
            rulesToSave.forEach { rule ->
                saveRule(rule.copy(enabled = enabled, priority = priority), deviceId)
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
    }
