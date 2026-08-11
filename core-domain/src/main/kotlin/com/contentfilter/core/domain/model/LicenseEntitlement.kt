package com.contentfilter.core.domain.model

private const val ExpiringSoonWindowMillis = 7L * 24 * 60 * 60 * 1_000

data class LicenseEntitlement(
    val state: LicenseState,
    val startsAtEpochMillis: Long?,
    val expiresAtEpochMillis: Long?,
    val verifiedAtEpochMillis: Long,
    val dagEntitled: Boolean = false,
) {
    fun effectiveState(): LicenseState =
        when {
            state == LicenseState.Suspended -> LicenseState.Suspended
            state == LicenseState.Expired -> LicenseState.Expired
            state == LicenseState.PendingActivation -> LicenseState.PendingActivation
            state == LicenseState.Scheduled -> LicenseState.Scheduled
            startsAtEpochMillis != null && verifiedAtEpochMillis < startsAtEpochMillis -> LicenseState.Scheduled
            expiresAtEpochMillis != null && verifiedAtEpochMillis >= expiresAtEpochMillis -> LicenseState.Expired
            expiresAtEpochMillis != null &&
                expiresAtEpochMillis - verifiedAtEpochMillis <= ExpiringSoonWindowMillis ->
                LicenseState.ExpiringSoon
            else -> LicenseState.Active
        }
}
