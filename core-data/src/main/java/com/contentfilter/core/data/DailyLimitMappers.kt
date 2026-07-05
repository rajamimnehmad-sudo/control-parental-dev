package com.contentfilter.core.data

import com.contentfilter.core.database.entity.DailyLimitEntity
import com.contentfilter.core.domain.model.DailyLimit
import com.contentfilter.core.domain.model.PolicyTargetType

internal fun DailyLimitEntity.toDomain(): DailyLimit =
    DailyLimit(
        id = id,
        targetType = enumValueOrDefault(targetType, PolicyTargetType.Global),
        target = target,
        limitMinutes = limitMinutes,
        enabled = enabled,
    )

internal fun DailyLimit.toEntity(): DailyLimitEntity =
    DailyLimitEntity(
        id = id,
        policyId = null,
        targetType = targetType.name,
        target = target,
        limitMinutes = limitMinutes,
        enabled = enabled,
    )

private inline fun <reified T : Enum<T>> enumValueOrDefault(
    value: String,
    default: T,
): T = enumValues<T>().firstOrNull { it.name == value } ?: default
