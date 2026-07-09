package com.contentfilter.feature.status

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.model.LicenseState
import com.contentfilter.core.domain.model.SystemHealthSnapshot
import com.contentfilter.core.domain.repository.AccountRepository
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.domain.repository.SystemStatusRepository
import com.contentfilter.feature.accessibility.service.AccessibilityController
import com.contentfilter.feature.vpn.service.VpnController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class SystemStatusViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val repository: SystemStatusRepository,
        activationRepository: DeviceActivationRepository,
        accountRepository: AccountRepository,
    ) : ViewModel() {
        private val activationFlow = activationRepository.observeActivation()
        private val accountFlow =
            activationFlow.flatMapLatest { activation ->
                activation?.accountId?.let(accountRepository::observeAccount) ?: flowOf(null)
            }

        val uiState =
            combine(
                repository.observeHealth(),
                activationFlow,
                accountFlow,
            ) { health, activation, account ->
                val accessibilityState =
                    if (AccessibilityController.isEnabled(context)) {
                        ComponentState.Enabled
                    } else {
                        ComponentState.Disabled
                    }
                val vpnState =
                    if (VpnController.isRunning(context)) {
                        ComponentState.Enabled
                    } else {
                        ComponentState.Disabled
                    }
                val licenseState = if (activation != null) LicenseState.Active else health.licenseState
                NormalizedStatus(
                    snapshot =
                        health.copy(
                            vpnState = vpnState,
                            accessibilityState = accessibilityState,
                            licenseState = licenseState,
                        ),
                    shouldPersistVpn = health.vpnState != vpnState,
                    shouldPersistAccessibility = health.accessibilityState != accessibilityState,
                    shouldPersistLicense = health.licenseState != licenseState,
                    communityName = account?.communityName.orEmpty(),
                    guideName = account?.guideName.orEmpty(),
                )
            }
                .onEach { status ->
                    if (status.shouldPersistVpn) {
                        repository.updateVpnState(status.snapshot.vpnState)
                    }
                    if (status.shouldPersistAccessibility) {
                        repository.updateAccessibilityState(status.snapshot.accessibilityState)
                    }
                    if (status.shouldPersistLicense) {
                        repository.updateLicenseState(status.snapshot.licenseState)
                    }
                }
                .map { SystemStatusUiState.from(it.snapshot, it.communityName, it.guideName) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                    initialValue =
                        SystemStatusUiState(
                            title = "Estado del sistema",
                            protectionLevel = com.contentfilter.core.domain.model.ProtectionLevel.Warning,
                            summary = "Verificando proteccion local",
                            vpnState = "Desconocida",
                            accessibilityState = "Desconocida",
                            syncState = "Desconocida",
                            activationState = "PendingActivation",
                            appVersion = "1.0.0",
                            isVpnActive = false,
                            communityName = "",
                            guideName = "",
                        ),
                )

        private data class NormalizedStatus(
            val snapshot: SystemHealthSnapshot,
            val shouldPersistVpn: Boolean,
            val shouldPersistAccessibility: Boolean,
            val shouldPersistLicense: Boolean,
            val communityName: String,
            val guideName: String,
        )
    }
