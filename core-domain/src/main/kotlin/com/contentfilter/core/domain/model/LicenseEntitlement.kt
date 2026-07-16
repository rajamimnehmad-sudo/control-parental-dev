package com.contentfilter.core.domain.model

private const val ExpiringSoonWindowMillis = 7L * 24 * 60 * 60 * 1_000

data class LicenseEntitlement(
    val state: LicenseState,
    val startsAtEpochMillis: Long?,
    val expiresAtEpochMillis: Long?,
    val verifiedAtEpochMillis: Long,
) {
    fun effectiveState(nowEpochMillis: Long): LicenseState =
        when {
            state == LicenseState.Suspended -> LicenseState.Suspended
            state == LicenseState.Expired -> LicenseState.Expired
            state == LicenseState.PendingActivation -> LicenseState.PendingActivation
            startsAtEpochMillis != null && nowEpochMillis < startsAtEpochMillis -> LicenseState.Scheduled
            expiresAtEpochMillis != null && nowEpochMillis >= expiresAtEpochMillis -> LicenseState.Expired
            expiresAtEpochMillis != null && expiresAtEpochMillis - nowEpochMillis <= ExpiringSoonWindowMillis ->
                LicenseState.ExpiringSoon
            else -> LicenseState.Active
        }
}
