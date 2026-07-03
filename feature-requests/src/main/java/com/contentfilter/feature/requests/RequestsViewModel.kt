package com.contentfilter.feature.requests

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.domain.model.AccessRequest
import com.contentfilter.core.domain.model.AccessRequestType
import com.contentfilter.core.domain.model.PolicyTargetType
import com.contentfilter.core.domain.model.RequestStatus
import com.contentfilter.core.domain.repository.AccessRequestRepository
import com.contentfilter.core.domain.repository.ExtraTimeGrantRepository
import com.contentfilter.core.sync.SyncScheduler
import com.contentfilter.core.sync.engine.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class RequestsViewModel
    @Inject
    constructor(
        private val repository: AccessRequestRepository,
        private val grantRepository: ExtraTimeGrantRepository,
        private val syncScheduler: SyncScheduler,
        private val syncEngine: SyncEngine,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(RequestsUiState())
        val uiState: StateFlow<RequestsUiState> = mutableState.asStateFlow()

        init {
            viewModelScope.launch {
                combine(
                    repository.observeRequests(),
                    grantRepository.observeGrants(),
                ) { requests, grants -> requests to grants }
                    .collect { (requests, grants) ->
                        val pendingRequests =
                            requests.filter {
                                it.status == RequestStatus.PendingLocal || it.status == RequestStatus.PendingRemote
                            }
                        val latestResult =
                            requests
                                .filter { it.status == RequestStatus.Approved || it.status == RequestStatus.Rejected }
                                .maxByOrNull { it.createdAtEpochMillis }
                        mutableState.update {
                            it.copy(
                                pendingCount = pendingRequests.size,
                                requests = pendingRequests,
                                extraTimeGrants = grants,
                                message =
                                    latestResult?.let { request ->
                                        "Última solicitud: ${request.status.displayName()}"
                                    } ?: it.message,
                            )
                        }
                    }
            }
            syncResultsNow()
        }

        fun onTargetChanged(value: String) = mutableState.update { it.copy(target = value) }

        fun onReasonChanged(value: String) = mutableState.update { it.copy(reason = value) }

        fun onMinutesChanged(value: String) = mutableState.update { it.copy(minutes = value.filter(Char::isDigit)) }

        fun requestAppAccess() =
            saveRequest(
                requestType = AccessRequestType.APP_ACCESS,
                targetType = PolicyTargetType.App,
                requestedMinutes = null,
            )

        fun requestDomainAccess() =
            saveRequest(
                requestType = AccessRequestType.DOMAIN_ACCESS,
                targetType = PolicyTargetType.Domain,
                requestedMinutes = null,
            )

        fun requestExtraTime() =
            saveRequest(
                requestType = AccessRequestType.EXTRA_TIME,
                targetType = PolicyTargetType.Global,
                requestedMinutes = mutableState.value.minutes.toIntOrNull() ?: 15,
            )

        private fun saveRequest(
            requestType: AccessRequestType,
            targetType: PolicyTargetType,
            requestedMinutes: Int?,
        ) {
            val state = mutableState.value
            if (requestType != AccessRequestType.EXTRA_TIME && state.target.isBlank()) {
                mutableState.update { it.copy(message = "Indicá paquete o dominio.") }
                return
            }
            val target = state.target.trim()
            viewModelScope.launch {
                val request =
                    AccessRequest(
                        id = UUID.randomUUID().toString(),
                        requestType = requestType,
                        targetType = targetType,
                        target = target.ifBlank { "extra_time" },
                        targetPackageName = if (targetType == PolicyTargetType.App) target.ifBlank { null } else null,
                        targetDomain = if (targetType == PolicyTargetType.Domain) target.ifBlank { null } else null,
                        reason = state.reason.trim(),
                        requestedMinutes = requestedMinutes,
                        status = RequestStatus.PendingLocal,
                        createdAtEpochMillis = System.currentTimeMillis(),
                        expiresAtEpochMillis = null,
                    )
                repository.saveRequest(request)
                Log.i(
                    LogTag,
                    "Queued access request id=${request.id} requestType=${request.requestType} targetType=${request.targetType}",
                )
                syncScheduler.requestSync()
                syncNow(request.id)
                mutableState.update { it.copy(message = "Solicitud pendiente creada.", target = "", reason = "") }
            }
        }

        private fun syncNow(requestId: String) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    val result = syncEngine.syncOnce()
                    val resultSync = syncEngine.syncRequestResultsFull()
                    Log.i(
                        LogTag,
                        "Immediate request sync id=$requestId success=${result.success} message=${result.message}",
                    )
                    Log.i(
                        LogTag,
                        "Immediate request results sync id=$requestId success=${resultSync.success} message=${resultSync.message}",
                    )
                }.onFailure { exception ->
                    Log.e(LogTag, "Immediate request sync failed id=$requestId: ${exception.message}", exception)
                }
            }
        }

        private fun syncResultsNow() {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    val result = syncEngine.syncRequestResultsFull()
                    Log.i(LogTag, "Request results sync success=${result.success} message=${result.message}")
                }.onFailure { exception ->
                    Log.e(LogTag, "Request results sync failed: ${exception.message}", exception)
                }
            }
        }

        private companion object {
            const val LogTag = "RequestsViewModel"
        }
    }

private fun RequestStatus.displayName(): String =
    when (this) {
        RequestStatus.PendingLocal,
        RequestStatus.PendingRemote,
        -> "Pendiente"
        RequestStatus.Approved -> "Aprobada"
        RequestStatus.Rejected -> "Rechazada"
        RequestStatus.Expired -> "Expirada"
    }
