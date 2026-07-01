package com.contentfilter.core.data

import com.contentfilter.core.database.entity.DeviceEntity
import com.contentfilter.core.domain.model.Device

internal fun DeviceEntity.toDomain(): Device =
    Device(
        id = id,
        accountId = accountId,
        displayName = displayName,
    )

internal fun Device.toEntity(): DeviceEntity =
    DeviceEntity(
        id = id,
        accountId = accountId,
        displayName = displayName,
    )
