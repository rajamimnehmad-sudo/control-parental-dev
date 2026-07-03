package com.contentfilter.core.sync.engine

import com.contentfilter.core.database.entity.AccessRequestEntity
import com.contentfilter.core.database.entity.DailyLimitEntity
import com.contentfilter.core.database.entity.DeviceEntity
import com.contentfilter.core.database.entity.ExtraTimeGrantEntity
import com.contentfilter.core.database.entity.PolicyEntity
import com.contentfilter.core.database.entity.PolicyRuleEntity
import com.contentfilter.core.network.dto.RemoteAccessRequestDto
import com.contentfilter.core.network.dto.RemoteDailyLimitDto
import com.contentfilter.core.network.dto.RemoteDeviceDto
import com.contentfilter.core.network.dto.RemoteExtraTimeGrantDto
import com.contentfilter.core.network.dto.RemotePolicyDto
import com.contentfilter.core.network.dto.RemotePolicyRuleDto

internal fun RemotePolicyDto.toEntity(): PolicyEntity =
    PolicyEntity(
        id = id,
        version = version,
        active = active && deletedAt == null,
        updatedAtEpochMillis = updatedAt.toEpochMillis(),
    )

internal fun RemoteDeviceDto.toEntity(): DeviceEntity =
    DeviceEntity(
        id = id,
        accountId = accountId,
        displayName = if (deletedAt == null) displayName else "$displayName (inactivo)",
        appRole = appRole,
        lastSeenAtEpochMillis = lastSeenAt?.toEpochMillis(),
    )

internal fun RemotePolicyRuleDto.toEntity(): PolicyRuleEntity =
    PolicyRuleEntity(
        id = id,
        policyId = policyId,
        scope = scope,
        target = target,
        action = action,
        priority = priority,
        enabled = enabled && deletedAt == null,
    )

internal fun RemoteDailyLimitDto.toEntity(): DailyLimitEntity =
    DailyLimitEntity(
        id = id,
        targetType = targetType,
        target = target,
        limitMinutes = limitMinutes,
        enabled = enabled && deletedAt == null,
    )

internal fun RemoteAccessRequestDto.toEntity(): AccessRequestEntity =
    AccessRequestEntity(
        id = id,
        requestType = requestType,
        targetType = targetType,
        target = target,
        targetPackageName = targetPackageName,
        targetDomain = targetDomain,
        reason = reason,
        requestedMinutes = requestedMinutes,
        status = status,
        createdAtEpochMillis = createdAt.toEpochMillis(),
        expiresAtEpochMillis = expiresAt?.toEpochMillis(),
    )

internal fun RemoteExtraTimeGrantDto.toEntity(): ExtraTimeGrantEntity =
    ExtraTimeGrantEntity(
        id = id,
        requestId = requestId,
        targetType = targetType,
        target = target,
        grantedMinutes = grantedMinutes,
        validUntilEpochMillis = validUntil.toEpochMillis(),
    )
