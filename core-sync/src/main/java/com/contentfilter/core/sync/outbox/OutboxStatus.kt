package com.contentfilter.core.sync.outbox

enum class OutboxStatus {
    Pending,
    Synced,
    Failed,
}
