package com.contentfilter.core.network.realtime

import kotlinx.coroutines.flow.Flow

interface RealtimeSubscription {
    fun observeChanges(): Flow<RealtimeChange>

    fun connect(deviceId: String? = null)

    fun disconnect()
}
