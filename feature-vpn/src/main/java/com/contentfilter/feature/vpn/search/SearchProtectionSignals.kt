package com.contentfilter.feature.vpn.search

import android.os.SystemClock

object SearchProtectionSignals {
    private var lastDnsBlock: RecentDnsSearchBlock? = null

    fun recordDnsBlock(host: String) {
        lastDnsBlock =
            RecentDnsSearchBlock(
                host = host.lowercase().substringBefore("/").substringBefore("?"),
                elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
            )
    }

    fun recentDnsBlock(windowMillis: Long = RecentWindowMillis): RecentDnsSearchBlock? {
        val block = lastDnsBlock ?: return null
        return block.takeIf { SystemClock.elapsedRealtime() - it.elapsedRealtimeMillis <= windowMillis }
    }

    private const val RecentWindowMillis = 12_000L
}

data class RecentDnsSearchBlock(
    val host: String,
    val elapsedRealtimeMillis: Long,
)
