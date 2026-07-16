package com.contentfilter.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.domain.model.Device
import com.contentfilter.core.domain.model.DeviceActivation
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.domain.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class UserHomeViewModel
    @Inject
    constructor(
        activationRepository: DeviceActivationRepository,
        deviceRepository: DeviceRepository,
    ) : ViewModel() {
        val uiState: StateFlow<UserHomeUiState> =
            combine(
                activationRepository.observeActivation(),
                deviceRepository.observeDevices(),
            ) { activation, devices ->
                UserHomeUiState(greeting = resolveUserGreeting(activation, devices))
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = UserHomeUiState(),
            )
    }

data class UserHomeUiState(
    val greeting: String = DefaultUserGreeting,
)

internal fun resolveUserGreeting(
    activation: DeviceActivation?,
    devices: List<Device>,
): String {
    val activeDeviceName =
        activation
            ?.let { current -> devices.firstOrNull { it.id == current.deviceId } }
            ?.displayName
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    return activeDeviceName?.let { "Hola, $it" } ?: DefaultUserGreeting
}

private const val DefaultUserGreeting = "Hola"
