package com.contentfilter.core.data

import com.contentfilter.core.database.entity.AccessRequestEntity
import com.contentfilter.core.domain.model.AccessRequest
import com.contentfilter.core.domain.model.AccessRequestType
import com.contentfilter.core.domain.model.PolicyTargetType
import com.contentfilter.core.domain.model.RequestStatus

internal fun AccessRequestEntity.toDomain(): AccessRequest =
    AccessRequest(
        id = id,
        deviceId = deviceId,
        requestType = enumValueOrDefault(requestType, AccessRequestType.APP_ACCESS),
        targetType = enumValueOrDefault(targetType, PolicyTargetType.Global),
        target = target,
        targetPackageName = targetPackageName,
        targetDomain = targetDomain,
        reason = reason,
        requestedMinutes = requestedMinutes,
        status = enumValueOrDefault(status, RequestStatus.PendingLocal),
        createdAtEpochMillis = createdAtEpochMillis,
        expiresAtEpochMillis = expiresAtEpochMillis,
    )

internal fun AccessRequest.toEntity(): AccessRequestEntity =
    AccessRequestEntity(
        id = id,
        deviceId = deviceId,
        requestType = requestType.name,
        targetType = targetType.name,
        target = target,
        targetPackageName = targetPackageName,
        targetDomain = targetDomain,
        reason = reason,
        requestedMinutes = requestedMinutes,
        status = status.name,
        createdAtEpochMillis = createdAtEpochMillis,
        expiresAtEpochMillis = expiresAtEpochMillis,
    )

private inline fun <reified T : Enum<T>> enumValueOrDefault(
    value: String,
    default: T,
): T = enumValues<T>().firstOrNull { it.name == value } ?: default
