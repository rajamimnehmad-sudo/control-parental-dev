package com.contentfilter.feature.accessibility.chromevisual

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

internal data class ChromeVisualShieldWork(
    val identity: ChromeVisualShieldIdentity,
    val trigger: String,
)

/**
 * Coalesces only pending work. Every authority invalidation happens before request(), while active
 * work is cancelled and joined by the runner before the newest pending work may start.
 */
internal class ChromeVisualShieldWorkCoordinator<T>(
    scope: CoroutineScope,
    private val onWorkSuperseded: () -> Unit,
    private val onActiveWorkCancelled: () -> Unit,
    private val execute: suspend (T) -> Unit,
) {
    private data class Pending<T>(
        val revision: Long,
        val value: T,
    )

    private val lock = Any()
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private val workerScope = scope
    private var revision = 0L
    private var pending: Pending<T>? = null
    private var active: Job? = null
    private var closed = false
    private val runner = scope.launch { runLoop() }

    fun request(value: T): Boolean {
        val activeToCancel: Job?
        val superseded: Boolean
        synchronized(lock) {
            if (closed) return false
            revision += 1
            activeToCancel = active?.takeIf { !it.isCompleted && !it.isCancelled }
            superseded = pending != null || activeToCancel != null
            pending = Pending(revision, value)
        }
        if (superseded) onWorkSuperseded()
        if (activeToCancel != null) {
            onActiveWorkCancelled()
            activeToCancel.cancel()
        }
        signal.trySend(Unit)
        return true
    }

    fun invalidateAuthority() {
        val activeToCancel: Job?
        val superseded: Boolean
        synchronized(lock) {
            if (closed) return
            revision += 1
            activeToCancel = active?.takeIf { !it.isCompleted && !it.isCancelled }
            superseded = pending != null || activeToCancel != null
            pending = null
        }
        if (superseded) onWorkSuperseded()
        if (activeToCancel != null) {
            onActiveWorkCancelled()
            activeToCancel.cancel()
        }
        signal.trySend(Unit)
    }

    suspend fun cancelAndJoin() {
        val target =
            synchronized(lock) {
                revision += 1
                pending = null
                active?.takeIf { !it.isCompleted }?.let { it to !it.isCancelled }
            }
        if (target != null) {
            if (target.second) onActiveWorkCancelled()
            target.first.cancelAndJoin()
        }
    }

    suspend fun shutdown() {
        val target =
            synchronized(lock) {
                if (closed) return
                closed = true
                revision += 1
                pending = null
                active?.takeIf { !it.isCompleted }?.let { it to !it.isCancelled }
            }
        if (target != null) {
            if (target.second) onActiveWorkCancelled()
            target.first.cancelAndJoin()
        }
        runner.cancelAndJoin()
        signal.close()
    }

    fun isIdle(): Boolean =
        synchronized(lock) {
            pending == null && active?.isCompleted != false
        }

    private suspend fun runLoop() {
        for (ignored in signal) {
            while (true) {
                val claimed = synchronized(lock) { pending.also { pending = null } } ?: break
                val candidate =
                    workerScope.launch(start = CoroutineStart.LAZY) {
                        execute(claimed.value)
                    }
                val shouldStart =
                    synchronized(lock) {
                        if (!closed && claimed.revision == revision) {
                            active = candidate
                            true
                        } else {
                            false
                        }
                    }
                if (!shouldStart) {
                    candidate.cancel()
                    continue
                }
                candidate.start()
                candidate.join()
                synchronized(lock) {
                    if (active === candidate) active = null
                }
            }
        }
    }
}
