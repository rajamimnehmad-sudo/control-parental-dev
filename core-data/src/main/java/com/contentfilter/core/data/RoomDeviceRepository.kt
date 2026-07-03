package com.contentfilter.core.data

import com.contentfilter.core.database.dao.DeviceDao
import com.contentfilter.core.domain.model.Device
import com.contentfilter.core.domain.repository.DeviceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomDeviceRepository
    @Inject
    constructor(
        private val deviceDao: DeviceDao,
    ) : DeviceRepository {
        override fun observeDevices(): Flow<List<Device>> =
            deviceDao.observeDevices().map { devices -> devices.map { it.toDomain() } }

        override suspend fun saveDevice(device: Device) {
            deviceDao.upsert(device.toEntity())
        }

        override suspend fun deleteDevice(deviceId: String) {
            deviceDao.deleteById(deviceId)
        }
    }
