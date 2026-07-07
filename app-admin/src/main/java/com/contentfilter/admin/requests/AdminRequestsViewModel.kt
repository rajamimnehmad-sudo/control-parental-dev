package com.contentfilter.admin.requests

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.domain.model.AccessRequest
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
import dagger.hilt.android.lifecycle.HiltViewModel
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
    ) : ViewModel() {
        private val syncMessage = MutableStateFlow("")
        private val isLoading = MutableStateFlow(false)
        private val selectedDeviceId = MutableStateFlow<String?>(null)
        private val installedApps = MutableStateFlow<List<RemoteInstalledAppDto>>(emptyList())
        private val localState =
            combine(
                installedApps,
                syncMessage,
                isLoading,
                selectedDeviceId,
            ) { apps, message, loading, selected ->
                RequestsLocalState(
                    apps = apps,
                    message = message,
                    loading = loading,
                    selectedDeviceId = selected,
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
                    val users = pendingRequests.toUserItems(devices)
                    val resolvedSelected = local.selectedDeviceId?.takeIf { id -> users.any { it.deviceId == id } }
                    AdminRequestsUiState(
                        requests =
                            resolvedSelected?.let { selectedId ->
                                pendingRequests.filter { it.deviceGroupId == selectedId }
                                    .toRequestItems(local.apps)
                            }.orEmpty(),
                        users = users,
                        selectedDeviceId = resolvedSelected,
                        offlineMode = false,
                        lastSyncMessage = local.message,
                        isLoading = local.loading,
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

        fun approve(requestId: String) {
            viewModelScope.launch {
                runCatching {
                    val request = uiState.value.requests.firstOrNull { it.id == requestId }
                    val domainRequest = request?.request
                    if (domainRequest?.status?.isPending() == false) return@runCatching
                    if (domainRequest == null) {
                        setRequestStatus(requestId, RequestStatus.Approved)
                    } else {
                        approveAccessRequest(domainRequest)
                    }
                    syncScheduler.requestSync()
                    syncNowBlocking()
                    syncMessage.update { "Solicitud aprobada." }
                }.onFailure { exception ->
                    Log.e(LogTag, "Approve failed requestId=$requestId: ${exception.message}", exception)
                    syncMessage.update { "No se pudo aprobar la solicitud." }
                }
            }
        }

        fun reject(requestId: String) {
            viewModelScope.launch {
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
                }
            }
        }

        fun grantTime(
            request: AccessRequest,
            rawMinutes: String,
        ) {
            viewModelScope.launch {
                runCatching {
                    if (!request.status.isPending()) return@runCatching
                    val minutes =
                        rawMinutes.filter(Char::isDigit).toIntOrNull()
                            ?: request.requestedMinutes
                            ?: DefaultGrantMinutes
                    grantExtraTime(
                        request = request,
                        minutes = minutes.coerceAtLeast(1),
                        nowEpochMillis = System.currentTimeMillis(),
                    )
                    syncScheduler.requestSync()
                    syncNowBlocking()
                    syncMessage.update { "Tiempo extra concedido." }
                }.onFailure { exception ->
                    Log.e(LogTag, "Grant time failed requestId=${request.id}: ${exception.message}", exception)
                    syncMessage.update { "No se pudo conceder tiempo extra." }
                }
            }
        }

        private fun syncNow() {
            viewModelScope.launch(Dispatchers.IO) {
                isLoading.update { true }
                runCatching {
                    updateSyncMessage(syncNowBlocking())
                }.onFailure { exception ->
                    Log.e(LogTag, "Immediate admin requests sync failed: ${exception.message}", exception)
                    syncMessage.update {
                        if (exception.message == OfflineMessage) {
                            OfflineMessage
                        } else {
                            "Sync solicitudes falló: ${exception.message.orEmpty()}"
                        }
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
                    "Cambio guardado localmente. Se sincronizará cuando haya conexión."
                }
            }
        }

        private companion object {
            const val DefaultGrantMinutes = 15
            const val LogTag = "AdminRequests"
            const val OfflineMessage = "Sin conexión. Mostrando datos guardados."
            const val UnknownDeviceId = "unknown-device"
        }

        private data class RequestsLocalState(
            val apps: List<RemoteInstalledAppDto>,
            val message: String,
            val loading: Boolean,
            val selectedDeviceId: String?,
        )
    }

private fun RequestStatus.isPending(): Boolean =
    this == RequestStatus.PendingLocal || this == RequestStatus.PendingRemote

private val AccessRequest.deviceGroupId: String
    get() = deviceId ?: "unknown-device"

private fun List<AccessRequest>.toUserItems(devices: List<Device>): List<AdminRequestUserUiState> {
    val devicesById = devices.associateBy { it.id }
    return groupBy { it.deviceGroupId }
        .map { (deviceId, requests) ->
            AdminRequestUserUiState(
                deviceId = deviceId,
                name = devicesById[deviceId]?.displayName ?: "Usuario",
                pendingCount = requests.size,
            )
        }
        .sortedWith(
            compareByDescending<AdminRequestUserUiState> { it.needsAttention }
                .thenBy { it.name.lowercase() },
        )
}

private fun List<AccessRequest>.toRequestItems(apps: List<RemoteInstalledAppDto>): List<AdminAccessRequestUiState> {
    val appsByDeviceAndPackage = apps.associateBy { "${it.deviceId}:${it.packageName}" }
    val appsByPackage = apps.distinctBy { it.packageName }.associateBy { it.packageName }
    return map { request ->
        val packageName = request.targetPackageName ?: request.target
        val app =
            request.deviceId
                ?.let { deviceId -> appsByDeviceAndPackage["$deviceId:$packageName"] }
                ?: appsByPackage[packageName]
        AdminAccessRequestUiState(
            request = request,
            appName = app?.appName?.takeIf { it.isNotBlank() } ?: packageName,
            iconBase64 = app?.iconBase64,
        )
    }
}
