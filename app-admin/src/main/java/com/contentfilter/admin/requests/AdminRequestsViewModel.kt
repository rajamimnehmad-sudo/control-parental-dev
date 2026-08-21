package com.contentfilter.admin.requests

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.domain.model.AccessRequest
import com.contentfilter.core.domain.model.AccessRequestType
import com.contentfilter.core.domain.model.Device
import com.contentfilter.core.domain.model.RequestStatus
import com.contentfilter.core.domain.usecase.admin.ApproveAccessRequestUseCase
import com.contentfilter.core.domain.usecase.admin.GrantExtraTimeUseCase
import com.contentfilter.core.domain.usecase.admin.ObserveDevicesUseCase
import com.contentfilter.core.domain.usecase.admin.ObserveRequestsUseCase
import com.contentfilter.core.domain.usecase.admin.SetRequestStatusUseCase
import com.contentfilter.core.network.dto.RemoteInstalledAppDto
import com.contentfilter.core.network.remote.RemoteInstalledAppRepository
import com.contentfilter.core.network.remote.RemoteResult
import com.contentfilter.core.sync.SyncScheduler
import com.contentfilter.core.sync.engine.SyncEngine
import com.contentfilter.core.sync.engine.TargetedPolicySyncCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AdminRequestsViewModel
    @Inject
    constructor(
        observeRequests: ObserveRequestsUseCase,
        observeDevices: ObserveDevicesUseCase,
        private val approveAccessRequest: ApproveAccessRequestUseCase,
        private val setRequestStatus: SetRequestStatusUseCase,
        private val grantExtraTime: GrantExtraTimeUseCase,
        private val remoteInstalledAppRepository: RemoteInstalledAppRepository,
        private val syncScheduler: SyncScheduler,
        private val syncEngine: SyncEngine,
        private val targetedPolicySyncCoordinator: TargetedPolicySyncCoordinator,
        @ApplicationContext context: Context,
    ) : ViewModel() {
        private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        private val syncMessage = MutableStateFlow("")
        private val isLoading = MutableStateFlow(false)
        private val lastRefreshedAtEpochMillis = MutableStateFlow<Long?>(null)
        private val selectedDeviceId = MutableStateFlow<String?>(null)
        private val pendingActionIds = MutableStateFlow<Set<String>>(emptySet())
        private val hiddenHistoryIds =
            MutableStateFlow(
                preferences.getStringSet(HiddenHistoryIdsKey, emptySet()).orEmpty(),
            )
        private val installedApps = MutableStateFlow<List<RemoteInstalledAppDto>>(emptyList())
        private val refreshState =
            combine(syncMessage, isLoading, lastRefreshedAtEpochMillis) { message, loading, refreshedAt ->
                RequestsRefreshState(message = message, loading = loading, lastRefreshedAtEpochMillis = refreshedAt)
            }
        private val localState =
            combine(
                installedApps,
                refreshState,
                selectedDeviceId,
                pendingActionIds,
                hiddenHistoryIds,
            ) { apps, refresh, selected, pendingActions, hiddenIds ->
                RequestsLocalState(
                    apps = apps,
                    message = refresh.message,
                    loading = refresh.loading,
                    lastRefreshedAtEpochMillis = refresh.lastRefreshedAtEpochMillis,
                    selectedDeviceId = selected,
                    pendingActionIds = pendingActions,
                    hiddenHistoryIds = hiddenIds,
                )
            }

        val uiState =
            combine(
                observeRequests()
                    .map { requests ->
                        Log.i(LogTag, "Loaded local access requests count=${requests.size}")
                        requests
                    }
                    .catch { exception ->
                        Log.e(LogTag, "Requests flow failed: ${exception.message}", exception)
                        syncMessage.update { "No se pudieron cargar las solicitudes." }
                        emit(emptyList())
                    },
                observeDevices(),
                localState,
            ) { requests, devices, local ->
                val pendingRequests = requests.filter { it.status.isPending() }
                val resolvedRequests = requests.filterNot { it.status.isPending() }
                val users = requests.toUserItems(devices, local.hiddenHistoryIds)
                val selected = local.selectedDeviceId?.takeIf { id -> users.any { it.deviceId == id } }
                val visiblePending =
                    selected?.let { selectedId -> pendingRequests.filter { it.deviceGroupId == selectedId } }
                        ?: pendingRequests
                val visibleResolved =
                    (selected?.let { selectedId -> resolvedRequests.filter { it.deviceGroupId == selectedId } }
                        ?: resolvedRequests)
                        .filterNot { it.id in local.hiddenHistoryIds }
                        .sortedByDescending(AccessRequest::createdAtEpochMillis)
                AdminRequestsUiState(
                    requests = visiblePending.toRequestItems(local.apps, devices),
                    resolvedRequests = visibleResolved.toRequestItems(local.apps, devices),
                    users = users,
                    selectedDeviceId = selected,
                    offlineMode = false,
                    lastSyncMessage = local.message,
                    isLoading = local.loading,
                    lastRefreshedAtEpochMillis = local.lastRefreshedAtEpochMillis,
                    pendingActionIds = local.pendingActionIds,
                )
            }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = AdminRequestsUiState(offlineMode = false),
                )

        init {
            Log.i(LogTag, "Admin requests opened; requesting immediate sync.")
            refresh()
        }

        fun refresh() {
            syncScheduler.requestSync()
            syncNow()
            refreshInstalledApps()
        }

        fun selectUser(deviceId: String) {
            selectedDeviceId.value = deviceId
        }

        fun clearUserSelection() {
            selectedDeviceId.value = null
        }

        fun clearHistory(requestIds: Set<String>) {
            if (requestIds.isEmpty()) return
            val updated = hiddenHistoryIds.value + requestIds
            preferences.edit().putStringSet(HiddenHistoryIdsKey, updated).apply()
            hiddenHistoryIds.value = updated
        }

        fun approve(requestId: String) {
            val actionId = requestId.actionId("approve")
            viewModelScope.launch {
                pendingActionIds.update { it + actionId }
                runCatching {
                    val request = uiState.value.requests.firstOrNull { it.id == requestId }
                    val domainRequest = request?.request
                    if (domainRequest?.status?.isPending() == false) return@runCatching
                    if (domainRequest == null) {
                        setRequestStatus(requestId, RequestStatus.Approved)
                    } else {
                        if (domainRequest.requestType == AccessRequestType.DOMAIN_ACCESS) {
                            domainRequest.deviceId?.let { deviceId ->
                                runCatching {
                                    targetedPolicySyncCoordinator.refresh(
                                        deviceId = deviceId,
                                        reason = "admin-domain-approval",
                                    )
                                }.onFailure { exception ->
                                    Log.w(LogTag, "Policy refresh before domain approval failed: ${exception.message}")
                                }
                            }
                        }
                        approveAccessRequest(domainRequest)
                    }
                    syncScheduler.requestSync()
                    syncNowBlocking()
                    syncMessage.update { "Solicitud aprobada." }
                }.onFailure { exception ->
                    Log.e(LogTag, "Approve failed requestId=$requestId: ${exception.message}", exception)
                    syncMessage.update { "No se pudo aprobar la solicitud." }
                }.also {
                    pendingActionIds.update { it - actionId }
                }
            }
        }

        fun reject(requestId: String) {
            val actionId = requestId.actionId("reject")
            viewModelScope.launch {
                pendingActionIds.update { it + actionId }
                runCatching {
                    val request = uiState.value.requests.firstOrNull { it.id == requestId }
                    if (request?.request?.status?.isPending() == false) return@runCatching
                    setRequestStatus(requestId, RequestStatus.Rejected)
                    syncScheduler.requestSync()
                    syncNowBlocking()
                    syncMessage.update { "Solicitud rechazada." }
                }.onFailure { exception ->
                    Log.e(LogTag, "Reject failed requestId=$requestId: ${exception.message}", exception)
                    syncMessage.update { "No se pudo rechazar la solicitud." }
                }.also {
                    pendingActionIds.update { it - actionId }
                }
            }
        }

        fun grantTime(
            request: AccessRequest,
            rawMinutes: String,
        ) {
            val actionId = request.id.actionId("grant")
            viewModelScope.launch {
                pendingActionIds.update { it + actionId }
                runCatching {
                    if (!request.status.isPending()) return@runCatching
                    val minutes = rawMinutes.filter(Char::isDigit).toIntOrNull()
                    if (minutes == null || minutes < 1) {
                        syncMessage.update { "Ingresá cuántos minutos querés conceder." }
                        return@runCatching
                    }
                    grantExtraTime(
                        request = request,
                        minutes = minutes,
                        nowEpochMillis = System.currentTimeMillis(),
                    )
                    syncScheduler.requestSync()
                    syncNowBlocking()
                    syncMessage.update { "Tiempo extra concedido." }
                }.onFailure { exception ->
                    Log.e(LogTag, "Grant time failed requestId=${request.id}: ${exception.message}", exception)
                    syncMessage.update { "No se pudo conceder tiempo extra." }
                }.also {
                    pendingActionIds.update { it - actionId }
                }
            }
        }

        private fun syncNow() {
            if (isLoading.value) return
            isLoading.value = true
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    val success = syncNowBlocking()
                    updateSyncMessage(success)
                    if (success) lastRefreshedAtEpochMillis.value = System.currentTimeMillis()
                }.onFailure { exception ->
                    Log.e(LogTag, "Immediate admin requests sync failed: ${exception.message}", exception)
                    syncMessage.update {
                        "No se pudo actualizar. ${exception.message.orEmpty()}".trim()
                    }
                }.also {
                    isLoading.update { false }
                }
            }
        }

        private fun refreshInstalledApps() {
            viewModelScope.launch(Dispatchers.IO) {
                when (val result = remoteInstalledAppRepository.pullInstalledApps()) {
                    is RemoteResult.Success -> installedApps.update { result.value }
                    is RemoteResult.Failure -> Log.w(LogTag, "Installed apps pull failed: ${result.reason}")
                }
            }
        }

        private suspend fun syncNowBlocking(): Boolean =
            withContext(Dispatchers.IO) {
                val outboxResult = syncEngine.syncOnce()
                val result = syncEngine.syncRequestResultsFull()
                Log.i(
                    LogTag,
                    "Immediate admin requests sync outboxSuccess=${outboxResult.success} resultsSuccess=${result.success} message=${result.message}",
                )
                updateSyncMessage(outboxResult.success && result.success)
                outboxResult.success && result.success
            }

        private fun updateSyncMessage(success: Boolean) {
            syncMessage.update {
                if (success) {
                    "Solicitudes sincronizadas."
                } else {
                    "No se pudo actualizar. Los cambios locales se sincronizarán cuando haya conexión."
                }
            }
        }

        private companion object {
            const val PreferencesName = "admin-requests"
            const val HiddenHistoryIdsKey = "hidden-history-request-ids"
            const val LogTag = "AdminRequests"
            const val UnknownDeviceId = "unknown-device"
        }

        private data class RequestsLocalState(
            val apps: List<RemoteInstalledAppDto>,
            val message: String,
            val loading: Boolean,
            val lastRefreshedAtEpochMillis: Long?,
            val selectedDeviceId: String?,
            val pendingActionIds: Set<String>,
            val hiddenHistoryIds: Set<String>,
        )

        private data class RequestsRefreshState(
            val message: String,
            val loading: Boolean,
            val lastRefreshedAtEpochMillis: Long?,
        )
    }

private fun String.actionId(action: String): String = "$this:$action"

private fun RequestStatus.isPending(): Boolean =
    this == RequestStatus.PendingLocal || this == RequestStatus.PendingRemote

private val AccessRequest.deviceGroupId: String
    get() = deviceId ?: "unknown-device"

private fun List<AccessRequest>.toUserItems(
    devices: List<Device>,
    hiddenHistoryIds: Set<String>,
): List<AdminRequestUserUiState> {
    val devicesById = devices.associateBy { it.id }
    return groupBy { it.deviceGroupId }
        .map { (deviceId, requests) ->
            AdminRequestUserUiState(
                deviceId = deviceId,
                name = devicesById[deviceId]?.displayName ?: "Usuario",
                pendingCount = requests.count { it.status.isPending() },
                resolvedCount = requests.count { !it.status.isPending() && it.id !in hiddenHistoryIds },
            )
        }
        .sortedWith(
            compareByDescending<AdminRequestUserUiState> { it.needsAttention }
                .thenBy { it.name.lowercase() },
        )
}

private fun List<AccessRequest>.toRequestItems(
    apps: List<RemoteInstalledAppDto>,
    devices: List<Device>,
): List<AdminAccessRequestUiState> {
    val appsByDeviceAndPackage = apps.preferAppsWithIcons().associateBy { "${it.deviceId}:${it.packageName}" }
    val appsByPackage = apps.preferAppsWithIcons().distinctBy { it.packageName }.associateBy { it.packageName }
    val devicesById = devices.associateBy(Device::id)
    return sortedByDescending(AccessRequest::createdAtEpochMillis).map { request ->
        val packageName = request.targetPackageName ?: request.target
        val app =
            if (request.requestType == AccessRequestType.DOMAIN_ACCESS) {
                null
            } else {
                request.deviceId
                    ?.let { deviceId -> appsByDeviceAndPackage["$deviceId:$packageName"] }
                    ?: appsByPackage[packageName]
            }
        AdminAccessRequestUiState(
            request = request,
            appName =
                if (request.requestType == AccessRequestType.DOMAIN_ACCESS) {
                    request.targetDomain ?: request.target
                } else {
                    app?.appName?.takeIf { it.isNotBlank() } ?: "Aplicación solicitada"
                },
            iconBase64 = app?.iconBase64,
            userName = request.deviceId?.let { devicesById[it]?.displayName } ?: "Usuario",
        )
    }
}

private fun List<RemoteInstalledAppDto>.preferAppsWithIcons(): List<RemoteInstalledAppDto> =
    sortedWith(
        compareByDescending<RemoteInstalledAppDto> { !it.iconBase64.isNullOrBlank() }
            .thenByDescending { it.updatedAt },
    )
