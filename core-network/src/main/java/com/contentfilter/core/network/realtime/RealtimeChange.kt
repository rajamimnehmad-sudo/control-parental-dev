package com.contentfilter.core.network.realtime

import com.contentfilter.core.network.remote.SupabaseTable

data class RealtimeChange(
    val table: SupabaseTable,
    val payload: String,
)
