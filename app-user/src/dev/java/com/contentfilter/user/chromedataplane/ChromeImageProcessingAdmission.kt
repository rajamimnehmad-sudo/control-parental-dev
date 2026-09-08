package com.contentfilter.user.chromedataplane

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Rechecks exact-byte cached decisions when processing completes, without polling or new workers. */
internal class ChromeImageProcessingAdmission(private val capacity: Int) {
    private val lock = ReentrantLock(true)
    private val changed = lock.newCondition()
    private var active = 0
    private val peak = AtomicInteger()
    private val rejected = AtomicLong()

    init {
        require(capacity > 0)
    }

    fun peak(): Int = peak.get()

    fun rejections(): Long = rejected.get()

    fun <T> run(
        onRejected: () -> T,
        cached: () -> T?,
        block: () -> T,
    ): T {
        try {
            lock.lockInterruptibly()
            try {
                while (true) {
                    cached()?.let { return it }
                    if (active < capacity) {
                        active += 1
                        peak.accumulateAndGet(active, ::maxOf)
                        break
                    }
                    changed.await()
                }
            } finally {
                lock.unlock()
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            rejected.incrementAndGet()
            return onRejected()
        }
        return try {
            block()
        } finally {
            lock.withLock {
                active -= 1
                changed.signalAll()
            }
        }
    }
}
