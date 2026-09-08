package com.contentfilter.user.chromedataplane

import com.contentfilter.user.chromeguard.ChromeGuardContract
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Detects expired or persistently suspended sessions; the existing guard authorizes every release. */
internal class ChromePhotosGuardHeartbeatRecovery {
    private val enabled = AtomicBoolean(false)
    private val pendingDelay = AtomicLong(0L)
    private var suspendedSince = 0L

    fun start() {
        pendingDelay.set(0L)
        enabled.set(true)
    }

    fun stop() {
        enabled.set(false)
        pendingDelay.set(0L)
    }

    fun isEnabled(): Boolean = enabled.get()

    fun needsNewSession(
        now: Long,
        lastHeartbeat: Long,
        chromeSuspended: Boolean = false,
    ): Boolean {
        if (!enabled.get() || lastHeartbeat <= 0L) {
            suspendedSince = 0L
            return false
        }
        if (!chromeSuspended) {
            suspendedSince = 0L
        } else if (suspendedSince == 0L) {
            suspendedSince = now
        }
        return now - lastHeartbeat >= ChromeGuardContract.LeaseTtlMillis ||
            (suspendedSince > 0L && now - suspendedSince >= ChromeGuardContract.LeaseTtlMillis)
    }

    /** Explicit DEV fault injection, consumed once; never runs during ordinary browsing. */
    fun requestDelay(): Boolean = enabled.get() && pendingDelay.compareAndSet(0L, 3_000L)

    fun consumeDelay(): Long = pendingDelay.getAndSet(0L)
}

internal val chromePhotosGuardHeartbeatRecovery = ChromePhotosGuardHeartbeatRecovery()

/** A bounded IPC timeout permits a later health-checked retry; external cancellation propagates. */
internal suspend fun <T> openGuardSessionOrRetry(openSession: suspend () -> T): T? =
    try {
        openSession()
    } catch (_: TimeoutCancellationException) {
        currentCoroutineContext().ensureActive()
        null
    }
