package com.contentfilter.user.chromedataplane

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.Semaphore
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal enum class ChromeProxyAdmissionResult {
    Accepted,
    Closed,
    Rejected,
}

/**
 * Bounds accepted proxy work before it reaches the executor queue.
 *
 * The accept loop blocks when every worker and queue slot is occupied, allowing the listening
 * socket backlog to provide TCP backpressure instead of accepting a connection only to drop it.
 */
internal class ChromeProxyAdmission(
    workerCount: Int,
    queueCapacity: Int,
    threadNamePrefix: String = "chrome-web-proxy-worker",
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val permits: Semaphore
    private val executor: ThreadPoolExecutor

    init {
        require(workerCount > 0)
        require(queueCapacity > 0)
        require(threadNamePrefix.isNotBlank())
        val threadNumber = AtomicInteger()
        permits = Semaphore(workerCount + queueCapacity, true)
        executor =
            ThreadPoolExecutor(
                workerCount,
                workerCount,
                0L,
                TimeUnit.MILLISECONDS,
                ArrayBlockingQueue(queueCapacity),
                ThreadFactory { runnable ->
                    Thread(runnable, "$threadNamePrefix-${threadNumber.incrementAndGet()}").apply {
                        isDaemon = true
                    }
                },
                ThreadPoolExecutor.AbortPolicy(),
            )
    }

    @Throws(InterruptedException::class)
    fun dispatch(
        onDiscard: () -> Unit,
        block: () -> Unit,
    ): ChromeProxyAdmissionResult {
        permits.acquire()
        if (closed.get()) {
            permits.release()
            runCatching(onDiscard)
            return ChromeProxyAdmissionResult.Closed
        }

        val task = AdmittedTask(permits, onDiscard, block)
        return try {
            executor.execute(task)
            ChromeProxyAdmissionResult.Accepted
        } catch (_: RejectedExecutionException) {
            task.discard()
            if (closed.get() || executor.isShutdown) {
                ChromeProxyAdmissionResult.Closed
            } else {
                ChromeProxyAdmissionResult.Rejected
            }
        }
    }

    fun isShutdown(): Boolean = closed.get() || executor.isShutdown

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        executor.shutdownNow().forEach { runnable ->
            (runnable as? AdmittedTask)?.discard()
        }
    }

    private class AdmittedTask(
        private val permits: Semaphore,
        private val onDiscard: () -> Unit,
        private val block: () -> Unit,
    ) : Runnable {
        private val finished = AtomicBoolean(false)

        override fun run() {
            try {
                block()
            } finally {
                finish(discard = false)
            }
        }

        fun discard() {
            finish(discard = true)
        }

        private fun finish(discard: Boolean) {
            if (!finished.compareAndSet(false, true)) return
            try {
                if (discard) runCatching(onDiscard)
            } finally {
                permits.release()
            }
        }
    }
}
