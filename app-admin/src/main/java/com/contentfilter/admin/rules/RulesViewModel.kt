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
import com.contentfilter.core.domain.usecase.admin.DeletePolicyRuleUseCase
import com.contentfilter.core.domain.usecase.admin.ObserveDailyLimitsUseCase
import com.contentfilter.core.domain.usecase.admin.ObserveDevicesUseCase
import com.contentfilter.core.domain.usecase.admin.ObservePolicyRulesUseCase
import com.contentfilter.core.domain.usecase.admin.SaveDailyLimitUseCase
import com.contentfilter.core.domain.usecase.admin.SavePolicyRuleUseCase
import com.contentfilter.core.network.dto.RemoteInstalledAppDto
import com.contentfilter.core.network.remote.RemoteInstalledAppRepository
import com.contentfilter.core.network.remote.RemoteResult
import com.contentfilter.core.sync.SyncScheduler
import com.contentfilter.core.sync.engine.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
class RulesViewModel
    @Inject
    constructor(
        observePolicyRules: ObservePolicyRulesUseCase,
        observeDailyLimits: ObserveDailyLimitsUseCase,
        observeDevices: ObserveDevicesUseCase,
        private val saveRule: SavePolicyRuleUseCase,
        private val deleteRule: DeletePolicyRuleUseCase,
        private val saveDailyLimit: SaveDailyLimitUseCase,
        private val remoteInstalledAppRepository: RemoteInstalledAppRepository,
        private val syncScheduler: SyncScheduler,
        private val syncEngine: SyncEngine,
    ) : ViewModel() {
        private val form =
            MutableStateFlow(
                RulesUiState(),
            )
        private val installedApps = MutableStateFlow<List<RemoteInstalledAppDto>>(emptyList())
        private val policyRules = observePolicyRules()

        val uiState =
            combine(
                policyRules,
                observeDailyLimits(),
                observeDevices(),
                installedApps,
                form,
            ) { rules, limits, devices, apps, formState ->
                val userDevices = devices.toUserDevices(apps)
                val selectedDeviceId =
                    formState.selectedDeviceId?.takeIf { selected ->
                        userDevices.any { it.id == selected }
                    }
                val savedInternetBlocked = rules.internetBlocked()
                formState.copy(
                    rules = rules.sortedWith(compareBy({ it.scope.name }, { it.target })),
                    limits = limits.sortedWith(compareBy({ it.targetType.name }, { it.target })),
                    userDevices = userDevices,
                    selectedDeviceId = selectedDeviceId,
                    internetBlocked = formState.pendingInternetBlocked ?: savedInternetBlocked,
                    googleSearchAllowed =
                        GoogleSearchDomains.all { domain ->
                            rules.any {
                                it.enabled &&
                                    it.scope == RuleScope.Domain &&
                                    it.target == domain &&
                                    it.action == RuleAction.Allow
                            }
                        },
                    appControls =
                        if (selectedDeviceId == null) {
                            emptyList()
                        } else {
                            apps
                                .filter { it.deviceId == selectedDeviceId }
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

        fun onDomainChanged(value: String) {
            form.update { it.copy(domain = value, message = "") }
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

        fun onDeviceSelected(deviceId: String) {
            form.update { it.copy(selectedDeviceId = deviceId, appSearchQuery = "", message = "Cargando apps...") }
            refreshInstalledApps()
        }

        fun clearDeviceSelection() {
            form.update { it.copy(selectedDeviceId = null, appSearchQuery = "", message = "") }
        }

        fun createBlockedAppRule() = createRule(RuleScope.App, RuleAction.Block)

        fun createBlockedDomainRule() = createRule(RuleScope.Domain, RuleAction.Block)

        fun createAllowedDomainRule() = createRule(RuleScope.Domain, RuleAction.Allow)

        fun createAppLimit() = createLimit(PolicyTargetType.App)

        fun createDomainLimit() = createLimit(PolicyTargetType.Domain)

        fun setGoogleSearchAllowed(allowed: Boolean) {
            viewModelScope.launch {
                GoogleSearchDomains.forEach { domain ->
                    setAllowedDomain(domain, allowed)
                }
                syncScheduler.requestSync()
                syncNow()
                form.update {
                    it.copy(
                        message = if (allowed) "Google permitido para buscar." else "Google vuelve a estar bloqueado.",
                    )
                }
            }
        }

        fun saveAllowedDomainLimit() {
            val target = normalizeLimitTarget(PolicyTargetType.Domain, form.value.allowDomain)
            val minutes = form.value.allowDomainMinutes.toIntOrNull()
            if (target == null || minutes == null || minutes <= 0) {
                form.update { it.copy(message = "Ingresá sitio permitido y minutos válidos.") }
                return
            }
            viewModelScope.launch {
                saveAllowedDomain(target)
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
                )
                syncScheduler.requestSync()
                syncNow()
                form.update {
                    it.copy(
                        allowDomain = "",
                        allowDomainMinutes = "",
                        message = "Sitio permitido con límite guardado.",
                    )
                }
            }
        }

        fun setInternetBlocked(blocked: Boolean) {
            val currentState = uiState.value
            val existing =
                currentState.rules.firstOrNull {
                    it.scope == RuleScope.Domain &&
                        it.target == DomainWildcard &&
                        it.action == RuleAction.Block
                }
            val previousRoom = currentState.rules.internetBlocked()
            logInternetSwitch(
                stage = "tap",
                touched = blocked,
                pending = form.value.pendingInternetBlocked,
                room = previousRoom,
                ui = currentState.internetBlocked,
            )
            form.update {
                it.copy(
                    pendingInternetBlocked = blocked,
                    message = if (blocked) "Activando lista blanca..." else "Activando lista negra...",
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
                        saveRule(
                            existing?.copy(enabled = blocked)
                                ?: PolicyRule(
                                    id = UUID.randomUUID().toString(),
                                    level = PolicyLevel.Account,
                                    scope = RuleScope.Domain,
                                    target = DomainWildcard,
                                    action = RuleAction.Block,
                                    priority = InternetBlockPriority,
                                    enabled = blocked,
                                ),
                        )
                        withTimeoutOrNull(RoomConfirmTimeoutMillis) {
                            policyRules.first { it.internetBlocked() == blocked }
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
                    syncNow()
                    form.update {
                        it.copy(
                            pendingInternetBlocked = null,
                            message =
                                if (blocked) {
                                    "Modo web: bloquear todo excepto permitidos."
                                } else {
                                    "Modo web: permitir todo excepto bloqueados."
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
                    form.update {
                        it.copy(
                            pendingInternetBlocked = null,
                            message = "No se pudo cambiar el modo web. Intentá otra vez.",
                        )
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
            viewModelScope.launch {
                saveRule(rule.copy(enabled = !rule.enabled))
                syncScheduler.requestSync()
                syncNow()
            }
        }

        fun delete(rule: PolicyRule) {
            viewModelScope.launch {
                deleteRule(rule)
                syncScheduler.requestSync()
                syncNow()
                form.update { it.copy(message = "Regla eliminada.") }
            }
        }

        fun setAppAllowed(
            packageName: String,
            allowed: Boolean,
        ) {
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
                                saveRule(it.copy(enabled = false))
                            }
                        } else {
                            allowRules.filter { it.enabled }.forEach {
                                saveRule(it.copy(enabled = false))
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
            val minutes = rawMinutes.filter(Char::isDigit).toIntOrNull()
            val existing =
                uiState.value.limits.firstOrNull {
                    it.targetType == PolicyTargetType.App && it.target == packageName
                }
            viewModelScope.launch {
                if (minutes == null || minutes <= 0) {
                    if (existing != null) {
                        saveDailyLimit(existing.copy(enabled = false))
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
                    )
                    form.update { it.copy(message = "Límite de app guardado.") }
                }
                syncScheduler.requestSync()
                syncNow()
            }
        }

        init {
            syncScheduler.requestSync()
            syncNow()
            refreshInstalledApps()
        }

        private fun createRule(
            scope: RuleScope,
            action: RuleAction,
        ) {
            val state = form.value
            val rawTarget =
                when (scope) {
                    RuleScope.App -> state.appPackageName
                    RuleScope.Domain -> if (action == RuleAction.Allow) state.allowDomain else state.domain
                    else -> ""
                }
            val target = normalizeTarget(scope, rawTarget)
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
            viewModelScope.launch {
                if (scope == RuleScope.Domain && action == RuleAction.Allow) {
                    saveAllowedDomain(target)
                } else if (scope == RuleScope.Domain && action == RuleAction.Block) {
                    target.expandBlockedDomainFamily().forEach { domain ->
                        saveRule(
                            PolicyRule(
                                id = UUID.randomUUID().toString(),
                                level = PolicyLevel.Account,
                                scope = RuleScope.Domain,
                                target = domain,
                                action = action,
                                priority = BlockDomainPriority,
                                enabled = true,
                            ),
                        )
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
                    )
                }
                syncScheduler.requestSync()
                syncNow()
                form.update {
                    when (scope) {
                        RuleScope.App -> it.copy(appPackageName = "", message = "App bloqueada.")
                        RuleScope.Domain ->
                            if (action == RuleAction.Allow) {
                                it.copy(allowDomain = "", message = "Sitio permitido.")
                            } else {
                                it.copy(domain = "", message = "Dominio/familia bloqueada.")
                            }
                        else -> it.copy(message = "Regla guardada.")
                    }
                }
            }
        }

        private fun createLimit(targetType: PolicyTargetType) {
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

        private suspend fun saveAllowedDomain(target: String) {
            setAllowedDomain(target, true)
        }

        private suspend fun setAllowedDomain(
            target: String,
            enabled: Boolean,
        ) {
            val existing =
                uiState.value.rules.firstOrNull {
                    it.scope == RuleScope.Domain &&
                        it.target == target &&
                        it.action == RuleAction.Allow
                }
            saveRule(
                existing?.copy(enabled = enabled, priority = AllowDomainPriority)
                    ?: PolicyRule(
                        id = UUID.randomUUID().toString(),
                        level = PolicyLevel.Account,
                        scope = RuleScope.Domain,
                        target = target,
                        action = RuleAction.Allow,
                        priority = AllowDomainPriority,
                        enabled = enabled,
                    ),
            )
        }

        private fun syncNow() {
            viewModelScope.launch(Dispatchers.IO) {
                syncEngine.syncCoreDataFull()
            }
        }

        private fun normalizeTarget(
            scope: RuleScope,
            rawTarget: String,
        ): String? {
            val trimmed = rawTarget.trim()
            return when (scope) {
                RuleScope.App -> trimmed.takeIf { PackageNameRegex.matches(it) }
                RuleScope.Domain -> trimmed.toDomainOrNull()
                else -> null
            }
        }

        private fun normalizeLimitTarget(
            targetType: PolicyTargetType,
            rawTarget: String,
        ): String? =
            when (targetType) {
                PolicyTargetType.App -> normalizeTarget(RuleScope.App, rawTarget)
                PolicyTargetType.Domain -> normalizeTarget(RuleScope.Domain, rawTarget)
                else -> null
            }

        private fun List<RemoteInstalledAppDto>.toAppControls(
            rules: List<PolicyRule>,
            limits: List<DailyLimit>,
            devices: List<Device>,
            pendingAllowed: Map<String, Boolean>,
        ): List<AppControlUiState> {
            val devicesById = devices.associateBy { it.id }
            return distinctBy { it.deviceId to it.packageName }
                .map { app ->
                    val effectiveRule = rules.effectiveAppRule(app.packageName)
                    val limit =
                        limits.firstOrNull {
                            it.enabled &&
                                it.targetType == PolicyTargetType.App &&
                                it.target == app.packageName
                        }
                    AppControlUiState(
                        appName = app.appName,
                        packageName = app.packageName,
                        versionName = app.versionName,
                        isSystemApp = app.isSystemApp,
                        iconBase64 = app.iconBase64,
                        deviceName = devicesById[app.deviceId]?.displayName ?: "Usuario",
                        allowed = pendingAllowed[app.packageName] ?: (effectiveRule?.action != RuleAction.Block),
                        dailyLimitMinutes = limit?.limitMinutes,
                        isUpdating = pendingAllowed.containsKey(app.packageName),
                    )
                }
                .sortedWith(compareBy({ it.deviceName.lowercase() }, { it.appName.lowercase() }, { it.packageName }))
        }

        private fun List<PolicyRule>.effectiveAppRule(packageName: String): PolicyRule? =
            asSequence()
                .filter { it.enabled && it.scope == RuleScope.App && it.target == packageName }
                .sortedWith(
                    compareByDescending<PolicyRule> { it.level.specificity }
                        .thenByDescending { it.priority },
                )
                .firstOrNull()

        private fun List<PolicyRule>.internetBlocked(): Boolean =
            any {
                it.enabled &&
                    it.scope == RuleScope.Domain &&
                    it.target == DomainWildcard &&
                    it.action == RuleAction.Block
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

        private fun List<Device>.toUserDevices(apps: List<RemoteInstalledAppDto>): List<UserDeviceUiState> {
            val appDeviceIds = apps.mapTo(mutableSetOf()) { it.deviceId }
            val appsByDevice = apps.groupBy { it.deviceId }
            return filter { device ->
                device.appRole != "admin" && (device.appRole == "user" || device.id in appDeviceIds)
            }.map { device ->
                val newestAppSeen =
                    appsByDevice[device.id]
                        ?.mapNotNull { it.updatedAt.toEpochMillisOrNull() }
                        ?.maxOrNull()
                val lastSeen = device.lastSeenAtEpochMillis ?: newestAppSeen
                UserDeviceUiState(
                    id = device.id,
                    name = device.displayName,
                    active = lastSeen?.let { System.currentTimeMillis() - it <= ActiveDeviceWindowMillis } ?: false,
                    lastSeenLabel = lastSeen.toLastSeenLabel(),
                    appCount = appsByDevice[device.id]?.distinctBy { it.packageName }?.size ?: 0,
                )
            }.sortedWith(compareByDescending<UserDeviceUiState> { it.active }.thenBy { it.name.lowercase() })
        }

        private fun List<AppControlUiState>.filterBySearch(query: String): List<AppControlUiState> {
            val normalized = query.trim().lowercase()
            if (normalized.isBlank()) return this
            return filter {
                it.appName.lowercase().contains(normalized) ||
                    it.packageName.lowercase().contains(normalized) ||
                    it.deviceName.lowercase().contains(normalized)
            }
        }

        private fun String.toEpochMillisOrNull(): Long? = runCatching { Instant.parse(this).toEpochMilli() }.getOrNull()

        private fun String.toDomainOrNull(): String? {
            val withoutScheme =
                trim()
                    .lowercase()
                    .substringAfter("://", missingDelimiterValue = trim().lowercase())
                    .substringBefore("/")
                    .substringBefore("?")
                    .substringBefore("#")
                    .substringBefore(":")
                    .removeSuffix(".")
                    .removePrefix("www.")
            return withoutScheme.takeIf { DomainRegex.matches(it) }
        }

        private fun String.expandBlockedDomainFamily(): List<String> =
            if (this in YouTubeWebDomains) {
                YouTubeWebDomains
            } else {
                listOf(this)
            }

        private fun Long?.toLastSeenLabel(): String =
            this?.let { value ->
                val minutes = Duration.ofMillis((System.currentTimeMillis() - value).coerceAtLeast(0)).toMinutes()
                when {
                    minutes < 1 -> "ahora"
                    minutes < 60 -> "hace ${minutes}m"
                    minutes < 24 * 60 -> "hace ${minutes / 60}h"
                    else -> "hace ${minutes / (24 * 60)}d"
                }
            } ?: "sin señal"

        private companion object {
            val PackageNameRegex = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
            val DomainRegex = Regex("^(?=.{1,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$")
            const val ActiveDeviceWindowMillis = 15 * 60 * 1000L
            const val SwitchHoldMillis = 2_500L
            const val RoomConfirmTimeoutMillis = 5_000L
            const val DomainWildcard = "*"
            const val InternetBlockPriority = 10
            const val BlockDomainPriority = 2_000
            const val AllowDomainPriority = 1_000
            const val LogTag = "RulesViewModel"
            val GoogleSearchDomains =
                listOf(
                    "google.com",
                    "gstatic.com",
                    "googleapis.com",
                    "googleusercontent.com",
                    "bing.com",
                    "duckduckgo.com",
                )
            val YouTubeWebDomains =
                listOf(
                    "youtube.com",
                    "youtubei.googleapis.com",
                    "googlevideo.com",
                    "ytimg.com",
                )
        }
    }
