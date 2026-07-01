package com.contentfilter.feature.activation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.domain.model.ActivationCredentials
import com.contentfilter.core.domain.model.ActivationResult
import com.contentfilter.core.domain.repository.ActivationRepository
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.sync.SyncScheduler
import com.contentfilter.core.sync.realtime.RealtimeSyncCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ActivationViewModel
    @Inject
    constructor(
        private val activationRepository: ActivationRepository,
        private val deviceActivationRepository: DeviceActivationRepository,
        private val syncScheduler: SyncScheduler,
        private val realtimeSyncCoordinator: RealtimeSyncCoordinator,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(ActivationUiState())
        val uiState: StateFlow<ActivationUiState> = mutableState.asStateFlow()

        init {
            viewModelScope.launch {
                deviceActivationRepository.observeActivation().collect { activation ->
                    if (activation != null) {
                        mutableState.update {
                            it.copy(
                                activated = true,
                                isLoading = false,
                                message = "Dispositivo activado. No hace falta volver a iniciar sesión.",
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
            val state = mutableState.value
            if (state.activated) {
                mutableState.update { it.copy(message = "Dispositivo activado. No hace falta volver a iniciar sesión.") }
                return
            }
            if (state.email.isBlank() || state.password.isBlank() || state.activationCode.isBlank()) {
                val reason = "Local validation failed: email, password or activation code is blank."
                Log.w(LogTag, reason)
                mutableState.update { it.copy(message = activationFailureMessage("Completá email, password y código.")) }
                return
            }
            viewModelScope.launch {
                mutableState.update { it.copy(isLoading = true, message = "Activando dispositivo...") }
                val result = runCatching {
                    activationRepository.activate(
                        ActivationCredentials(
                            email = state.email.trim(),
                            password = state.password,
                            activationCode = state.activationCode.trim(),
                            deviceDisplayName = state.deviceName.ifBlank { "Android" },
                            appVersionCode = 1,
                        ),
                    )
                }.getOrElse { exception ->
                    val message = exception.message?.takeIf { it.isNotBlank() } ?: exception.toString()
                    Log.e(LogTag, "Unexpected activation exception: ${exception.javaClass.name}: $message", exception)
                    ActivationResult.Failed("Unexpected activation exception: ${exception.javaClass.simpleName}: $message")
                }
                if (result is ActivationResult.Activated) {
                    realtimeSyncCoordinator.stop()
                    realtimeSyncCoordinator.start()
                    syncScheduler.requestSync()
                } else if (result is ActivationResult.Failed) {
                    Log.e(LogTag, "Activation failed. Reason: ${result.reason.ifBlank { "<blank reason>" }}")
                }
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        message = when (result) {
                            is ActivationResult.Activated -> "Dispositivo activado."
                            is ActivationResult.Failed -> activationFailureMessage(result.reason)
                        },
                    )
                }
            }
        }

        private fun update(block: ActivationUiState.() -> ActivationUiState) {
            mutableState.update { it.block() }
        }

        private fun activationFailureMessage(reason: String): String {
            val detail = reason.ifBlank { "No se recibió detalle técnico del error." }
            if (detail == OfflineMessage) return OfflineMessage
            return "No se pudo activar. Revisá email, password y código."
        }

        private companion object {
            const val LogTag = "ActivationViewModel"
            const val OfflineMessage = "Sin conexión. Mostrando datos guardados."
        }
    }
