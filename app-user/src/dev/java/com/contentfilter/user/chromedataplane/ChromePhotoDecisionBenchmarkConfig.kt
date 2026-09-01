package com.contentfilter.user.chromedataplane

internal data class ChromePhotoDecisionBenchmarkConfig(
    val maximumCacheEntries: Int = DefaultCacheEntries,
    val maximumConcurrentInferences: Int = DefaultConcurrentInferences,
) {
    init {
        require(maximumCacheEntries in AllowedCacheEntries) { "invalid_decision_cache_entries" }
        require(maximumConcurrentInferences in AllowedConcurrentInferences) {
            "invalid_decision_concurrency"
        }
    }

    val maximumQueueEntries: Int = FixedQueueEntries
    val timeoutMillis: Long = FixedTimeoutMillis

    internal companion object {
        const val DefaultCacheEntries = 64
        const val DefaultConcurrentInferences = 1
        const val FixedQueueEntries = 2
        const val FixedTimeoutMillis = 5_000L
        val AllowedCacheEntries = setOf(64, 256)
        val AllowedConcurrentInferences = setOf(1, 2)
    }
}
