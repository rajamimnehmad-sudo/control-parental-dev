package com.contentfilter.feature.vpn.search

import android.os.SystemClock

object SearchProtectionSignals {
    @Volatile
    private var lastSearchEngine: RecentSearchEngine? = null

    fun recordSearchEngine(
        engineId: String,
        policyRevision: Long,
    ) {
        lastSearchEngine =
            RecentSearchEngine(
                engineId = engineId,
                policyRevision = policyRevision,
                elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
            )
    }

    fun recentSearchEngine(windowMillis: Long = RecentWindowMillis): RecentSearchEngine? {
        val observation = lastSearchEngine ?: return null
        return observation.takeIf {
            SystemClock.elapsedRealtime() - it.elapsedRealtimeMillis <= windowMillis
        }
    }

    private const val RecentWindowMillis = 12_000L
}

data class RecentSearchEngine(
    val engineId: String,
    val policyRevision: Long,
    val elapsedRealtimeMillis: Long,
)
