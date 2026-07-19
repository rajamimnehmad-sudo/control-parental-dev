package com.contentfilter.core.sync.engine

import com.contentfilter.core.database.entity.AccessRequestEntity
import com.contentfilter.core.database.entity.AppGroupAppEntity
import com.contentfilter.core.database.entity.AppGroupEntity
import com.contentfilter.core.database.entity.DailyLimitEntity
import com.contentfilter.core.database.entity.DeviceEntity
import com.contentfilter.core.database.entity.ExtraTimeGrantEntity
import com.contentfilter.core.database.entity.PolicyEntity
import com.contentfilter.core.database.entity.PolicyRuleEntity
import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.network.dto.RemoteAccessRequestDto
import com.contentfilter.core.network.dto.RemoteAppGroupAppDto
import com.contentfilter.core.network.dto.RemoteAppGroupDto
import com.contentfilter.core.network.dto.RemoteDailyLimitDto
import com.contentfilter.core.network.dto.RemoteDeviceDto
import com.contentfilter.core.network.dto.RemoteExtraTimeGrantDto
import com.contentfilter.core.network.dto.RemotePolicyDto
import com.contentfilter.core.network.dto.RemotePolicyRuleDto

internal fun RemotePolicyDto.toEntity(): PolicyEntity =
    PolicyEntity(
        id = id,
        deviceId = deviceId,
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
        vpnState = vpnState.toComponentStateName(),
        accessibilityState = accessibilityState.toComponentStateName(),
        deviceAdminState = deviceAdminState.toComponentStateName(),
        protectionAlert = protectionAlert,
        protectionUpdatedAtEpochMillis = protectionUpdatedAt?.toEpochMillis(),
        appliedPolicyId = appliedPolicyId,
        appliedPolicyRevision = appliedPolicyRevision,
        policyAppliedAtEpochMillis = policyAppliedAt?.toEpochMillis(),
    )

private fun String?.toComponentStateName(): String =
    this
        ?.let { value -> runCatching { ComponentState.valueOf(value) }.getOrNull() }
        ?.name
        ?: ComponentState.Unknown.name

internal fun RemotePolicyRuleDto.toEntity(): PolicyRuleEntity =
    PolicyRuleEntity(
        id = id,
        policyId = policyId,
        scope = scope,
        target = target,
        action = action,
        priority = priority,
        enabled = enabled && deletedAt == null,
        activeWindowStartMinute = activeWindowStartMinute,
        activeWindowEndMinute = activeWindowEndMinute,
        activeDaysMask = activeDaysMask,
        updatedAtEpochMillis = updatedAt.toEpochMillis(),
    )

internal fun RemoteDailyLimitDto.toEntity(): DailyLimitEntity =
    DailyLimitEntity(
        id = id,
        policyId = policyId,
        targetType = targetType,
        target = target,
        limitMinutes = limitMinutes,
        enabled = enabled && deletedAt == null,
        updatedAtEpochMillis = updatedAt.toEpochMillis(),
    )

internal fun RemoteAppGroupDto.toEntity(): AppGroupEntity =
    AppGroupEntity(
        id = id,
        deviceId = deviceId,
        name = name,
        color = color,
        limitMinutes = limitMinutes,
        resetMinuteOfDay = resetMinuteOfDay,
        enabled = enabled && deletedAt == null,
        updatedAtEpochMillis = updatedAt.toEpochMillis(),
    )

internal fun RemoteAppGroupAppDto.toEntity(): AppGroupAppEntity =
    AppGroupAppEntity(
        id = id,
        groupId = groupId,
        packageName = packageName,
        enabled = enabled && deletedAt == null,
        updatedAtEpochMillis = updatedAt.toEpochMillis(),
    )

internal fun RemoteAccessRequestDto.toEntity(): AccessRequestEntity =
    AccessRequestEntity(
        id = id,
        deviceId = deviceId,
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
