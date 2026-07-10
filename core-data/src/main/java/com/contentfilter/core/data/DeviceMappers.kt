package com.contentfilter.core.data

import com.contentfilter.core.database.entity.DeviceEntity
import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.model.Device

internal fun DeviceEntity.toDomain(): Device =
    Device(
        id = id,
        accountId = accountId,
        displayName = displayName,
        appRole = appRole,
        lastSeenAtEpochMillis = lastSeenAtEpochMillis,
        vpnState = vpnState.toComponentState(),
        accessibilityState = accessibilityState.toComponentState(),
        protectionAlert = protectionAlert,
        protectionUpdatedAtEpochMillis = protectionUpdatedAtEpochMillis,
        appliedPolicyId = appliedPolicyId,
        appliedPolicyRevision = appliedPolicyRevision,
        policyAppliedAtEpochMillis = policyAppliedAtEpochMillis,
    )

internal fun Device.toEntity(): DeviceEntity =
    DeviceEntity(
        id = id,
        accountId = accountId,
        displayName = displayName,
        appRole = appRole,
        lastSeenAtEpochMillis = lastSeenAtEpochMillis,
        vpnState = vpnState.name,
        accessibilityState = accessibilityState.name,
        protectionAlert = protectionAlert,
        protectionUpdatedAtEpochMillis = protectionUpdatedAtEpochMillis,
        appliedPolicyId = appliedPolicyId,
        appliedPolicyRevision = appliedPolicyRevision,
        policyAppliedAtEpochMillis = policyAppliedAtEpochMillis,
    )

private fun String.toComponentState(): ComponentState =
    runCatching { ComponentState.valueOf(this) }.getOrDefault(ComponentState.Unknown)
