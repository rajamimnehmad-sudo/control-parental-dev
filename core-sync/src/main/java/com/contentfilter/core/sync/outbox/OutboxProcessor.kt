package com.contentfilter.core.sync.outbox

import com.contentfilter.core.domain.model.PolicyMutationReceipt

interface OutboxProcessor {
    suspend fun processPending()

    suspend fun processPolicyMutation(receipt: PolicyMutationReceipt): OutboxBatchResult
}
