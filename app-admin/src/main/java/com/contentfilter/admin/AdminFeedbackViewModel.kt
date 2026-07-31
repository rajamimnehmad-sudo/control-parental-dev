package com.contentfilter.admin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.domain.repository.AppFeedbackRepository
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        private val mutableState =
            MutableStateFlow(
                AdminFeedbackUiState(
                    ratingAvailableAtEpochMillis = preferences.getLong(RatingAvailableAtKey, 0L),
                ),
            )
        val state = mutableState.asStateFlow()

        init {
            loadContact()
            loadRatingAvailability()
        }

        fun submitRating(
            stars: Int,
            comment: String,
        ) {
            if (stars !in 1..5 || mutableState.value.saving) return
            val now = System.currentTimeMillis()
            if (mutableState.value.ratingAvailableAtEpochMillis > now) {
                mutableState.value = mutableState.value.copy(message = RatingCooldownMessage)
                return
            }
            mutableState.value = mutableState.value.copy(saving = true, message = "")
            viewModelScope.launch {
                val deviceId = activationRepository.currentActivation()?.deviceId
                val result =
                    if (deviceId == null) Result.failure(IllegalStateException())
                    else feedbackRepository.submitRating(deviceId, stars, comment, BuildConfig.VERSION_CODE)
                if (result.isSuccess) {
                    val availableAt = System.currentTimeMillis() + RatingCooldownMillis
                    preferences.edit().putLong(RatingAvailableAtKey, availableAt).apply()
                    mutableState.value = mutableState.value.copy(
                        saving = false,
                        message = "Gracias. Tu valoración fue enviada.",
                        ratingAvailableAtEpochMillis = availableAt,
                    )
                } else {
                    mutableState.value = mutableState.value.copy(
                        saving = false,
                        message = ratingFailureMessage(result),
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
        ) = runAction("Datos actualizados.") { deviceId ->
            feedbackRepository.updateAdminContact(deviceId, contactEmail.trim(), phone.trim())
        }

        fun savePhone(phone: String) = saveContact(mutableState.value.contactEmail, phone)

        private fun loadContact() {
            viewModelScope.launch {
                val deviceId = activationRepository.currentActivation()?.deviceId ?: return@launch
                feedbackRepository.getAdminContact(deviceId).onSuccess { contact ->
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

        fun clearMessage() {
            mutableState.value = mutableState.value.copy(message = "")
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
                mutableState.value = mutableState.value.copy(
                    saving = false,
                    message = if (result.isSuccess) successMessage else contactFailureMessage(result),
                )
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
                else -> "No se pudo guardar. Revisá los datos e intentá nuevamente."
            }
        }

        private companion object {
            const val PreferencesName = "admin-feedback"
            const val RatingAvailableAtKey = "rating-available-at"
            const val RatingCooldownMillis = 7L * 24L * 60L * 60L * 1000L
            const val RatingCooldownMessage = "Ya valoraste esta app. Podés volver a hacerlo en 7 días."
        }
    }

data class AdminFeedbackUiState(
    val saving: Boolean = false,
    val message: String = "",
    val contactEmail: String = "",
    val phoneE164: String = "",
    val contactLoaded: Boolean = false,
    val ratingAvailableAtEpochMillis: Long = 0L,
)
