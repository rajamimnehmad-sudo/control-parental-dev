package com.contentfilter.core.domain.model

data class PolicyMutationReceipt(
    val requestId: String,
    val deviceId: String,
    val policyId: String,
    val revision: Long,
    val operationIds: List<String>,
)
