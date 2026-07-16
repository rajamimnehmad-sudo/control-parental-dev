package com.contentfilter.core.domain.model

/**
 * License state exposed to policy decisions without coupling to a payment provider.
 */
enum class LicenseState {
    Active,
    Scheduled,
    ExpiringSoon,
    Expired,
    GracePeriod,
    Suspended,
    PendingActivation,
}

fun LicenseState.allowsProtection(): Boolean =
    this == LicenseState.Active || this == LicenseState.ExpiringSoon || this == LicenseState.GracePeriod
