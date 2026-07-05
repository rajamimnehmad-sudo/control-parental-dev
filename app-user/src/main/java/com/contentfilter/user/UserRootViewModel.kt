package com.contentfilter.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.user.repair.RepairResult
import com.contentfilter.user.repair.UserLocalDataRepair
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserRootViewModel
    @Inject
    constructor(
        private val activationRepository: DeviceActivationRepository,
        private val localDataRepair: UserLocalDataRepair,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(UserRootUiState())
        val uiState: StateFlow<UserRootUiState> = mutableState.asStateFlow()

        init {
            viewModelScope.launch {
                runCatching { validateLocalLicense() }
                    .onFailure {
                        mutableState.update { state ->
                            state.copy(checkingActivation = false, needsActivation = true)
                        }
                    }
                activationRepository.observeActivation().collect { activation ->
                    mutableState.update {
                        it.copy(
                            checkingActivation = false,
                            needsActivation = activation == null,
                            activationNotice =
                                if (activation == null && localDataRepair.hasRevokedLicenseNotice()) {
                                    RevokedLicenseMessage
                                } else if (activation != null) {
                                    ""
                                } else {
                                    it.activationNotice
                                },
                        )
                    }
                }
            }
            viewModelScope.launch {
                while (true) {
                    delay(DeviceLicenseValidationIntervalMillis)
                    runCatching { validateLocalLicense() }
                }
            }
        }

        private suspend fun validateLocalLicense() {
            val hadActivation = activationRepository.currentActivation() != null
            val result = localDataRepair.repairIfNeeded()
            if ((hadActivation && result is RepairResult.NeedsActivation) || localDataRepair.hasRevokedLicenseNotice()) {
                mutableState.update {
                    it.copy(
                        checkingActivation = false,
                        needsActivation = true,
                        activationNotice = RevokedLicenseMessage,
                    )
                }
            }
        }

        private companion object {
            const val DeviceLicenseValidationIntervalMillis = 30_000L
            const val RevokedLicenseMessage =
                "Este dispositivo ya no tiene licencia. Pedí un nuevo código al administrador."
        }
    }

data class UserRootUiState(
    val checkingActivation: Boolean = true,
    val needsActivation: Boolean = true,
    val activationNotice: String = "",
)
