package com.contentfilter.user.chromedataplane

import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger

/** Bounds buffered encoded image bytes independently of inference concurrency. */
internal class ChromeImageBodyBudget(val capacity: Int) {
    private val permits = Semaphore(capacity, true)
    private val used = AtomicInteger()
    private val peak = AtomicInteger()

    init {
        require(capacity > 0)
    }

    fun peakBytes(): Int = peak.get()

    fun <T> withReservation(
        bytes: Int,
        onRejected: () -> T,
        block: () -> T,
    ): T {
        if (bytes !in 1..capacity) return onRejected()
        try {
            permits.acquire(bytes)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return onRejected()
        }
        val active = used.addAndGet(bytes)
        peak.accumulateAndGet(active, ::maxOf)
        return try {
            block()
        } finally {
            used.addAndGet(-bytes)
            permits.release(bytes)
        }
    }
}
