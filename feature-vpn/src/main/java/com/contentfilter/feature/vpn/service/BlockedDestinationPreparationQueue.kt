package com.contentfilter.feature.vpn.service

internal class BlockedDestinationPreparationQueue<T>(
    private val maxPending: Int = DefaultMaxPending,
) {
    private val pending = LinkedHashMap<String, T>()
    private var workerActive = false

    init {
        require(maxPending > 0)
    }

    @Synchronized
    fun offer(
        key: String,
        value: T,
    ): OfferResult =
        if (!pending.containsKey(key) && pending.size >= maxPending) {
            OfferResult.Rejected
        } else {
            pending[key] = value
            if (workerActive) {
                OfferResult.Queued
            } else {
                workerActive = true
                OfferResult.StartWorker
            }
        }

    @Synchronized
    fun drain(): List<T> {
        val drained = pending.values.toList()
        pending.clear()
        return drained
    }

    @Synchronized
    fun markIdleIfEmpty(): Boolean {
        if (pending.isNotEmpty()) return false
        workerActive = false
        return true
    }

    @Synchronized
    fun clear(): List<T> {
        val abandoned = pending.values.toList()
        pending.clear()
        workerActive = false
        return abandoned
    }

    enum class OfferResult {
        StartWorker,
        Queued,
        Rejected,
    }

    private companion object {
        const val DefaultMaxPending = 128
    }
}

internal fun blockedDestinationInvalidationDelayMillis(
    lastStartedAtMillis: Long?,
    nowMillis: Long,
    minimumIntervalMillis: Long,
): Long {
    require(nowMillis >= 0L)
    require(minimumIntervalMillis >= 0L)
    val lastStarted = lastStartedAtMillis ?: return 0L
    val elapsed = (nowMillis - lastStarted).coerceAtLeast(0L)
    return (minimumIntervalMillis - elapsed).coerceAtLeast(0L)
}
