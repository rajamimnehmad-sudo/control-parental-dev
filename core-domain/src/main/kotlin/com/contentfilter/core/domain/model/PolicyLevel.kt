package com.contentfilter.core.domain.model

/**
 * Policy hierarchy. Higher specificity wins when multiple rules match.
 */
enum class PolicyLevel(val specificity: Int) {
    Global(0),
    Account(1),
    Family(2),
    ProtectedUser(3),
    Device(4),
}
