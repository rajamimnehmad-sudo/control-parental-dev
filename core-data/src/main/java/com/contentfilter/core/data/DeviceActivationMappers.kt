package com.contentfilter.core.data

import com.contentfilter.core.database.entity.DeviceActivationEntity
import com.contentfilter.core.domain.model.DeviceActivation

internal fun DeviceActivationEntity.toDomain(): DeviceActivation =
    DeviceActivation(
        id = id,
        accountId = accountId,
        deviceId = deviceId,
        activatedAtEpochMillis = activatedAtEpochMillis,
    )

internal fun DeviceActivation.toEntity(): DeviceActivationEntity =
    DeviceActivationEntity(
        id = id,
        accountId = accountId,
        deviceId = deviceId,
        activatedAtEpochMillis = activatedAtEpochMillis,
    )
