package com.contentfilter.core.sync.outbox

data class OutboxBatchResult(
    val serverConfirmed: Boolean,
    val notificationDelivered: Boolean,
    val revision: Long,
    val pendingOperationIds: List<String>,
    val error: String? = null,
)
