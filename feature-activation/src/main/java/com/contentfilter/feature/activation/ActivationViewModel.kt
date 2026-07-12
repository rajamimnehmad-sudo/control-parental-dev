package com.contentfilter.feature.activation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.domain.model.ActivationCredentials
import com.contentfilter.core.domain.model.ActivationResult
import com.contentfilter.core.domain.repository.ActivationRepository
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.sync.SyncScheduler
import com.contentfilter.core.sync.engine.TargetedPolicySyncCoordinator
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
        private val targetedPolicySyncCoordinator: TargetedPolicySyncCoordinator,
        private val realtimeSyncCoordinator: RealtimeSyncCoordinator,
        private val installedAppVersionProvider: InstalledAppVersionProvider,
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

        fun onActivationCodeChanged(value: String) =
            update {
                val token = PairingToken.from(value)
                copy(
                    activationCode = value,
                    deviceName = token.displayName.orEmpty(),
                )
            }

        fun activate() {
            val state = mutableState.value
            if (state.activated && state.activationCode.isBlank()) {
                mutableState.update { it.copy(message = "Dispositivo enlazado. No hace falta volver a enlazar.") }
                return
            }
            val token = PairingToken.from(state.activationCode)
            val deviceDisplayName = token.displayName ?: DefaultDeviceDisplayName
            if (token.code.isBlank()) {
                val reason = "Local validation failed: device name or activation code is blank."
                Log.w(LogTag, reason)
                mutableState.update { it.copy(message = "Ingresá el token del administrador.") }
                return
            }
            viewModelScope.launch {
                val oldActivation = deviceActivationRepository.currentActivation()
                Log.i(
                    LogTag,
                    "Activation requested. rawInput=${state.activationCode.maskForLog()} normalizedCode=${token.code.maskForLog()} tokenMode=${token.mode.logName} oldDeviceId=${oldActivation?.deviceId} localActivated=${oldActivation != null}",
                )
                mutableState.update { it.copy(isLoading = true, message = "Enlazando dispositivo...") }
                val result =
                    runCatching {
                        activationRepository.activate(
                            ActivationCredentials(
                                email = "",
                                password = "",
                                activationCode = token.code,
                                deviceDisplayName = deviceDisplayName,
                                appVersionCode = installedAppVersionProvider.versionCode(),
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
                    val savedActivation = deviceActivationRepository.currentActivation()
                    Log.i(
                        LogTag,
                        "Activation success. oldDeviceId=${oldActivation?.deviceId} newDeviceId=${result.activation.deviceId} localDeviceSaved=${savedActivation?.deviceId == result.activation.deviceId} accountId=${result.activation.accountId}",
                    )
                    realtimeSyncCoordinator.stop()
                    realtimeSyncCoordinator.start()
                    syncScheduler.requestSync()
                    viewModelScope.launch(Dispatchers.IO) {
                        val syncResult =
                            runCatching {
                                targetedPolicySyncCoordinator.refresh(
                                    deviceId = result.activation.deviceId,
                                    reason = "activation-view-model",
                                )
                            }
                        Log.i(
                            LogTag,
                            "Initial sync after activation. newDeviceId=${result.activation.deviceId} success=${syncResult.isSuccess} error=${syncResult.exceptionOrNull()?.javaClass?.simpleName}",
                        )
                    }
                } else if (result is ActivationResult.Failed) {
                    Log.e(LogTag, "Activation failed. rpcResult=failure rpcErrorCode=${result.reason.ifBlank { "<blank reason>" }}")
                }
                mutableState.update {
                    val navigationTarget =
                        when (result) {
                            is ActivationResult.Activated -> "home"
                            is ActivationResult.Failed -> "activation"
                        }
                    Log.i(LogTag, "Activation finished. navigationTarget=$navigationTarget")
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
            val normalized = detail.lowercase()
            if ("already used" in normalized) return "Ese código ya fue usado. Pedí uno nuevo al administrador."
            if ("expired" in normalized) return "Ese código venció. Pedí uno nuevo al administrador."
            if ("not found" in normalized || "invalid pairing code" in normalized) {
                return "Código inválido. Revisá el código o pedí uno nuevo."
            }
            if ("license" in normalized) return "No se pudo enlazar por la licencia de la comunidad."
            if ("role mismatch" in normalized) return "Ese código no corresponde a esta app."
            return "No se pudo enlazar. Revisá el código o pedí uno nuevo al administrador."
        }

        private data class PairingToken(
            val code: String,
            val displayName: String?,
            val mode: TokenMode,
        ) {
            companion object {
                fun from(rawValue: String): PairingToken {
                    val trimmed = rawValue.trim()
                    val directCode = trimmed.normalizedPairingCodeOrNull()
                    if (directCode != null) return PairingToken(code = directCode, displayName = null, mode = TokenMode.Plain)

                    val parts = trimmed.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotBlank() }
                    val code = parts.lastOrNull { it.normalizedPairingCodeOrNull() != null }
                        ?.normalizedPairingCodeOrNull()
                        .orEmpty()
                    if (code.isBlank()) return PairingToken(code = "", displayName = null, mode = TokenMode.PastedText)

                    val name =
                        parts
                            .takeWhile { it.normalizedPairingCodeOrNull() == null }
                            .joinToString(" ")
                            .trim()
                            .takeIf { it.isNotBlank() }
                    return PairingToken(
                        code = code,
                        displayName = name,
                        mode = if (name == null) TokenMode.PastedText else TokenMode.NameCode,
                    )
                }

                private fun String.normalizedPairingCodeOrNull(): String? {
                    val normalized = trim().uppercase()
                    return if (normalized.matches(UserPairingCodeRegex)) {
                        normalized
                    } else {
                        null
                    }
                }

                private val UserPairingCodeRegex = Regex("[A-Z0-9]{6}")
            }
        }

        private enum class TokenMode(val logName: String) {
            Plain("plain"),
            NameCode("name-code"),
            PastedText("pasted-text"),
        }

        private companion object {
            const val LogTag = "ActivationViewModel"
            const val OfflineMessage = "Sin conexión. Mostrando datos guardados."
            const val DefaultDeviceDisplayName = "Dispositivo protegido"

            fun String.maskForLog(): String =
                if (length <= 4) "****" else "${take(2)}***${takeLast(2)}"
        }
    }
