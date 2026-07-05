package com.contentfilter.admin.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.admin.BuildConfig
import com.contentfilter.admin.devtools.AdminDevTools
import com.contentfilter.core.domain.model.TechnicalDiagnostic
import com.contentfilter.core.domain.model.RequestStatus
import com.contentfilter.core.domain.repository.SystemStatusRepository
import com.contentfilter.core.domain.repository.TelemetryRepository
import com.contentfilter.core.domain.usecase.admin.ObserveDevicesUseCase
import com.contentfilter.core.domain.usecase.admin.ObserveRequestsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel
    @Inject
    constructor(
        observeDevices: ObserveDevicesUseCase,
        observeRequests: ObserveRequestsUseCase,
        systemStatusRepository: SystemStatusRepository,
        private val devTools: AdminDevTools,
        private val telemetryRepository: TelemetryRepository,
    ) : ViewModel() {
        private val devToolsState = MutableStateFlow(DevToolsState())

        val uiState =
            combine(
                observeDevices(),
                observeRequests(),
                systemStatusRepository.observeHealth(),
                telemetryRepository.observeDiagnostics(),
                devToolsState,
            ) { devices, requests, health, diagnostics, devState ->
                DashboardUiState(
                    deviceCount = devices.count { it.appRole != "admin" },
                    pendingRequests =
                        requests.count {
                            it.status == RequestStatus.PendingLocal || it.status == RequestStatus.PendingRemote
                        },
                    syncState = health.syncState.name,
                    systemState = health.protectionLevel.name,
                    lastSync = health.checkedAtEpochMillis.toDisplayDate(),
                    offlineMode = false,
                    showDevTools = BuildConfig.FLAVOR == "dev",
                    devToolsBusy = devState.busy,
                    devToolsMessage = devState.message,
                    diagnosticsText = diagnostics.formatDiagnostics(),
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue =
                    DashboardUiState(
                        offlineMode = false,
                        showDevTools = BuildConfig.FLAVOR == "dev",
                    ),
            )

        fun clearLocalRequests() =
            runDevTool("clear_local_requests") {
                devTools.clearLocalRequests()
            }

        fun clearRemoteRequests() =
            runDevTool("clear_remote_requests") {
                devTools.clearRemoteRequests()
            }

        fun clearAllRequests() =
            runDevTool("clear_all_requests") {
                devTools.clearAllRequests()
            }

        fun clearRules() =
            runDevTool("clear_rules") {
                devTools.clearRules()
            }

        fun clearExtraTimeGrants() =
            runDevTool("clear_extra_time_grants") {
                devTools.clearExtraTimeGrants()
            }

        fun clearDuplicateDevices() =
            runDevTool("clear_duplicate_devices") {
                devTools.clearDuplicateDevices()
            }

        fun resetDev() =
            runDevTool("reset_dev") {
                devTools.resetDev()
            }

        fun clearDiagnostics() =
            runDevTool("clear_diagnostics") {
                telemetryRepository.clearDiagnostics()
                "Diagnóstico limpiado."
            }

        private fun runDevTool(
            name: String,
            block: suspend () -> String,
        ) {
            if (BuildConfig.FLAVOR != "dev") return
            viewModelScope.launch {
                devToolsState.update { it.copy(busy = true, message = "Ejecutando $name...") }
                runCatching { block() }
                    .onSuccess { message ->
                        Log.i(LogTag, "DEV tool $name completed: $message")
                        devToolsState.update { it.copy(busy = false, message = message) }
                    }.onFailure { exception ->
                        Log.e(LogTag, "DEV tool $name failed: ${exception.message}", exception)
                        devToolsState.update {
                            it.copy(
                                busy = false,
                                message =
                                    exception.message?.takeIf { value -> value.isNotBlank() }
                                        ?: "No se pudo ejecutar herramienta DEV.",
                            )
                        }
                    }
            }
        }

        private data class DevToolsState(
            val busy: Boolean = false,
            val message: String = "",
        )

        private companion object {
            const val LogTag = "DashboardViewModel"
        }
    }

private fun List<TechnicalDiagnostic>.formatDiagnostics(): String {
    if (isEmpty()) return "Sin eventos."
    return joinToString(separator = "\n") { diagnostic ->
        "${diagnostic.occurredAtEpochMillis.toDiagnosticDate()} ${diagnostic.type} ${diagnostic.message}"
    }
}

private fun Long.toDiagnosticDate(): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(this))

private fun Long.toDisplayDate(): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(this))
