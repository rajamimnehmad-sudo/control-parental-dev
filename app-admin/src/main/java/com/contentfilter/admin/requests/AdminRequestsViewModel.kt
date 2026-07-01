package com.contentfilter.admin.requests

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.domain.model.AccessRequest
import com.contentfilter.core.domain.model.RequestStatus
import com.contentfilter.core.domain.usecase.admin.ApproveAccessRequestUseCase
import com.contentfilter.core.domain.usecase.admin.GrantExtraTimeUseCase
import com.contentfilter.core.domain.usecase.admin.ObserveRequestsUseCase
import com.contentfilter.core.domain.usecase.admin.SetRequestStatusUseCase
import com.contentfilter.core.network.config.SupabaseConfigProvider
import com.contentfilter.core.sync.SyncScheduler
import com.contentfilter.core.sync.engine.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AdminRequestsViewModel
    @Inject
    constructor(
        observeRequests: ObserveRequestsUseCase,
        private val approveAccessRequest: ApproveAccessRequestUseCase,
        private val setRequestStatus: SetRequestStatusUseCase,
        private val grantExtraTime: GrantExtraTimeUseCase,
        private val syncScheduler: SyncScheduler,
        private val syncEngine: SyncEngine,
        configProvider: SupabaseConfigProvider,
    ) : ViewModel() {
        private val syncMessage = MutableStateFlow("")

        val uiState = observeRequests()
            .map { requests ->
                Log.i(LogTag, "Loaded local access requests count=${requests.size}")
                requests
            }
            .catch { exception ->
                Log.e(LogTag, "Requests flow failed: ${exception.message}", exception)
                syncMessage.update { "No se pudieron cargar las solicitudes." }
                emit(emptyList())
            }
            .combine(syncMessage) { requests, message ->
                AdminRequestsUiState(
                    requests = requests,
                    offlineMode = !configProvider.current().isConfigured,
                    lastSyncMessage = message,
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = AdminRequestsUiState(offlineMode = !configProvider.current().isConfigured),
            )

        init {
            Log.i(LogTag, "Admin requests opened; requesting immediate sync.")
            syncScheduler.requestSync()
            syncNow()
        }

        fun approve(requestId: String) {
            viewModelScope.launch {
                runCatching {
                    val request = uiState.value.requests.firstOrNull { it.id == requestId }
                    if (request?.status?.isPending() == false) return@runCatching
                    if (request == null) {
                        setRequestStatus(requestId, RequestStatus.Approved)
                    } else {
                        approveAccessRequest(request)
                    }
                    syncScheduler.requestSync()
                    syncNow()
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
                    if (request?.status?.isPending() == false) return@runCatching
                    setRequestStatus(requestId, RequestStatus.Rejected)
                    syncScheduler.requestSync()
                    syncNow()
                    syncMessage.update { "Solicitud rechazada." }
                }.onFailure { exception ->
                    Log.e(LogTag, "Reject failed requestId=$requestId: ${exception.message}", exception)
                    syncMessage.update { "No se pudo rechazar la solicitud." }
                }
            }
        }

        fun grantTime(request: AccessRequest) {
            viewModelScope.launch {
                runCatching {
                    if (!request.status.isPending()) return@runCatching
                    grantExtraTime(
                        request = request,
                        minutes = request.requestedMinutes ?: DefaultGrantMinutes,
                        nowEpochMillis = System.currentTimeMillis(),
                    )
                    syncScheduler.requestSync()
                    syncNow()
                    syncMessage.update { "Tiempo extra concedido." }
                }.onFailure { exception ->
                    Log.e(LogTag, "Grant time failed requestId=${request.id}: ${exception.message}", exception)
                    syncMessage.update { "No se pudo conceder tiempo extra." }
                }
            }
        }

        private fun syncNow() {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    val outboxResult = syncEngine.syncOnce()
                    val result = syncEngine.syncRequestResultsFull()
                    Log.i(
                        LogTag,
                        "Immediate admin requests sync outboxSuccess=${outboxResult.success} resultsSuccess=${result.success} message=${result.message}",
                    )
                    syncMessage.update {
                        if (outboxResult.success && result.success) {
                            "Sync solicitudes OK: ${result.message}"
                        } else if (outboxResult.message.contains(OfflineMessage) || result.message.contains(OfflineMessage)) {
                            OfflineMessage
                        } else {
                            "Sync solicitudes falló: ${outboxResult.message}; ${result.message}"
                        }
                    }
                }.onFailure { exception ->
                    Log.e(LogTag, "Immediate admin requests sync failed: ${exception.message}", exception)
                    syncMessage.update {
                        if (exception.message == OfflineMessage) {
                            OfflineMessage
                        } else {
                            "Sync solicitudes falló: ${exception.message.orEmpty()}"
                        }
                    }
                }
            }
        }

        private companion object {
            const val DefaultGrantMinutes = 15
            const val LogTag = "AdminRequests"
            const val OfflineMessage = "Sin conexión. Mostrando datos guardados."
        }
    }

private fun RequestStatus.isPending(): Boolean =
    this == RequestStatus.PendingLocal || this == RequestStatus.PendingRemote
