package com.contentfilter.user.protection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.domain.model.ProtectionAuthorizationScope
import com.contentfilter.core.domain.model.RecoveryUnlockResult
import com.contentfilter.core.domain.repository.ProtectionControlRepository
import com.contentfilter.core.domain.repository.ProtectionStateStore
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
        private val remoteRepository: ProtectionControlRepository,
        private val stateStore: ProtectionStateStore,
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

        fun cancelRemovalAuthorization() {
            stateStore.cancelLocalRemovalAuthorization()
            mutableState.update {
                it.copy(
                    removalAuthorized = false,
                    message = "Cancelando autorización...",
                )
            }
            viewModelScope.launch(Dispatchers.IO) {
                val result = remoteRepository.cancelOwnRemovalAuthorization()
                coordinator.refresh()
                updateFromStore(
                    message =
                        if (result.isSuccess) {
                            "Autorización cancelada. La barrera volvió a quedar activa."
                        } else {
                            "No se pudo cancelar la autorización remota."
                        },
                )
            }
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
            mutableState.update { previous ->
                previous.copy(
                    removalAuthorized =
                        stateStore.isAuthorized(ProtectionAuthorizationScope.Removal),
                    refreshing = false,
                    recoveryCode = if (clearCode) "" else previous.recoveryCode,
                    message = message ?: previous.message,
                )
            }
        }

        private fun Long.timeLabel(): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(this))
    }

data class ProtectionUiState(
    val removalAuthorized: Boolean = false,
    val recoveryCode: String = "",
    val refreshing: Boolean = false,
    val message: String = "",
)
