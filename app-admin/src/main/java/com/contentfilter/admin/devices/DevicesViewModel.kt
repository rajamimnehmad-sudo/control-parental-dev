package com.contentfilter.admin.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.admin.BuildConfig
import com.contentfilter.core.domain.model.Device
import com.contentfilter.core.domain.model.DeviceActivation
import com.contentfilter.core.domain.model.SystemHealthSnapshot
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.domain.repository.DeviceRepository
import com.contentfilter.core.domain.repository.SystemStatusRepository
import com.contentfilter.core.domain.usecase.admin.ObserveDevicesUseCase
import com.contentfilter.core.network.remote.RemoteResult
import com.contentfilter.core.network.remote.SupabaseActivationClient
import com.contentfilter.core.sync.SyncScheduler
import com.contentfilter.core.sync.engine.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DevicesViewModel
    @Inject
    constructor(
        observeDevices: ObserveDevicesUseCase,
        activationRepository: DeviceActivationRepository,
        systemStatusRepository: SystemStatusRepository,
        private val deviceRepository: DeviceRepository,
        private val activationClient: SupabaseActivationClient,
        private val syncScheduler: SyncScheduler,
        private val syncEngine: SyncEngine,
    ) : ViewModel() {
        private val syncMessage = MutableStateFlow("Sincronizando dispositivos...")
        private val pairingCode = MutableStateFlow("")
        private val pairingExpiresAt = MutableStateFlow("")
        private val loading = MutableStateFlow(false)
        private val deviceState = combine(
            observeDevices(),
            activationRepository.observeActivation(),
            systemStatusRepository.observeHealth(),
        ) { devices, activation, health ->
            DeviceState(devices, activation, health)
        }
        private val localState = combine(
            syncMessage,
            pairingCode,
            pairingExpiresAt,
            loading,
        ) { message, code, expiresAt, isLoading ->
            LocalState(message, code, expiresAt, isLoading)
        }

        val uiState = combine(
            deviceState,
            localState,
        ) { deviceState, local ->
            val items = deviceState.devices.filter { it.appRole != "admin" }.map { device ->
                val isCurrent = deviceState.activation?.deviceId == device.id
                AdminDeviceItem(
                    id = device.id,
                    name = device.displayName,
                    user = "Usuario",
                    version = if (isCurrent) BuildConfig.VERSION_NAME else "Sin datos",
                    vpnState = if (isCurrent) deviceState.health.vpnState.name else "Sin datos",
                    accessibilityState = if (isCurrent) deviceState.health.accessibilityState.name else "Sin datos",
                    lastSync = if (isCurrent) deviceState.health.checkedAtEpochMillis.toDisplayDate() else "Sin datos",
                    systemState = if (isCurrent) deviceState.health.protectionLevel.name else "Sin datos",
                )
            }
            DevicesUiState(
                devices = items,
                pairingCode = local.pairingCode,
                pairingExpiresAt = local.pairingExpiresAt,
                loading = local.loading,
                offlineMode = false,
                message = local.message,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DevicesUiState(offlineMode = false, message = "Sincronizando dispositivos..."),
        )

        init {
            syncScheduler.requestSync()
            viewModelScope.launch(Dispatchers.IO) {
                val result = syncEngine.syncDevicesFull()
                syncMessage.update { result.message }
                syncEngine.syncCoreDataFull()
            }
        }

        fun generatePairingCode() {
            viewModelScope.launch {
                loading.value = true
                syncMessage.value = "Generando código..."
                when (val result = activationClient.createDevicePairingCode()) {
                    is RemoteResult.Success -> {
                        pairingCode.value = result.value.code
                        pairingExpiresAt.value = result.value.expiresAt
                        syncMessage.value = "Código listo. Pasalo a la App Usuario."
                    }
                    is RemoteResult.Failure -> {
                        syncMessage.value = "No se pudo generar código."
                    }
                }
                loading.value = false
            }
        }

        fun revokeDevice(deviceId: String) {
            viewModelScope.launch(Dispatchers.IO) {
                loading.value = true
                syncMessage.value = "Eliminando dispositivo..."
                when (activationClient.revokeDevice(deviceId)) {
                    is RemoteResult.Success -> {
                        deviceRepository.deleteDevice(deviceId)
                        syncEngine.syncDevicesFull()
                        syncMessage.value = "Dispositivo eliminado."
                    }
                    is RemoteResult.Failure -> {
                        syncMessage.value = "No se pudo eliminar el dispositivo."
                    }
                }
                loading.value = false
            }
        }

        private fun Long.toDisplayDate(): String =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(this))

        private data class DeviceState(
            val devices: List<Device>,
            val activation: DeviceActivation?,
            val health: SystemHealthSnapshot,
        )

        private data class LocalState(
            val message: String,
            val pairingCode: String,
            val pairingExpiresAt: String,
            val loading: Boolean,
        )
    }
