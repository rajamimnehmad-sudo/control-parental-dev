package com.contentfilter.feature.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.domain.model.UsageSession
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.domain.repository.UsageSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UsageViewModel
    @Inject
    constructor(
        activationRepository: DeviceActivationRepository,
        usageSessionRepository: UsageSessionRepository,
    ) : ViewModel() {
        private val day = UsageDay.today()

        val uiState =
            activationRepository
                .observeActivation()
                .flatMapLatest { activation ->
                    usageSessionRepository.observeDailyUsage(
                        deviceId = activation?.deviceId ?: UsageSession.LOCAL_DEVICE_ID,
                        localDate = day.localDate,
                        dayStartEpochMillis = day.startEpochMillis,
                        dayEndEpochMillis = day.endEpochMillis,
                    )
                }
                .map { usage ->
                    UsageUiState(
                        localDate = day.localDate,
                        items =
                            usage
                                .sortedByDescending { it.usedMinutes }
                                .map { UsageItemUiState(it.packageName, it.usedMinutes) },
                    )
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                    initialValue = UsageUiState(localDate = day.localDate),
                )
    }
