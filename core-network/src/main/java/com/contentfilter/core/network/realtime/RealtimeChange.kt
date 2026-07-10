package com.contentfilter.core.network.realtime

import com.contentfilter.core.network.remote.SupabaseTable

sealed interface RealtimeChange {
    data class Table(
        val table: SupabaseTable,
        val payload: String,
    ) : RealtimeChange

    data class PolicyRevision(
        val requestId: String,
        val deviceId: String,
        val policyId: String,
        val revision: Long,
    ) : RealtimeChange
}
