package com.contentfilter.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.user.repair.UserLocalDataRepair
import dagger.hilt.android.lifecycle.HiltViewModel
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
                runCatching { localDataRepair.repairIfNeeded() }
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
                        )
                    }
                }
            }
        }
    }

data class UserRootUiState(
    val checkingActivation: Boolean = true,
    val needsActivation: Boolean = true,
)
