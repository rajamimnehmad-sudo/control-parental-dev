package com.contentfilter.user.protection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.domain.model.ProtectionAlertType
import com.contentfilter.core.domain.model.ProtectionAuthorizationScope
import com.contentfilter.core.domain.model.RecoveryUnlockResult
import com.contentfilter.core.domain.repository.ProtectionStateStore
import com.contentfilter.core.domain.repository.PushNotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class ProtectionViewModel
    @Inject
    constructor(
        private val coordinator: ProtectionControlCoordinator,
        private val stateStore: ProtectionStateStore,
        private val pushNotificationRepository: PushNotificationRepository,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(ProtectionUiState())
        val uiState: StateFlow<ProtectionUiState> = mutableState.asStateFlow()

        init {
            refresh()
            viewModelScope.launch(Dispatchers.IO) {
                while (true) {
                    delay(30_000)
                    updateFromStore()
                }
            }
        }

        fun refresh() {
            viewModelScope.launch(Dispatchers.IO) { refreshInternal(showProgress = true) }
        }

        fun onRecoveryCodeChanged(value: String) {
            mutableState.update { it.copy(recoveryCode = value.take(24), message = "") }
        }

        fun submitRecoveryCode() {
            val result = stateStore.verifyAndConsumeRecovery(mutableState.value.recoveryCode)
            val message =
                when (result) {
                    is RecoveryUnlockResult.Unlocked ->
                        "Código aceptado. Retiro autorizado hasta ${result.validUntilEpochMillis.timeLabel()}."
                    is RecoveryUnlockResult.Invalid ->
                        "Código incorrecto. Quedan ${result.attemptsRemaining} intentos."
                    is RecoveryUnlockResult.Locked ->
                        "Demasiados intentos. Probá después de ${result.retryAtEpochMillis.timeLabel()}."
                    RecoveryUnlockResult.Unavailable -> "No hay un código de recuperación disponible."
                }
            updateFromStore(message = message, clearCode = result is RecoveryUnlockResult.Unlocked)
            if (result is RecoveryUnlockResult.Unlocked) {
                viewModelScope.launch(Dispatchers.IO) { coordinator.refresh() }
            }
        }

        fun requestMaintenance() {
            viewModelScope.launch(Dispatchers.IO) {
                pushNotificationRepository.reportProtectionAlert(ProtectionAlertType.MaintenanceRequested)
                mutableState.update { it.copy(message = "Pedido enviado al administrador.") }
            }
        }

        fun cancelRemovalAuthorization() {
            stateStore.cancelLocalRemovalAuthorization()
            updateFromStore(message = "Autorización cancelada. La barrera volvió a quedar activa.")
        }

        private suspend fun refreshInternal(showProgress: Boolean) {
            if (showProgress) mutableState.update { it.copy(refreshing = true) }
            val result = coordinator.refresh()
            updateFromStore(
                message = if (result.isFailure && showProgress) "No se pudo actualizar. Se mantiene el estado local seguro." else null,
            )
        }

        private fun updateFromStore(
            message: String? = null,
            clearCode: Boolean = false,
        ) {
            val control = stateStore.currentControl()
            mutableState.update { previous ->
                previous.copy(
                    armed = control?.armed == true,
                    remoteSettingsAuthorized =
                        stateStore.isAuthorized(ProtectionAuthorizationScope.Settings),
                    removalAuthorized =
                        stateStore.isAuthorized(ProtectionAuthorizationScope.Removal),
                    recoveryAvailable = control?.hasAvailableRecovery == true,
                    appliedRevision = control?.appliedRevision ?: 0,
                    commandRevision = control?.commandRevision ?: 0,
                    refreshing = false,
                    recoveryCode = if (clearCode) "" else previous.recoveryCode,
                    message = message ?: previous.message,
                )
            }
        }

        private fun Long.timeLabel(): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(this))
    }

data class ProtectionUiState(
    val armed: Boolean = false,
    val remoteSettingsAuthorized: Boolean = false,
    val removalAuthorized: Boolean = false,
    val recoveryAvailable: Boolean = false,
    val appliedRevision: Long = 0,
    val commandRevision: Long = 0,
    val recoveryCode: String = "",
    val refreshing: Boolean = false,
    val message: String = "",
)
