package com.contentfilter.admin.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.admin.BuildConfig
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.domain.repository.SystemStatusRepository
import com.contentfilter.core.domain.usecase.admin.ObserveDevicesUseCase
import com.contentfilter.core.sync.SyncScheduler
import com.contentfilter.core.sync.engine.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DevicesViewModel
    @Inject
    constructor(
        observeDevices: ObserveDevicesUseCase,
        activationRepository: DeviceActivationRepository,
        systemStatusRepository: SystemStatusRepository,
        private val syncScheduler: SyncScheduler,
        private val syncEngine: SyncEngine,
    ) : ViewModel() {
        val uiState = combine(
            observeDevices(),
            activationRepository.observeActivation(),
            systemStatusRepository.observeHealth(),
        ) { devices, activation, health ->
            val items = devices.map { device ->
                val isCurrent = activation?.deviceId == device.id
                AdminDeviceItem(
                    id = device.id,
                    name = device.displayName,
                    user = "Usuario protegido",
                    version = if (isCurrent) BuildConfig.VERSION_NAME else "Sin datos",
                    vpnState = if (isCurrent) health.vpnState.name else "Sin datos",
                    accessibilityState = if (isCurrent) health.accessibilityState.name else "Sin datos",
                    lastSync = if (isCurrent) health.checkedAtEpochMillis.toDisplayDate() else "Sin datos",
                    systemState = if (isCurrent) health.protectionLevel.name else "Sin datos",
                )
            }
            DevicesUiState(
                devices = items,
                offlineMode = false,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DevicesUiState(offlineMode = false),
        )

        init {
            syncScheduler.requestSync()
            viewModelScope.launch(Dispatchers.IO) {
                syncEngine.syncOnce()
            }
        }

        private fun Long.toDisplayDate(): String =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(this))
    }
