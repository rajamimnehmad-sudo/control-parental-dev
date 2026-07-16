package com.contentfilter.admin.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.network.remote.RemoteProtectionAlert
import com.contentfilter.core.network.remote.RemoteProtectionAlertRepository
import com.contentfilter.core.network.remote.RemoteResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class AdminAlertsViewModel
    @Inject
    constructor(
        private val repository: RemoteProtectionAlertRepository,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(AdminAlertsUiState())
        val uiState: StateFlow<AdminAlertsUiState> = mutableState.asStateFlow()

        init {
            refresh()
        }

        fun refresh() {
            if (mutableState.value.loading) return
            mutableState.update { it.copy(loading = true, message = "") }
            viewModelScope.launch(Dispatchers.IO) {
                when (val result = repository.pullAlerts()) {
                    is RemoteResult.Success ->
                        mutableState.update {
                            it.copy(
                                loading = false,
                                alerts = result.value.map(RemoteProtectionAlert::toUiState),
                                message = if (result.value.isEmpty()) "No hay alertas confirmadas." else "",
                            )
                        }
                    is RemoteResult.Failure ->
                        mutableState.update {
                            it.copy(loading = false, message = "No se pudieron actualizar las alertas.")
                        }
                }
            }
        }
    }

data class AdminAlertsUiState(
    val alerts: List<AdminAlertUiState> = emptyList(),
    val loading: Boolean = false,
    val message: String = "",
)

data class AdminAlertUiState(
    val id: String,
    val deviceId: String,
    val title: String,
    val body: String,
    val alertType: String,
    val createdAtEpochMillis: Long,
)

private fun RemoteProtectionAlert.toUiState(): AdminAlertUiState =
    AdminAlertUiState(
        id = id,
        deviceId = deviceId,
        title = title,
        body = body,
        alertType = alertType,
        createdAtEpochMillis = Instant.parse(createdAt).toEpochMilli(),
    )
