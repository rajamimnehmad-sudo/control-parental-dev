package com.contentfilter.core.domain.repository

import com.contentfilter.core.domain.model.DeviceActivation
import kotlinx.coroutines.flow.Flow

interface DeviceActivationRepository {
    fun observeActivation(): Flow<DeviceActivation?>

    suspend fun currentActivation(): DeviceActivation?

    suspend fun saveActivation(activation: DeviceActivation)
}
