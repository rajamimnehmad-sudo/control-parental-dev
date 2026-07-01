package com.contentfilter.core.domain.model

data class DevicePolicyContext(
    val isActivated: Boolean,
    val healthSnapshot: SystemHealthSnapshot,
)
