package com.contentfilter.core.domain.model

data class DomainPolicyContext(
    val domain: String,
    val category: String?,
    val time: TimePolicyContext,
    val device: DevicePolicyContext,
)
