package com.contentfilter.core.sync.outbox

interface OutboxProcessor {
    suspend fun processPending()
}
