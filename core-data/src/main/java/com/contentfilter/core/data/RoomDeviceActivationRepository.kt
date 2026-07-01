package com.contentfilter.core.data

import com.contentfilter.core.database.dao.DeviceActivationDao
import com.contentfilter.core.domain.model.DeviceActivation
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomDeviceActivationRepository
    @Inject
    constructor(
        private val dao: DeviceActivationDao,
    ) : DeviceActivationRepository {
        override fun observeActivation(): Flow<DeviceActivation?> =
            dao.observeLatest().map { it?.toDomain() }

        override suspend fun currentActivation(): DeviceActivation? =
            dao.latest()?.toDomain()

        override suspend fun saveActivation(activation: DeviceActivation) {
            dao.upsert(activation.toEntity())
        }
    }
