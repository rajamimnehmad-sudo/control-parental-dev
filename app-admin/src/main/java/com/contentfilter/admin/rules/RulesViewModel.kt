package com.contentfilter.admin.rules

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.admin.BuildConfig
import com.contentfilter.core.domain.model.AppGroup
import com.contentfilter.core.domain.model.DailyLimit
import com.contentfilter.core.domain.model.ExtraTimeGrant
import com.contentfilter.core.domain.model.InstalledApp
import com.contentfilter.core.domain.model.PolicyLevel
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicyTargetType
import com.contentfilter.core.domain.model.ProtectionAuthorizationScope
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.model.TechnicalDiagnostic
import com.contentfilter.core.domain.model.WebProtectionSemantics
import com.contentfilter.core.domain.model.dagEnabled
import com.contentfilter.core.domain.repository.AppGroupRepository
import com.contentfilter.core.domain.repository.DeviceRepository
import com.contentfilter.core.domain.repository.ExtraTimeGrantRepository
import com.contentfilter.core.domain.repository.InstalledAppRepository
import com.contentfilter.core.domain.repository.PolicyRepository
import com.contentfilter.core.domain.repository.ProtectionControlRepository
import com.contentfilter.core.domain.repository.SystemStatusRepository
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
import com.contentfilter.core.security.RecoveryCodeHasher
import com.contentfilter.core.sync.SyncScheduler
import com.contentfilter.core.sync.engine.PolicyApplicationState
import com.contentfilter.core.sync.engine.SyncEngine
import com.contentfilter.core.sync.engine.SyncResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
        private val policyRepository: PolicyRepository,
        grantRepository: ExtraTimeGrantRepository,
        private val remoteInstalledAppRepository: RemoteInstalledAppRepository,
        private val installedAppRepository: InstalledAppRepository,
        private val activationClient: SupabaseActivationClient,
        private val syncScheduler: SyncScheduler,
        private val syncEngine: SyncEngine,
        private val telemetryRepository: TelemetryRepository,
        private val protectionControlRepository: ProtectionControlRepository,
        private val recoveryCodeHasher: RecoveryCodeHasher,
        systemStatusRepository: SystemStatusRepository,
    ) : ViewModel() {
        private val form =
            MutableStateFlow(
                RulesUiState(),
            )
        private val installedApps =
            installedAppRepository.observeInstalledApps().stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList(),
            )
        private var internetSaveRequestId = 0L
        private var webPreferenceSaveRequestId = 0L
        private var latestWebPreferenceMessageRequestId = 0L
        private val latestWebPreferenceRequestIds = mutableMapOf<WebPolicyPreference, Long>()
        private val webPreferenceMutationMutex = Mutex()
        private val selectedPolicyDeviceId = form.map { it.selectedDeviceId }.distinctUntilChanged()
        private val policyRules = selectedPolicyDeviceId.flatMapLatest { observePolicyRules(it) }
        private val dailyLimits = selectedPolicyDeviceId.flatMapLatest { observeDailyLimits(it) }
        private val appGroups = selectedPolicyDeviceId.flatMapLatest { appGroupRepository.observeGroups(it) }
        private val extraTimeGrants = grantRepository.observeGrants()
        private val devices =
            observeDevices().stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList(),
            )
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
                devices,
                installedApps,
                form,
                systemStatusRepository.observeHealth(),
            ) { policy, devices, apps, formState, health ->
                val userDevices = devices.toUserDevices(apps)
                val selectedDeviceId = userDevices.selectedDeviceId(formState.selectedDeviceId)
                val savedInternetBlocked = policy.rules.internetBlocked()
                if (BuildConfig.DEBUG) {
                    Log.d(
                        LogTag,
                        "webNavigation admin state selectedDeviceId=$selectedDeviceId " +
                            "webNavigationBlocked=$savedInternetBlocked " +
                            "externalSearchResultsAllowed=${policy.rules.externalSearchResultsAllowedForWeb()} " +
                            "safeSearch=${policy.rules.safeSearchEnabledForWeb()} " +
                            "pendingNavigation=${formState.pendingInternetBlocked} " +
                            "pendingExternalResults=${formState.pendingExternalSearchResultsAllowed} " +
                            "pendingSafeSearch=${formState.pendingSafeSearchEnabled} " +
                            "policyRevision=${policy.rules.webPolicyRevision()}",
                    )
                }
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
                    externalSearchResultsAllowed =
                        formState.pendingExternalSearchResultsAllowed
                            ?: policy.rules.externalSearchResultsAllowedForWeb(),
                    safeSearchEnabled =
                        formState.pendingSafeSearchEnabled ?: policy.rules.safeSearchEnabledForWeb(),
                    dagEnabled = health.dagEntitled && (formState.pendingDagEnabled ?: policy.rules.dagEnabled()),
                    dagEntitled = health.dagEntitled,
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

        init {
            viewModelScope.launch(Dispatchers.IO) {
                devices.collect { values -> refreshProtectionControls(values.map { it.id }) }
            }
        }

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
            form.update { it.copy(message = "Actualizando apps...") }
            refreshInstalledApps(forceFull = true)
        }

        fun refreshDevices() {
            form.update { it.copy(message = "Actualizando dispositivos...") }
            viewModelScope.launch(Dispatchers.IO) {
                val devicesResult = syncEngine.syncDevicesFull()
                refreshProtectionControls(devices.value.map { it.id })
                refreshInstalledApps(forceFull = false)
                form.update {
                    it.copy(message = devicesResult.message.takeIf { message -> message.isNotBlank() }.orEmpty())
                }
            }
        }

        fun setProtectionArmed(
            deviceId: String,
            armed: Boolean,
        ) = mutateProtection(deviceId, if (armed) "Activando protección..." else "Desarmando protección...") { device ->
            protectionControlRepository.setArmed(device.accountId, device.id, armed)
        }

        fun authorizeSettings(deviceId: String) =
            mutateProtection(deviceId, "Autorizando mantenimiento...") { device ->
                protectionControlRepository.authorize(
                    accountId = device.accountId,
                    deviceId = device.id,
                    scope = ProtectionAuthorizationScope.Settings,
                    durationMinutes = 10,
                )
            }

        fun authorizeRemoval(deviceId: String) =
            mutateProtection(deviceId, "Autorizando retiro...") { device ->
                protectionControlRepository.authorize(
                    accountId = device.accountId,
                    deviceId = device.id,
                    scope = ProtectionAuthorizationScope.Removal,
                    durationMinutes = 10,
                )
            }

        fun generateRecoveryCode(deviceId: String) {
            val material = recoveryCodeHasher.generate()
            val device = uiState.value.userDevices.firstOrNull { it.id == deviceId }
            if (device == null) {
                form.update { it.copy(message = "Dispositivo no disponible.") }
                return
            }
            form.update {
                it.copy(
                    protectionLoadingDeviceIds = it.protectionLoadingDeviceIds + deviceId,
                    message = "Generando código de recuperación...",
                    recoveryCode = "",
                )
            }
            viewModelScope.launch(Dispatchers.IO) {
                protectionControlRepository
                    .rotateRecovery(
                        accountId = device.accountId,
                        deviceId = device.id,
                        salt = material.salt,
                        verifier = material.verifier,
                    ).onSuccess { control ->
                        form.update {
                            it.copy(
                                protectionControls = it.protectionControls + (deviceId to control),
                                protectionLoadingDeviceIds = it.protectionLoadingDeviceIds - deviceId,
                                recoveryCode = material.code,
                                message = "Código listo. Guardalo antes de cerrar.",
                            )
                        }
                    }.onFailure {
                        form.update {
                            it.copy(
                                protectionLoadingDeviceIds = it.protectionLoadingDeviceIds - deviceId,
                                message = "No se pudo generar el código.",
                            )
                        }
                    }
            }
        }

        fun clearRecoveryCode() {
            form.update { it.copy(recoveryCode = "", message = "Código ocultado.") }
        }

        private fun mutateProtection(
            deviceId: String,
            progressMessage: String,
            action: suspend (UserDeviceUiState) -> Result<com.contentfilter.core.domain.model.DeviceProtectionControl>,
        ): kotlinx.coroutines.Job {
            val device =
                uiState.value.userDevices.firstOrNull { it.id == deviceId }
                    ?: return viewModelScope.launch { form.update { it.copy(message = "Dispositivo no disponible.") } }
            form.update {
                it.copy(
                    protectionLoadingDeviceIds = it.protectionLoadingDeviceIds + deviceId,
                    message = progressMessage,
                    recoveryCode = "",
                )
            }
            return viewModelScope.launch(Dispatchers.IO) {
                action(device)
                    .onSuccess { control ->
                        form.update {
                            it.copy(
                                protectionControls = it.protectionControls + (deviceId to control),
                                protectionLoadingDeviceIds = it.protectionLoadingDeviceIds - deviceId,
                                message = "Protección actualizada. El teléfono la aplicará al sincronizar.",
                            )
                        }
                    }.onFailure {
                        form.update {
                            it.copy(
                                protectionLoadingDeviceIds = it.protectionLoadingDeviceIds - deviceId,
                                message = "No se pudo actualizar la protección.",
                            )
                        }
                    }
            }
        }

        private suspend fun refreshProtectionControls(deviceIds: List<String>) {
            val refreshed =
                deviceIds.distinct().mapNotNull { deviceId ->
                    protectionControlRepository.get(deviceId).getOrNull()?.let { deviceId to it }
                }.toMap()
            if (refreshed.isNotEmpty()) {
                form.update { it.copy(protectionControls = it.protectionControls + refreshed) }
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

        fun onDeviceSelected(
            deviceId: String,
            refreshApps: Boolean = true,
        ) {
            form.update {
                it.copy(
                    selectedDeviceId = deviceId,
                    appSearchQuery = "",
                    editingGroupId = null,
                    groupName = "",
                    groupMinutes = "",
                    groupSelectedPackages = emptySet(),
                    message = if (refreshApps) "Cargando apps..." else "",
                )
            }
            if (refreshApps) refreshInstalledApps()
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
                    message = "Borrando usuario...",
                )
            }
            viewModelScope.launch(Dispatchers.IO) {
                when (activationClient.archiveProtectedUser(deviceId)) {
                    is RemoteResult.Success -> {
                        deviceRepository.deleteDevice(deviceId)
                        installedAppRepository.deleteForDevice(deviceId)
                        val syncResult = syncEngine.syncDevicesFull()
                        Log.i(
                            LogTag,
                            "deleteUser finished deviceId=$deviceId syncSuccess=${syncResult.success} message=${syncResult.message}",
                        )
                        form.update {
                            it.copy(
                                selectedDeviceId = it.selectedDeviceId.takeUnless { selected -> selected == deviceId },
                                pendingDeviceDeleteIds = it.pendingDeviceDeleteIds - deviceId,
                                message = "Usuario borrado.",
                            )
                        }
                    }
                    is RemoteResult.Failure -> {
                        form.update {
                            it.copy(
                                pendingDeviceDeleteIds = it.pendingDeviceDeleteIds - deviceId,
                                message = "No se pudo borrar el usuario.",
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
            setWebOption(
                preference = WebPolicyPreference.NavigationBlocked,
                action = "internet-mode",
                requestedState = blocked,
                previousState = uiState.value.internetBlocked,
                successMessage =
                    if (blocked) {
                        "Navegación web bloqueada."
                    } else {
                        "Navegación web permitida."
                    },
            )
        }

        fun setOnlyResultsEnabled(enabled: Boolean) {
            val externalSearchResultsAllowed =
                WebProtectionSemantics.externalSearchResultsAllowed(enabled)
            setWebOption(
                preference = WebPolicyPreference.ExternalSearchResultsAllowed,
                action = "only-results",
                requestedState = externalSearchResultsAllowed,
                previousState = uiState.value.externalSearchResultsAllowed,
                successMessage =
                    if (enabled) {
                        "Solo resultados activado."
                    } else {
                        "Navegación completa desde buscadores activada."
                    },
            )
        }

        fun setSafeSearchEnabled(enabled: Boolean) {
            setWebOption(
                preference = WebPolicyPreference.SafeSearchEnabled,
                action = "safe-search",
                requestedState = enabled,
                previousState = uiState.value.safeSearchEnabled,
                successMessage =
                    if (enabled) {
                        "SafeSearch activado."
                    } else {
                        "SafeSearch desactivado."
                    },
            )
        }

        fun setDagEnabled(enabled: Boolean) {
            if (!uiState.value.dagEntitled) {
                form.update { it.copy(message = "DAG no está incluido en la licencia de esta comunidad.") }
                return
            }
            setWebOption(
                preference = WebPolicyPreference.DagEnabled,
                action = "dag-enabled",
                requestedState = enabled,
                previousState = uiState.value.dagEnabled,
                successMessage = if (enabled) "DAG abierto." else "DAG cerrado.",
            )
        }

        private fun setWebOption(
            preference: WebPolicyPreference,
            action: String,
            requestedState: Boolean,
            previousState: Boolean,
            successMessage: String,
        ) {
            val targetDeviceId = uiState.value.selectedDeviceId
            if (targetDeviceId == null) {
                form.update { it.copy(message = "Seleccioná un dispositivo.") }
                return
            }
            val requestId =
                beginWebPreferenceSave(
                    preference = preference,
                    action = action,
                    deviceId = targetDeviceId,
                    previousState = previousState,
                    requestedState = requestedState,
                )
            val traceRequestId = "$action-$requestId"
            form.update {
                it.withPendingWebPreference(preference, requestedState).copy(message = "Guardando...")
            }
            viewModelScope.launch {
                val saved =
                    webPreferenceMutationMutex.withLock {
                        runCatching {
                            refreshRemotePolicyBeforeWebMutation(targetDeviceId, traceRequestId)
                            val before = policyRepository.getActivePolicy(targetDeviceId)
                            check(before.deviceId == null || before.deviceId == targetDeviceId) {
                                "Room devolvió una policy de otro dispositivo."
                            }
                            val desired =
                                before.rules
                                    .webPolicyPreferences()
                                    .withPreference(preference, requestedState)
                            val changes =
                                before.rules.webPolicyPreferenceChanges(
                                    preference = preference,
                                    enabled = requestedState,
                                    deviceId = targetDeviceId,
                                )
                            val receipt = saveRule.saveAll(changes, targetDeviceId, traceRequestId)
                            val confirmed =
                                withTimeoutOrNull(RoomConfirmTimeoutMillis) {
                                    policyRepository.observeActivePolicy(targetDeviceId).first { snapshot ->
                                        snapshot.deviceId == targetDeviceId &&
                                            snapshot.version >= receipt.revision &&
                                            snapshot.rules.webPolicyPreferences() == desired &&
                                            snapshot.rules.activeWebAuxiliaryBlockCount() == 0
                                    }
                                } ?: error("Room no confirmó $action.")
                            if (BuildConfig.FLAVOR == "dev") {
                                Log.i(
                                    LogTag,
                                    "web option room confirmed requestId=$traceRequestId " +
                                        "deviceId=${targetDeviceId.safeDeviceId()} policyId=${receipt.policyId.take(8)} " +
                                        "revision=${receipt.revision} changed=${preference.name} " +
                                        "webBlocked=${confirmed.rules.internetBlocked()} " +
                                        "externalSearchResultsAllowed=" +
                                        "${confirmed.rules.externalSearchResultsAllowedForWeb()} " +
                                        "safeSearchEnabled=${confirmed.rules.safeSearchEnabledForWeb()} " +
                                        "activeDomainBlocks=${confirmed.rules.activeDomainBlockCount()} " +
                                        "activeAuxiliaryBlocks=${confirmed.rules.activeWebAuxiliaryBlockCount()} " +
                                        "changes=${changes.size}",
                                )
                            }
                            receipt
                        }
                    }
                if (saved.isFailure) {
                    recordAdminDiagnostic(
                        action = action,
                        deviceId = targetDeviceId,
                        requestId = requestId,
                        previousState = previousState.toString(),
                        requestedState = requestedState.toString(),
                        result = "save-failed",
                        reason = saved.exceptionOrNull()?.javaClass?.simpleName ?: "unknown",
                    )
                    if (isCurrentWebPreferenceSave(preference, requestId)) {
                        form.update { state ->
                            val cleared = state.clearPendingWebPreference(preference)
                            if (isLatestWebPreferenceMessage(requestId)) {
                                cleared.copy(message = "No se pudo guardar el cambio web.")
                            } else {
                                cleared
                            }
                        }
                    }
                    return@launch
                }
                recordAdminDiagnostic(
                    action = action,
                    deviceId = targetDeviceId,
                    requestId = requestId,
                    previousState = previousState.toString(),
                    requestedState = requestedState.toString(),
                    result = "local-confirmed",
                    reason = preference.name,
                )
                if (isCurrentWebPreferenceSave(preference, requestId)) {
                    form.update { state ->
                        val cleared = state.clearPendingWebPreference(preference)
                        if (isLatestWebPreferenceMessage(requestId)) {
                            cleared.copy(message = "Guardado en este dispositivo. Sincronizando...")
                        } else {
                            cleared
                        }
                    }
                }
                syncScheduler.requestSync()
                val receipt = saved.getOrThrow()
                val syncResult = withContext(Dispatchers.IO) { syncEngine.syncPolicyChanges(receipt) }
                if (BuildConfig.FLAVOR == "dev") {
                    Log.i(
                        LogTag,
                        "web option sync finished requestId=$traceRequestId " +
                            "deviceId=${targetDeviceId.safeDeviceId()} revision=${receipt.revision} " +
                            "serverConfirmed=${syncResult.serverConfirmed} pending=${syncResult.pendingOperationIds.size} " +
                            "error=${syncResult.error}",
                    )
                }
                if (!isLatestWebPreferenceMessage(requestId)) return@launch
                if (!syncResult.serverConfirmed) {
                    form.update {
                        it.copy(message = "Guardado en este dispositivo. Pendiente por conexión.")
                    }
                    return@launch
                }
                form.update {
                    it.copy(message = "Sincronizado con el servidor. Esperando dispositivo...")
                }
                val application =
                    withContext(Dispatchers.IO) {
                        syncEngine.waitForPolicyApplied(receipt, PolicyApplicationWaitMillis)
                    }
                if (!isLatestWebPreferenceMessage(requestId)) return@launch
                form.update {
                    it.copy(
                        message =
                            when (application.state) {
                                PolicyApplicationState.Applied -> successMessage
                                PolicyApplicationState.Pending ->
                                    "Sincronizado con el servidor. Dispositivo sin confirmar."
                                PolicyApplicationState.Offline ->
                                    "Sincronizado con el servidor. Dispositivo sin conexión."
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
                        Log.i(
                            LogTag,
                            "internetSave stale response ignored requestId=$requestId action=toggle-domain-rule",
                        )
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
            Log.i(
                LogTag,
                "appControl requested action=setAllowed deviceId=${targetDeviceId.safeDeviceId()} " +
                    "package=$packageName allowed=$allowed",
            )
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
                val requestId = "app-${UUID.randomUUID()}"
                val localSave =
                    runCatching {
                        val receipts =
                            appLimits.map {
                                deleteDailyLimitUseCase(it, targetDeviceId, requestId)
                            }.toMutableList()
                        val ruleChanges =
                            if (allowed) {
                                blockRules.filter { it.enabled }.map { it.copy(enabled = false) } +
                                    authorizationRules.filter { it.enabled }.map { it.copy(enabled = false) }
                            } else {
                                allowRules.filter { it.enabled }.map { it.copy(enabled = false) } +
                                    authorizationRules.filter { it.enabled }.map { it.copy(enabled = false) } +
                                    listOf(
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
                                    )
                            }
                        if (ruleChanges.isNotEmpty()) {
                            receipts += saveRule.saveAll(ruleChanges, targetDeviceId, requestId)
                        }
                        receipts.maxByOrNull { it.revision }
                    }
                if (localSave.isFailure) {
                    form.update {
                        it.copy(
                            pendingAppAllowed = it.pendingAppAllowed - packageName,
                            message = "No se pudo guardar el cambio de la app.",
                        )
                    }
                    return@launch
                }
                form.update {
                    it.copy(
                        pendingAppAllowed = it.pendingAppAllowed - packageName,
                        message = "Guardado en este dispositivo. Sincronizando...",
                    )
                }
                val receipt = localSave.getOrNull()
                if (receipt == null) {
                    form.update { it.copy(message = if (allowed) "App permitida." else "App bloqueada.") }
                    return@launch
                }
                syncScheduler.requestSync()
                val syncResult =
                    withContext(Dispatchers.IO) {
                        syncEngine.syncPolicyChanges(receipt)
                    }
                form.update {
                    it.copy(
                        message =
                            if (syncResult.serverConfirmed) {
                                "Sincronizado con el servidor. Esperando dispositivo..."
                            } else {
                                "Guardado en este dispositivo. Pendiente por conexión."
                            },
                    )
                }
                if (syncResult.serverConfirmed) {
                    val application =
                        withContext(Dispatchers.IO) {
                            syncEngine.waitForPolicyApplied(receipt, PolicyApplicationWaitMillis)
                        }
                    form.update {
                        it.copy(
                            message =
                                when (application.state) {
                                    PolicyApplicationState.Applied -> "Aplicado en el dispositivo Usuario."
                                    PolicyApplicationState.Pending ->
                                        "Sincronizado con el servidor. Dispositivo sin confirmar."
                                    PolicyApplicationState.Offline ->
                                        "Sincronizado con el servidor. Dispositivo sin conexión."
                                },
                        )
                    }
                }
                val synced = syncResult.serverConfirmed
                Log.i(
                    LogTag,
                    "appControl saved requestId=$requestId action=setAllowed " +
                        "deviceId=${targetDeviceId.safeDeviceId()} allowed=$allowed syncSuccess=$synced",
                )
            }
        }

        fun saveAppControlLimit(
            packageName: String,
            rawMinutes: String,
        ) {
            val targetDeviceId = selectedDeviceIdForRules() ?: return
            val minutes = rawMinutes.filter(Char::isDigit).toIntOrNull()
            Log.i(
                LogTag,
                "appControl requested action=saveLimit deviceId=${targetDeviceId.safeDeviceId()} " +
                    "package=$packageName minutes=$minutes",
            )
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
                    return@launch
                }
                val requestId = "limit-${UUID.randomUUID()}"
                form.update {
                    it.copy(
                        pendingAppAllowed = it.pendingAppAllowed + (packageName to true),
                        message = "Guardando...",
                    )
                }
                val localSave =
                    runCatching {
                        val ruleChanges =
                            matchingRules
                                .filter {
                                    it.enabled &&
                                        (it.action == RuleAction.Block || it.action == RuleAction.RequestAuthorization)
                                }.map { it.copy(enabled = false) }
                        if (ruleChanges.isNotEmpty()) {
                            saveRule.saveAll(ruleChanges, targetDeviceId, requestId)
                        }
                        saveDailyLimit(
                            DailyLimit(
                                id = existing?.id ?: UUID.randomUUID().toString(),
                                targetType = PolicyTargetType.App,
                                target = packageName,
                                limitMinutes = minutes,
                                enabled = true,
                            ),
                            targetDeviceId,
                            requestId,
                        )
                    }
                form.update {
                    it.copy(
                        pendingAppAllowed = it.pendingAppAllowed - packageName,
                        message =
                            if (localSave.isSuccess) {
                                "Guardado en este dispositivo. Sincronizando..."
                            } else {
                                "No se pudo guardar el límite."
                            },
                    )
                }
                val receipt = localSave.getOrNull() ?: return@launch
                syncScheduler.requestSync()
                val syncResult =
                    withContext(Dispatchers.IO) {
                        syncEngine.syncPolicyChanges(receipt)
                    }
                form.update {
                    it.copy(
                        message =
                            if (syncResult.serverConfirmed) {
                                "Sincronizado con el servidor. Esperando dispositivo..."
                            } else {
                                "Guardado en este dispositivo. Pendiente por conexión."
                            },
                    )
                }
                Log.i(
                    LogTag,
                    "appControl saved requestId=$requestId action=saveLimit " +
                        "deviceId=${targetDeviceId.safeDeviceId()} minutes=$minutes " +
                        "syncSuccess=${syncResult.serverConfirmed}",
                )
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
                    form.update {
                        it.copy(
                            message =
                                "Una app elegida ya está en ${packageInOtherGroup.name}. " +
                                    "Sacala de ese grupo o editá ese grupo.",
                        )
                    }
                    return
                }
            }
            viewModelScope.launch {
                val requestId = "group-${UUID.randomUUID()}"
                form.update {
                    it.copy(
                        groupSaving = true,
                        message = if (editingGroupId == null) "Guardando grupo..." else "Actualizando grupo...",
                    )
                }
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
                        val receipt = appGroupRepository.replaceGroupApps(group, packages, requestId)
                        syncScheduler.requestSync()
                        withContext(Dispatchers.IO) { syncEngine.syncPolicyChanges(receipt) }
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
                                if (saved.getOrNull()?.serverConfirmed == true) {
                                    "Sincronizado con el servidor. Esperando dispositivo..."
                                } else {
                                    "Guardado en este dispositivo. Pendiente por conexión."
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
                val requestId = "group-delete-${UUID.randomUUID()}"
                val saved =
                    runCatching {
                        val receipt =
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
                                requestId,
                            )
                        syncScheduler.requestSync()
                        withContext(Dispatchers.IO) { syncEngine.syncPolicyChanges(receipt) }
                    }
                form.update {
                    it.copy(
                        pendingAppGroupDeleteIds = it.pendingAppGroupDeleteIds - groupId,
                        editingGroupId = it.editingGroupId.takeUnless { editingId -> editingId == groupId },
                        groupName = if (it.editingGroupId == groupId) "" else it.groupName,
                        groupMinutes = if (it.editingGroupId == groupId) "" else it.groupMinutes,
                        groupSelectedPackages = if (it.editingGroupId == groupId) emptySet() else it.groupSelectedPackages,
                        message =
                            if (saved.getOrNull()?.serverConfirmed == true) {
                                "Grupo eliminado y sincronizado con el servidor."
                            } else if (saved.isSuccess) {
                                "Grupo eliminado localmente. Pendiente por conexión."
                            } else {
                                "No se pudo eliminar el grupo."
                            },
                    )
                }
            }
        }

        init {
            syncScheduler.requestSync()
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
                    Log.i(
                        LogTag,
                        "internetSave stale response ignored requestId=$internetRequestId action=allow-domain",
                    )
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

        private fun refreshInstalledApps(forceFull: Boolean = false) {
            viewModelScope.launch(Dispatchers.IO) {
                val startedAt = System.currentTimeMillis()
                val selectedDeviceId = form.value.selectedDeviceId ?: return@launch
                val cachedCount = installedApps.value.count { it.deviceId == selectedDeviceId }
                if (BuildConfig.DEBUG) {
                    Log.i(
                        LogTag,
                        "appsRefresh stage=cache requestId=apps-$startedAt deviceId=${selectedDeviceId.safeDeviceId()} " +
                            "count=$cachedCount durationMs=${System.currentTimeMillis() - startedAt}",
                    )
                }
                val updatedAfter =
                    if (forceFull) {
                        null
                    } else {
                        installedAppRepository.latestUpdatedAt(selectedDeviceId)?.let {
                            Instant.ofEpochMilli(it).toString()
                        }
                    }
                val remoteStartedAt = System.currentTimeMillis()
                when (
                    val result =
                        remoteInstalledAppRepository.pullInstalledApps(
                            deviceId = selectedDeviceId,
                            updatedAfterIso = updatedAfter,
                        )
                ) {
                    is RemoteResult.Success -> {
                        val remoteApps = result.value.map { it.toInstalledApp() }
                        installedAppRepository.mergeInstalledApps(remoteApps)
                        if (BuildConfig.DEBUG) {
                            Log.i(
                                LogTag,
                                "appsRefresh stage=remote-merge requestId=apps-$startedAt " +
                                    "deviceId=${selectedDeviceId.safeDeviceId()} deltaCount=${remoteApps.size} " +
                                    "forceFull=$forceFull remoteDurationMs=${System.currentTimeMillis() - remoteStartedAt} " +
                                    "totalDurationMs=${System.currentTimeMillis() - startedAt}",
                            )
                        }
                        form.update { state ->
                            state.copy(
                                message =
                                    state.message.takeUnless {
                                        it == "Cargando apps..." || it == "Actualizando apps..."
                                    }.orEmpty(),
                            )
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

        private fun RemoteInstalledAppDto.toInstalledApp(): InstalledApp =
            InstalledApp(
                id = id,
                accountId = accountId,
                deviceId = deviceId,
                appName = appName,
                packageName = packageName,
                versionName = versionName,
                isSystemApp = isSystemApp,
                iconBase64 = iconBase64,
                updatedAtEpochMillis = runCatching { Instant.parse(updatedAt).toEpochMilli() }.getOrDefault(0L),
            )

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

        private suspend fun refreshRemotePolicyBeforeWebMutation(
            deviceId: String,
            requestId: String,
        ) {
            val result =
                syncEngine.pullPolicyRevision(
                    requestId = requestId,
                    deviceId = deviceId,
                    reason = "admin-web-preflight",
                )
            if (BuildConfig.FLAVOR == "dev") {
                Log.i(
                    LogTag,
                    "web preflight requestId=$requestId deviceId=${deviceId.safeDeviceId()} " +
                        "policyId=${result.policyId?.take(8) ?: "none"} revision=${result.revision ?: 0L} " +
                        "roomApplied=${result.roomApplied} success=${result.success}",
                )
            }
        }

        private suspend fun syncNow(): Boolean = syncNowWithResult().success

        private suspend fun syncNowWithResult(): SyncResult =
            withContext(Dispatchers.IO) { syncEngine.syncCoreDataFull() }

        private fun beginWebPreferenceSave(
            preference: WebPolicyPreference,
            action: String,
            deviceId: String,
            previousState: Boolean,
            requestedState: Boolean,
        ): Long {
            val requestId = ++webPreferenceSaveRequestId
            latestWebPreferenceRequestIds[preference] = requestId
            latestWebPreferenceMessageRequestId = requestId
            Log.i(
                LogTag,
                "webPreferenceSave start requestId=$requestId action=$action " +
                    "deviceId=${deviceId.safeDeviceId()} changed=${preference.name} " +
                    "previousState=$previousState requestedState=$requestedState",
            )
            recordAdminDiagnostic(
                action = action,
                deviceId = deviceId,
                requestId = requestId,
                previousState = previousState.toString(),
                requestedState = requestedState.toString(),
                result = "start",
                reason = preference.name,
            )
            return requestId
        }

        private fun isCurrentWebPreferenceSave(
            preference: WebPolicyPreference,
            requestId: Long,
        ): Boolean = latestWebPreferenceRequestIds[preference] == requestId

        private fun isLatestWebPreferenceMessage(requestId: Long): Boolean =
            latestWebPreferenceMessageRequestId == requestId

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
            const val PolicyApplicationWaitMillis = 8_000L
        }
    }
