package com.contentfilter.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminRootViewModel
    @Inject
    constructor(
        activationRepository: DeviceActivationRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(AdminRootUiState())
        val uiState: StateFlow<AdminRootUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                activationRepository.observeActivation().collect { activation ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            activated = activation != null,
                        )
                    }
                }
            }
        }
    }

data class AdminRootUiState(
    val loading: Boolean = true,
    val activated: Boolean = false,
)
