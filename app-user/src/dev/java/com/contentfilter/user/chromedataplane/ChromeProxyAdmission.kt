package com.contentfilter.user.chromedataplane

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.RejectedExecutionHandler
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
 * Applies bounded backpressure when every proxy worker and queue slot is occupied.
 *
 * The accept loop blocks on the existing bounded executor queue instead of accepting a connection
 * only to drop it. The listening socket backlog therefore remains the outer TCP admission boundary.
 */
internal class ChromeProxyAdmission(
    workerCount: Int,
    queueCapacity: Int,
    threadNamePrefix: String = "chrome-web-proxy-worker",
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val executor: ThreadPoolExecutor

    init {
        require(workerCount > 0)
        require(queueCapacity > 0)
        require(threadNamePrefix.isNotBlank())
        val threadNumber = AtomicInteger()
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
                BackpressurePolicy(closed),
            )
    }

    fun dispatch(
        onDiscard: () -> Unit,
        block: () -> Unit,
    ): ChromeProxyAdmissionResult {
        if (closed.get()) {
            runCatching(onDiscard)
            return ChromeProxyAdmissionResult.Closed
        }

        val task = AdmittedTask(onDiscard, block)
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

    private class BackpressurePolicy(
        private val closed: AtomicBoolean,
    ) : RejectedExecutionHandler {
        override fun rejectedExecution(
            runnable: Runnable,
            executor: ThreadPoolExecutor,
        ) {
            if (closed.get() || executor.isShutdown) {
                throw RejectedExecutionException("Proxy admission is closed")
            }
            try {
                executor.queue.put(runnable)
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw RejectedExecutionException("Proxy admission interrupted", error)
            }
            if ((closed.get() || executor.isShutdown) && executor.remove(runnable)) {
                (runnable as? AdmittedTask)?.discard()
                throw RejectedExecutionException("Proxy admission closed during enqueue")
            }
        }
    }

    private class AdmittedTask(
        private val onDiscard: () -> Unit,
        private val block: () -> Unit,
    ) : Runnable {
        private val finished = AtomicBoolean(false)

        override fun run() {
            try {
                block()
            } finally {
                finished.compareAndSet(false, true)
            }
        }

        fun discard() {
            if (!finished.compareAndSet(false, true)) return
            runCatching(onDiscard)
        }
    }
}
