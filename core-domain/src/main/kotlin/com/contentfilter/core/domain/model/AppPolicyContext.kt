package com.contentfilter.core.domain.model

data class AppPolicyContext(
    val packageName: String,
    val category: String?,
    val usedMinutesToday: Int,
    val time: TimePolicyContext,
    val device: DevicePolicyContext,
)
