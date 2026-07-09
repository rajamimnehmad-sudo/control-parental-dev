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
        private val localDataResetter: AdminLocalDataResetter,
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
                    } else {
                        _uiState.update {
                            it.copy(
                                activated = false,
                                loading = false,
                                showResetConfirmation = false,
                                message = "",
                            )
                        }
                    }
                }
            }
        }

        fun onActivationCodeChanged(value: String) = update { copy(activationCode = value) }

        fun onEmailChanged(value: String) = update { copy(email = value) }

        fun onPasswordChanged(value: String) = update { copy(password = value) }

        fun onConfirmPasswordChanged(value: String) = update { copy(confirmPassword = value) }

        fun requestResetLocalAdmin() = update { copy(showResetConfirmation = true) }

        fun dismissResetLocalAdmin() = update { copy(showResetConfirmation = false) }

        fun resetLocalAdmin() {
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        loading = true,
                        showResetConfirmation = false,
                        message = "Reseteando datos locales",
                    )
                }
                runCatching {
                    realtimeSyncCoordinator.stop()
                    localDataResetter.resetForNewAdminToken()
                }.onFailure { exception ->
                    Log.e(LogTag, "Local admin reset failed: ${exception.message}", exception)
                    _uiState.update {
                        it.copy(
                            loading = false,
                            message = "No se pudo resetear esta app. Cerrala y volvé a intentar.",
                        )
                    }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        activationCode = "",
                        email = "",
                        password = "",
                        confirmPassword = "",
                        activated = false,
                        loading = false,
                        message = "Listo. Ingresá el nuevo token de administrador.",
                    )
                }
            }
        }

        fun activate() {
            val state = _uiState.value
            if (state.activated) {
                _uiState.update { it.copy(message = "Administrador activado. No hace falta volver a iniciar sesión.") }
                return
            }
            if (state.offlineMode) {
                _uiState.update { it.copy(message = "Sin conexion. Mostrando datos guardados.") }
                return
            }
            val token = PairingToken.from(state.activationCode)
            val displayName = token.displayName ?: "Administrador"
            if (token.code.isBlank()) {
                val reason = "Local validation failed: admin activation token is blank."
                Log.w(LogTag, reason)
                _uiState.update {
                    it.copy(message = activationFailureMessage("Ingresá el token de administrador."))
                }
                return
            }
            val email = state.email.trim().lowercase()
            if (!email.contains('@') || !email.contains('.')) {
                _uiState.update { it.copy(message = activationFailureMessage("Ingresá el email del administrador.")) }
                return
            }
            if (state.password.length < 8) {
                _uiState.update { it.copy(message = activationFailureMessage("La contraseña debe tener al menos 8 caracteres.")) }
                return
            }
            if (state.password != state.confirmPassword) {
                _uiState.update { it.copy(message = activationFailureMessage("Las contraseñas no coinciden.")) }
                return
            }
            viewModelScope.launch {
                _uiState.update { it.copy(loading = true, message = "Activando") }
                val result =
                    runCatching {
                        activationRepository.activate(
                            ActivationCredentials(
                                email = email,
                                password = state.password,
                                activationCode = token.code,
                                deviceDisplayName = displayName,
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
            if (detail.startsWith("Ingresá")) return detail
            if (detail.startsWith("La contraseña")) return detail
            if (detail.startsWith("Las contraseñas")) return detail
            if (detail.startsWith("Ese email")) return detail
            if (detail.startsWith("Email")) return detail
            if (detail.startsWith("Este administrador")) return detail
            if (detail.startsWith("Token de administrador")) return detail
            if (detail.startsWith("La licencia")) return detail
            return "No se pudo activar. Revisá el token o pedí uno nuevo."
        }

        private data class PairingToken(
            val code: String,
            val displayName: String?,
        ) {
            companion object {
                fun from(rawValue: String): PairingToken {
                    val trimmed = rawValue.trim()
                    val separator = trimmed.lastIndexOf('-')
                    if (separator <= 0 || separator >= trimmed.lastIndex - 1) {
                        return PairingToken(code = trimmed, displayName = null)
                    }
                    return PairingToken(
                        code = trimmed.substring(separator + 1).trim(),
                        displayName =
                            trimmed
                                .substring(0, separator)
                                .replace('-', ' ')
                                .trim()
                                .takeIf { it.isNotBlank() },
                    )
                }
            }
        }

        private companion object {
            const val AdminRole = "admin"
            const val LogTag = "AdminAuthViewModel"
            const val OfflineMessage = "Sin conexión. Mostrando datos guardados."
        }
    }
