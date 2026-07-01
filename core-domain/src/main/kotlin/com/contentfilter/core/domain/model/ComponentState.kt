package com.contentfilter.core.domain.model

/**
 * Generic state used by platform components that affect protection.
 */
enum class ComponentState {
    Enabled,
    Disabled,
    Warning,
    Unknown,
}
