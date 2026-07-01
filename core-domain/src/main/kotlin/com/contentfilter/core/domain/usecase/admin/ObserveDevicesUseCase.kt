package com.contentfilter.core.domain.usecase.admin

import com.contentfilter.core.domain.repository.DeviceRepository

class ObserveDevicesUseCase(
    private val repository: DeviceRepository,
) {
    operator fun invoke() = repository.observeDevices()
}
