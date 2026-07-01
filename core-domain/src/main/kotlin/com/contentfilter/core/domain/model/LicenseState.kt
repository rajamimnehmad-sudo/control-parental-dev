package com.contentfilter.core.domain.model

/**
 * License state exposed to policy decisions without coupling to a payment provider.
 */
enum class LicenseState {
    Active,
    ExpiringSoon,
    Expired,
    GracePeriod,
    Suspended,
    PendingActivation,
}
