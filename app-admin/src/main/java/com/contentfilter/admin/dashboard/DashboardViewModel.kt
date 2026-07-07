package com.contentfilter.admin.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.domain.model.RequestStatus
import com.contentfilter.core.domain.repository.AccountRepository
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.domain.repository.SystemStatusRepository
import com.contentfilter.core.domain.usecase.admin.ObserveDevicesUseCase
import com.contentfilter.core.domain.usecase.admin.ObserveRequestsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel
    @Inject
    constructor(
        observeDevices: ObserveDevicesUseCase,
        observeRequests: ObserveRequestsUseCase,
        systemStatusRepository: SystemStatusRepository,
        activationRepository: DeviceActivationRepository,
        accountRepository: AccountRepository,
    ) : ViewModel() {
        private val accountFlow =
            activationRepository.observeActivation().flatMapLatest { activation ->
                activation?.accountId?.let(accountRepository::observeAccount) ?: flowOf(null)
            }

        val uiState =
            combine(
                observeDevices(),
                observeRequests(),
                systemStatusRepository.observeHealth(),
                accountFlow,
            ) { devices, requests, health, account ->
                DashboardUiState(
                    deviceCount = devices.count { it.appRole != "admin" },
                    pendingRequests =
                        requests.count {
                            it.status == RequestStatus.PendingLocal || it.status == RequestStatus.PendingRemote
                        },
                    syncState = health.syncState.name,
                    systemState = health.protectionLevel.name,
                    lastSync = health.checkedAtEpochMillis.toDisplayDate(),
                    communityName = account?.communityName.orEmpty(),
                    guideName = account?.guideName.orEmpty(),
                    offlineMode = false,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = DashboardUiState(offlineMode = false),
            )
    }

private fun Long.toDisplayDate(): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(this))
