package com.contentfilter.feature.status

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.model.LicenseState
import com.contentfilter.core.domain.model.SystemHealthSnapshot
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.domain.repository.SystemStatusRepository
import com.contentfilter.feature.accessibility.service.AccessibilityController
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class SystemStatusViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val repository: SystemStatusRepository,
        activationRepository: DeviceActivationRepository,
    ) : ViewModel() {
        val uiState = combine(
            repository.observeHealth(),
            activationRepository.observeActivation(),
        ) { health, activation ->
            val accessibilityState = if (AccessibilityController.isEnabled(context)) {
                ComponentState.Enabled
            } else {
                ComponentState.Disabled
            }
            val licenseState = if (activation != null) LicenseState.Active else health.licenseState
            NormalizedStatus(
                snapshot = health.copy(
                    accessibilityState = accessibilityState,
                    licenseState = licenseState,
                ),
                shouldPersistAccessibility = health.accessibilityState != accessibilityState,
                shouldPersistLicense = health.licenseState != licenseState,
            )
        }
            .onEach { status ->
                if (status.shouldPersistAccessibility) {
                    repository.updateAccessibilityState(status.snapshot.accessibilityState)
                }
                if (status.shouldPersistLicense) {
                    repository.updateLicenseState(status.snapshot.licenseState)
                }
            }
            .map { SystemStatusUiState.from(it.snapshot) }
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

        private data class NormalizedStatus(
            val snapshot: SystemHealthSnapshot,
            val shouldPersistAccessibility: Boolean,
            val shouldPersistLicense: Boolean,
        )
    }
