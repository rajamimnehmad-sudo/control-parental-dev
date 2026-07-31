package com.contentfilter.user.updates

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.domain.repository.AppFeedbackRepository
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.user.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        private val mutableState = MutableStateFlow(
            FeedbackUiState(
                ratingAvailableAtEpochMillis = preferences.getLong(RatingAvailableAtKey, 0L),
            ),
        )
        val state = mutableState.asStateFlow()

        fun submit(
            stars: Int,
            comment: String,
        ) {
            if (stars !in 1..5 || mutableState.value.saving) return
            if (mutableState.value.ratingAvailableAtEpochMillis > System.currentTimeMillis()) {
                mutableState.value = mutableState.value.copy(message = RatingCooldownMessage)
                return
            }
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
                if (result.isSuccess) {
                    val availableAt = System.currentTimeMillis() + RatingCooldownMillis
                    preferences.edit().putLong(RatingAvailableAtKey, availableAt).apply()
                    mutableState.value = mutableState.value.copy(
                        saving = false,
                        message = "Gracias. Tu valoración fue enviada.",
                        sent = true,
                        ratingAvailableAtEpochMillis = availableAt,
                    )
                } else {
                    mutableState.value = mutableState.value.copy(
                        saving = false,
                        message = "No se pudo enviar. Intentá nuevamente.",
                        sent = false,
                    )
                }
            }
        }

        private companion object {
            const val PreferencesName = "user-feedback"
            const val RatingAvailableAtKey = "rating-available-at"
            const val RatingCooldownMillis = 7L * 24L * 60L * 60L * 1000L
            const val RatingCooldownMessage = "Ya valoraste esta app. Podés volver a hacerlo en 7 días."
        }
    }

data class FeedbackUiState(
    val saving: Boolean = false,
    val message: String = "",
    val sent: Boolean = false,
    val ratingAvailableAtEpochMillis: Long = 0L,
)
