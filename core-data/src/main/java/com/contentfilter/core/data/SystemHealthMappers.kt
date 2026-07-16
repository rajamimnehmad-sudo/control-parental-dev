package com.contentfilter.core.data

import com.contentfilter.core.database.entity.SystemHealthEntity
import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.model.LicenseEntitlement
import com.contentfilter.core.domain.model.LicenseState
import com.contentfilter.core.domain.model.SystemHealthSnapshot
import com.contentfilter.core.domain.model.UpdateState

internal fun SystemHealthEntity.toDomain(): SystemHealthSnapshot {
    val now = System.currentTimeMillis()
    val storedState = enumValueOrDefault(licenseState, LicenseState.PendingActivation)
    val effectiveState =
        LicenseEntitlement(
            state = storedState,
            startsAtEpochMillis = licenseStartsAtEpochMillis,
            expiresAtEpochMillis = licenseExpiresAtEpochMillis,
            verifiedAtEpochMillis = licenseVerifiedAtEpochMillis ?: checkedAtEpochMillis,
            dagEntitled = dagEntitled,
        ).effectiveState(now)
    return SystemHealthSnapshot(
        vpnState = enumValueOrDefault(vpnState, ComponentState.Unknown),
        accessibilityState = enumValueOrDefault(accessibilityState, ComponentState.Unknown),
        deviceAdminState = enumValueOrDefault(deviceAdminState, ComponentState.Unknown),
        syncState = enumValueOrDefault(syncState, ComponentState.Unknown),
        integrityState = enumValueOrDefault(integrityState, ComponentState.Unknown),
        databaseState = enumValueOrDefault(databaseState, ComponentState.Warning),
        licenseState = effectiveState,
        licenseStartsAtEpochMillis = licenseStartsAtEpochMillis,
        licenseExpiresAtEpochMillis = licenseExpiresAtEpochMillis,
        licenseVerifiedAtEpochMillis = licenseVerifiedAtEpochMillis,
        dagEntitled = dagEntitled,
        updateState = enumValueOrDefault(updateState, UpdateState.Unknown),
        checkedAtEpochMillis = checkedAtEpochMillis,
    )
}

internal fun defaultHealthSnapshot(nowEpochMillis: Long): SystemHealthSnapshot =
    SystemHealthSnapshot(
        vpnState = ComponentState.Unknown,
        accessibilityState = ComponentState.Unknown,
        deviceAdminState = ComponentState.Unknown,
        syncState = ComponentState.Unknown,
        integrityState = ComponentState.Unknown,
        databaseState = ComponentState.Enabled,
        licenseState = LicenseState.PendingActivation,
        updateState = UpdateState.Unknown,
        checkedAtEpochMillis = nowEpochMillis,
    )

internal fun SystemHealthSnapshot.toEntity(): SystemHealthEntity =
    SystemHealthEntity(
        vpnState = vpnState.name,
        accessibilityState = accessibilityState.name,
        deviceAdminState = deviceAdminState.name,
        syncState = syncState.name,
        integrityState = integrityState.name,
        databaseState = databaseState.name,
        licenseState = licenseState.name,
        licenseStartsAtEpochMillis = licenseStartsAtEpochMillis,
        licenseExpiresAtEpochMillis = licenseExpiresAtEpochMillis,
        licenseVerifiedAtEpochMillis = licenseVerifiedAtEpochMillis,
        dagEntitled = dagEntitled,
        updateState = updateState.name,
        checkedAtEpochMillis = checkedAtEpochMillis,
    )

private inline fun <reified T : Enum<T>> enumValueOrDefault(
    value: String,
    default: T,
): T = enumValues<T>().firstOrNull { it.name == value } ?: default
