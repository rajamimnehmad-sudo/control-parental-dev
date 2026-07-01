package com.contentfilter.core.data

import com.contentfilter.core.database.entity.ExtraTimeGrantEntity
import com.contentfilter.core.domain.model.ExtraTimeGrant
import com.contentfilter.core.domain.model.PolicyTargetType

internal fun ExtraTimeGrantEntity.toDomain(): ExtraTimeGrant =
    ExtraTimeGrant(
        id = id,
        requestId = requestId,
        targetType = enumValueOrDefault(targetType, PolicyTargetType.Global),
        target = target,
        grantedMinutes = grantedMinutes,
        validUntilEpochMillis = validUntilEpochMillis,
    )

internal fun ExtraTimeGrant.toEntity(): ExtraTimeGrantEntity =
    ExtraTimeGrantEntity(
        id = id,
        requestId = requestId,
        targetType = targetType.name,
        target = target,
        grantedMinutes = grantedMinutes,
        validUntilEpochMillis = validUntilEpochMillis,
    )

private inline fun <reified T : Enum<T>> enumValueOrDefault(
    value: String,
    default: T,
): T = enumValues<T>().firstOrNull { it.name == value } ?: default
