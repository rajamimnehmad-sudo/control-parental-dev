package com.contentfilter.user.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.domain.repository.AppFeedbackRepository
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.user.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserFeedbackViewModel
    @Inject
    constructor(
        private val activationRepository: DeviceActivationRepository,
        private val feedbackRepository: AppFeedbackRepository,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(FeedbackUiState())
        val state = mutableState.asStateFlow()

        fun submit(
            stars: Int,
            comment: String,
        ) {
            if (stars !in 1..5 || mutableState.value.saving) return
            mutableState.value = mutableState.value.copy(saving = true, message = "")
            viewModelScope.launch {
                val activation = activationRepository.currentActivation()
                val result =
                    if (activation == null) {
                        Result.failure(IllegalStateException())
                    } else {
                        feedbackRepository.submitRating(
                            activation.deviceId,
                            stars,
                            comment,
                            BuildConfig.VERSION_CODE,
                        )
                    }
                mutableState.value =
                    FeedbackUiState(
                        saving = false,
                        message = if (result.isSuccess) "Gracias. Tu valoración fue enviada." else "No se pudo enviar. Intentá nuevamente.",
                        sent = result.isSuccess,
                    )
            }
        }
    }

data class FeedbackUiState(
    val saving: Boolean = false,
    val message: String = "",
    val sent: Boolean = false,
)
