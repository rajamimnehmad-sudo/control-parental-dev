package com.contentfilter.admin.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.admin.BuildConfig
import com.contentfilter.core.domain.model.ActivationCredentials
import com.contentfilter.core.domain.model.ActivationResult
import com.contentfilter.core.domain.repository.ActivationRepository
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.network.config.SupabaseConfigProvider
import com.contentfilter.core.sync.SyncScheduler
import com.contentfilter.core.sync.engine.SyncEngine
import com.contentfilter.core.sync.realtime.RealtimeSyncCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminAuthViewModel
    @Inject
    constructor(
        private val activationRepository: ActivationRepository,
        private val deviceActivationRepository: DeviceActivationRepository,
        private val syncScheduler: SyncScheduler,
        private val syncEngine: SyncEngine,
        private val realtimeSyncCoordinator: RealtimeSyncCoordinator,
        configProvider: SupabaseConfigProvider,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow(
                AdminAuthUiState(offlineMode = !configProvider.current().isConfigured),
            )
        val uiState: StateFlow<AdminAuthUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                deviceActivationRepository.observeActivation().collect { activation ->
                    if (activation != null) {
                        _uiState.update {
                            it.copy(
                                activated = true,
                                loading = false,
                                message = "Administrador activado. No hace falta volver a iniciar sesión.",
                            )
                        }
                    }
                }
            }
        }

        fun onEmailChanged(value: String) = update { copy(email = value) }

        fun onPasswordChanged(value: String) = update { copy(password = value) }

        fun onActivationCodeChanged(value: String) = update { copy(activationCode = value) }

        fun onDeviceNameChanged(value: String) = update { copy(deviceName = value) }

        fun activate() {
            val state = _uiState.value
            if (state.activated) {
                _uiState.update { it.copy(message = "Administrador activado. No hace falta volver a iniciar sesión.") }
                return
            }
            if (state.offlineMode) {
                _uiState.update { it.copy(message = "Modo Offline / Desarrollo") }
                return
            }
            if (state.email.isBlank() || state.password.isBlank() || state.activationCode.isBlank()) {
                val reason = "Local validation failed: email, password or activation code is blank."
                Log.w(LogTag, reason)
                _uiState.update { it.copy(message = activationFailureMessage("Completá email, password y código.")) }
                return
            }
            viewModelScope.launch {
                _uiState.update { it.copy(loading = true, message = "Activando") }
                val result =
                    runCatching {
                        activationRepository.activate(
                            ActivationCredentials(
                                email = state.email.trim(),
                                password = state.password,
                                activationCode = state.activationCode.trim(),
                                deviceDisplayName = state.deviceName.ifBlank { "Admin Android" },
                                appVersionCode = BuildConfig.VERSION_CODE,
                                appRole = AdminRole,
                            ),
                        )
                    }.getOrElse { exception ->
                        val message = exception.message?.takeIf { it.isNotBlank() } ?: exception.toString()
                        Log.e(
                            LogTag,
                            "Unexpected admin activation exception: ${exception.javaClass.name}: $message",
                            exception,
                        )
                        ActivationResult.Failed(
                            "Unexpected admin activation exception: ${exception.javaClass.simpleName}: $message",
                        )
                    }
                if (result is ActivationResult.Activated) {
                    realtimeSyncCoordinator.stop()
                    realtimeSyncCoordinator.start()
                    syncScheduler.requestSync()
                    viewModelScope.launch(Dispatchers.IO) {
                        syncEngine.syncCoreDataFull()
                    }
                } else if (result is ActivationResult.Failed) {
                    Log.e(LogTag, "Admin activation failed. Reason: ${result.reason.ifBlank { "<blank reason>" }}")
                }
                _uiState.update {
                    it.copy(
                        loading = false,
                        message =
                            when (result) {
                                is ActivationResult.Activated -> "Administrador activado"
                                is ActivationResult.Failed -> activationFailureMessage(result.reason)
                            },
                    )
                }
            }
        }

        private fun update(block: AdminAuthUiState.() -> AdminAuthUiState) {
            _uiState.update { it.block() }
        }

        private fun activationFailureMessage(reason: String): String {
            val detail = reason.ifBlank { "No se recibió detalle técnico del error." }
            if (detail == OfflineMessage) return OfflineMessage
            return "No se pudo activar. Revisá email, password y código."
        }

        private companion object {
            const val AdminRole = "admin"
            const val LogTag = "AdminAuthViewModel"
            const val OfflineMessage = "Sin conexión. Mostrando datos guardados."
        }
    }
