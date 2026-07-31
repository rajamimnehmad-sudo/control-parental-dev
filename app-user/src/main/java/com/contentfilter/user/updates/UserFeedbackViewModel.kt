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

        init {
            loadContact()
            loadRatingAvailability()
        }

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
                        message = ratingFailureMessage(result),
                        sent = false,
                    )
                    if (result.exceptionOrNull()?.message?.contains("7 days", ignoreCase = true) == true) {
                        loadRatingAvailability()
                    }
                }
            }
        }

        fun saveContact(
            contactEmail: String,
            phone: String,
        ) {
            if (mutableState.value.saving) return
            mutableState.value = mutableState.value.copy(saving = true, message = "")
            viewModelScope.launch {
                val deviceId = activationRepository.currentActivation()?.deviceId
                val result =
                    if (deviceId == null) Result.failure(IllegalStateException())
                    else feedbackRepository.updateUserContact(deviceId, contactEmail.trim(), phone.trim())
                mutableState.value = mutableState.value.copy(
                    saving = false,
                    message = if (result.isSuccess) "Datos de contacto guardados." else contactFailureMessage(result),
                )
            }
        }

        private fun loadContact() {
            viewModelScope.launch {
                val deviceId = activationRepository.currentActivation()?.deviceId ?: return@launch
                feedbackRepository.getUserContact(deviceId).onSuccess { contact ->
                    mutableState.value = mutableState.value.copy(
                        contactEmail = contact.contactEmail,
                        phoneE164 = contact.phoneE164,
                        contactLoaded = true,
                    )
                }.onFailure {
                    mutableState.value = mutableState.value.copy(contactLoaded = true)
                }
            }
        }

        private fun loadRatingAvailability() {
            viewModelScope.launch {
                val deviceId = activationRepository.currentActivation()?.deviceId ?: return@launch
                feedbackRepository.getRatingAvailability(deviceId).onSuccess { availability ->
                    mutableState.value = mutableState.value.copy(
                        ratingAvailableAtEpochMillis = availability.nextAvailableAtEpochMillis ?: 0L,
                    )
                }
            }
        }

        private fun ratingFailureMessage(result: Result<Unit>): String {
            val reason = result.exceptionOrNull()?.message.orEmpty()
            return if (reason.contains("7 days", ignoreCase = true)) {
                "Ya valoraste esta app. La próxima valoración se habilita cuando pasen 7 días."
            } else {
                "No se pudo enviar la valoración. Intentá nuevamente."
            }
        }

        private fun contactFailureMessage(result: Result<Unit>): String {
            val reason = result.exceptionOrNull()?.message.orEmpty()
            return when {
                reason.contains("Email de contacto", ignoreCase = true) ->
                    "El mail no tiene un formato válido. Ejemplo: nombre@dominio.com."
                reason.contains("Phone must", ignoreCase = true) ->
                    "El celular debe incluir código de país. Ejemplo: +5491123456789."
                else -> "No se pudieron guardar los datos. Revisá los campos e intentá nuevamente."
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
    val contactEmail: String = "",
    val phoneE164: String = "",
    val contactLoaded: Boolean = false,
)
