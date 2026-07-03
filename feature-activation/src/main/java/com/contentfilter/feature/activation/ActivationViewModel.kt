package com.contentfilter.feature.activation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.domain.model.ActivationCredentials
import com.contentfilter.core.domain.model.ActivationResult
import com.contentfilter.core.domain.repository.ActivationRepository
import com.contentfilter.core.domain.repository.DeviceActivationRepository
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
class ActivationViewModel
    @Inject
    constructor(
        private val activationRepository: ActivationRepository,
        private val deviceActivationRepository: DeviceActivationRepository,
        private val syncScheduler: SyncScheduler,
        private val syncEngine: SyncEngine,
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

        fun onActivationCodeChanged(value: String) = update { copy(activationCode = value) }

        fun onDeviceNameChanged(value: String) = update { copy(deviceName = value) }

        fun activate() {
            val state = mutableState.value
            if (state.activated) {
                mutableState.update { it.copy(message = "Dispositivo enlazado. No hace falta volver a enlazar.") }
                return
            }
            if (state.deviceName.isBlank() || state.activationCode.isBlank()) {
                val reason = "Local validation failed: device name or activation code is blank."
                Log.w(LogTag, reason)
                mutableState.update { it.copy(message = "Completá nombre y código.") }
                return
            }
            viewModelScope.launch {
                mutableState.update { it.copy(isLoading = true, message = "Enlazando dispositivo...") }
                val result =
                    runCatching {
                        activationRepository.activate(
                            ActivationCredentials(
                                email = "",
                                password = "",
                                activationCode = state.activationCode.trim(),
                                deviceDisplayName = state.deviceName.ifBlank { "Android" },
                                appVersionCode = 1,
                            ),
                        )
                    }.getOrElse { exception ->
                        val message = exception.message?.takeIf { it.isNotBlank() } ?: exception.toString()
                        Log.e(
                            LogTag,
                            "Unexpected activation exception: ${exception.javaClass.name}: $message",
                            exception,
                        )
                        ActivationResult.Failed(
                            "Unexpected activation exception: ${exception.javaClass.simpleName}: $message",
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
                    Log.e(LogTag, "Activation failed. Reason: ${result.reason.ifBlank { "<blank reason>" }}")
                }
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        message =
                            when (result) {
                                is ActivationResult.Activated -> "Dispositivo enlazado."
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
            return "No se pudo enlazar. Revisá el código o pedí uno nuevo al administrador."
        }

        private companion object {
            const val LogTag = "ActivationViewModel"
            const val OfflineMessage = "Sin conexión. Mostrando datos guardados."
        }
    }
