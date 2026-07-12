package com.contentfilter.feature.vpn.dns

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BoundedDnsRequestDispatcherTest {
    @Test
    fun `slow DNS request does not block an independent request`() =
        runBlocking {
            val slowStarted = CompletableDeferred<Unit>()
            val releaseSlow = CompletableDeferred<Unit>()
            val fastCompleted = CompletableDeferred<Unit>()
            val dispatcher =
                BoundedDnsRequestDispatcher<Int>(
                    scope = this,
                    workerCount = 2,
                    queueCapacity = 4,
                    handler = { request ->
                        if (request == SlowRequest) {
                            slowStarted.complete(Unit)
                            releaseSlow.await()
                        } else {
                            fastCompleted.complete(Unit)
                        }
                    },
                    onFailure = { throw it },
                )

            try {
                dispatcher.submit(SlowRequest)
                slowStarted.await()
                dispatcher.submit(FastRequest)

                withTimeout(FastRequestTimeoutMillis) { fastCompleted.await() }
            } finally {
                releaseSlow.complete(Unit)
                dispatcher.cancel()
            }
        }

    @Test
    fun `worker count bounds concurrent DNS work`() =
        runBlocking {
            val active = AtomicInteger(0)
            val maximum = AtomicInteger(0)
            val workersStarted = CompletableDeferred<Unit>()
            val releaseWorkers = CompletableDeferred<Unit>()
            val dispatcher =
                BoundedDnsRequestDispatcher<Int>(
                    scope = this,
                    workerCount = WorkerCount,
                    queueCapacity = RequestCount,
                    handler = {
                        val current = active.incrementAndGet()
                        maximum.updateAndGet { previous -> maxOf(previous, current) }
                        if (current == WorkerCount) workersStarted.complete(Unit)
                        try {
                            releaseWorkers.await()
                        } finally {
                            active.decrementAndGet()
                        }
                    },
                    onFailure = { throw it },
                )

            try {
                repeat(RequestCount) { dispatcher.submit(it) }
                withTimeout(WorkersStartTimeoutMillis) { workersStarted.await() }
                delay(ConcurrencyObservationMillis)

                assertEquals(WorkerCount, maximum.get())
                assertTrue(active.get() <= WorkerCount)
            } finally {
                releaseWorkers.complete(Unit)
                dispatcher.cancel()
            }
        }

    private companion object {
        const val SlowRequest = 1
        const val FastRequest = 2
        const val WorkerCount = 3
        const val RequestCount = 12
        const val FastRequestTimeoutMillis = 500L
        const val WorkersStartTimeoutMillis = 1_000L
        const val ConcurrencyObservationMillis = 50L
    }
}
