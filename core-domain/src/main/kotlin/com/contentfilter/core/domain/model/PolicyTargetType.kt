package com.contentfilter.core.domain.model

/**
 * Target type shared by limits, usage and requests.
 */
enum class PolicyTargetType {
    App,
    Domain,
    Category,
    Global,
}
