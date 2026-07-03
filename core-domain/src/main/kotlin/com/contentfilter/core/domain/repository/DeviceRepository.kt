package com.contentfilter.core.domain.repository

import com.contentfilter.core.domain.model.Device
import kotlinx.coroutines.flow.Flow

interface DeviceRepository {
    fun observeDevices(): Flow<List<Device>>

    suspend fun saveDevice(device: Device)

    suspend fun deleteDevice(deviceId: String)
}
