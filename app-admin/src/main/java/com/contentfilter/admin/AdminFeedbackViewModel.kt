package com.contentfilter.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.domain.repository.AppFeedbackRepository
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminFeedbackViewModel
    @Inject
    constructor(
        private val activationRepository: DeviceActivationRepository,
        private val feedbackRepository: AppFeedbackRepository,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(AdminFeedbackUiState())
        val state = mutableState.asStateFlow()

        fun submitRating(
            stars: Int,
            comment: String,
        ) = runAction("Gracias. Tu valoración fue enviada.") { deviceId ->
            feedbackRepository.submitRating(deviceId, stars, comment, BuildConfig.VERSION_CODE)
        }

        fun savePhone(phone: String) =
            runAction("Contacto actualizado.") { deviceId ->
                feedbackRepository.updateAdminPhone(deviceId, phone)
            }

        private fun runAction(
            successMessage: String,
            action: suspend (String) -> Result<Unit>,
        ) {
            if (mutableState.value.saving) return
            mutableState.value = mutableState.value.copy(saving = true, message = "")
            viewModelScope.launch {
                val deviceId = activationRepository.currentActivation()?.deviceId
                val result = if (deviceId == null) Result.failure(IllegalStateException()) else action(deviceId)
                mutableState.value =
                    AdminFeedbackUiState(
                        saving = false,
                        message = if (result.isSuccess) successMessage else "No se pudo guardar. Revisá los datos e intentá nuevamente.",
                    )
            }
        }
    }

data class AdminFeedbackUiState(
    val saving: Boolean = false,
    val message: String = "",
)
