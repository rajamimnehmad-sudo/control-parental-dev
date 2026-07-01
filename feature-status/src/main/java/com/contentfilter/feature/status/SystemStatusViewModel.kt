package com.contentfilter.feature.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.domain.repository.SystemStatusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class SystemStatusViewModel
    @Inject
    constructor(
        repository: SystemStatusRepository,
    ) : ViewModel() {
        val uiState = repository
            .observeHealth()
            .map(SystemStatusUiState::from)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                initialValue = SystemStatusUiState(
                    title = "Estado del sistema",
                    protectionLevel = com.contentfilter.core.domain.model.ProtectionLevel.Warning,
                    summary = "Verificando proteccion local",
                    vpnState = "Desconocida",
                    accessibilityState = "Desconocida",
                    syncState = "Desconocida",
                    activationState = "PendingActivation",
                    appVersion = "1.0.0",
                    isVpnActive = false,
                ),
            )
    }
