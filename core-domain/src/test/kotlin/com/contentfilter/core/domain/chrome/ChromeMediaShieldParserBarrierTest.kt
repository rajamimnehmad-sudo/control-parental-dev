package com.contentfilter.core.domain.chrome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class ChromeMediaShieldParserBarrierTest {
    @Test
    fun `ready completes one request and records a terminal snapshot`() {
        ChromeMediaShieldParserBarrier().use { barrier ->
            barrier.register { completion -> assertTrue(completion.ready()) }.use {
                assertEquals(ChromeMediaShieldParserBarrierResult.Ready, barrier.await())
                assertEquals(
                    ChromeMediaShieldParserBarrierSnapshot(
                        listenerRegistered = true,
                        pendingRequests = 0,
                        requests = 1L,
                        ready = 1L,
                        rejected = 0L,
                        superseded = 0L,
                        unavailable = 0L,
                        timedOut = 0L,
                        interrupted = 0L,
                    ),
                    barrier.snapshot(),
                )
            }
        }
    }

    @Test
    fun `await stays pending until the listener drives an explicit state transition`() {
        ChromeMediaShieldParserBarrier().use { barrier ->
            val listener = HoldingListener()
            barrier.register(listener).use {
                val executor = Executors.newSingleThreadExecutor()
                try {
                    val result = executor.submit<ChromeMediaShieldParserBarrierResult> { barrier.await() }
                    assertTrue(listener.dispatched.await(TestWaitMillis, TimeUnit.MILLISECONDS))
                    assertFalse(result.isDone)
                    assertEquals(1, barrier.snapshot().pendingRequests)

                    assertTrue(listener.completion().ready())

                    assertEquals(
                        ChromeMediaShieldParserBarrierResult.Ready,
                        result.get(TestWaitMillis, TimeUnit.MILLISECONDS),
                    )
                    assertEquals(0, barrier.snapshot().pendingRequests)
                } finally {
                    executor.shutdownNow()
                }
            }
        }
    }

    @Test
    fun `timeout invokes transport cancellation once and makes late completion inert`() {
        val listener = HoldingListener()
        val transportCancellations = AtomicInteger()
        ChromeMediaShieldParserBarrier(waitTimeoutMillis = ShortTimeoutMillis).use { barrier ->
            barrier.register { completion ->
                listener.onParserBarrierRequest(completion)
                assertEquals(
                    ChromeMediaShieldActiveDocumentTransportCancellationRegistration.Registered,
                    completion.onTransportCancelled { transportCancellations.incrementAndGet() },
                )
            }.use {
                assertEquals(ChromeMediaShieldParserBarrierResult.TimedOut, barrier.await())
                assertEquals(1, transportCancellations.get())
                assertFalse(listener.completion().ready())
                assertFalse(listener.completion().reject())
                assertEquals(
                    ChromeMediaShieldActiveDocumentTransportCancellationRegistration.AlreadyCancelled,
                    listener.completion().onTransportCancelled { transportCancellations.incrementAndGet() },
                )
                assertEquals(2, transportCancellations.get())
                assertEquals(0, barrier.snapshot().pendingRequests)
                assertEquals(1L, barrier.snapshot().timedOut)
            }
        }
    }

    @Test
    fun `fifth request supersedes the oldest and keeps the bounded capacity reusable`() {
        ChromeMediaShieldParserBarrier().use { barrier ->
            val listener = QueueingListener()
            barrier.register(listener).use {
                val executor = Executors.newFixedThreadPool(5)
                try {
                    val firstBatch =
                        List(4) {
                            executor.submit<ChromeMediaShieldParserBarrierResult> { barrier.await() }.also {
                                assertTrue(listener.awaitAdditional(1))
                            }
                        }
                    val fifth = executor.submit<ChromeMediaShieldParserBarrierResult> { barrier.await() }
                    assertTrue(listener.awaitAdditional(1))

                    assertEquals(
                        ChromeMediaShieldParserBarrierResult.Superseded,
                        firstBatch.first().get(TestWaitMillis, TimeUnit.MILLISECONDS),
                    )
                    assertEquals(4, barrier.snapshot().pendingRequests)
                    assertEquals(5, listener.completionCount())
                    assertFalse(listener.completion(0).ready())

                    (1..4).forEach { assertTrue(listener.completion(it).ready()) }
                    firstBatch.drop(1).plus(fifth).forEach { result ->
                        assertEquals(
                            ChromeMediaShieldParserBarrierResult.Ready,
                            result.get(TestWaitMillis, TimeUnit.MILLISECONDS),
                        )
                    }

                    val next = executor.submit<ChromeMediaShieldParserBarrierResult> { barrier.await() }
                    assertTrue(listener.awaitAdditional(1))
                    assertTrue(listener.completion(5).reject())
                    assertEquals(
                        ChromeMediaShieldParserBarrierResult.Rejected,
                        next.get(TestWaitMillis, TimeUnit.MILLISECONDS),
                    )

                    val snapshot = barrier.snapshot()
                    assertEquals(6L, snapshot.requests)
                    assertEquals(4L, snapshot.ready)
                    assertEquals(1L, snapshot.rejected)
                    assertEquals(1L, snapshot.superseded)
                    assertEquals(0L, snapshot.unavailable)
                    assertEquals(0, snapshot.pendingRequests)
                } finally {
                    executor.shutdownNow()
                }
            }
        }
    }

    @Test
    fun `one hundred requests retain only the latest four and close every superseded transport`() {
        val transportCancellations = AtomicInteger()
        ChromeMediaShieldParserBarrier(waitTimeoutMillis = StressWaitMillis).use { barrier ->
            val listener = QueueingListener(transportCancellations)
            barrier.register(listener).use {
                val executor = Executors.newFixedThreadPool(8)
                try {
                    val results =
                        List(100) {
                            executor.submit<ChromeMediaShieldParserBarrierResult> { barrier.await() }
                        }
                    assertTrue(listener.awaitAdditional(100, StressWaitMillis))
                    assertEquals(4, barrier.snapshot().pendingRequests)
                    assertEquals(4, (0 until listener.completionCount()).count { listener.completion(it).ready() })

                    val terminals =
                        results.map { result -> result.get(StressWaitMillis, TimeUnit.MILLISECONDS) }
                    assertEquals(96, terminals.count { it == ChromeMediaShieldParserBarrierResult.Superseded })
                    assertEquals(4, terminals.count { it == ChromeMediaShieldParserBarrierResult.Ready })
                    assertEquals(96, transportCancellations.get())
                    assertEquals(0, barrier.snapshot().pendingRequests)
                    assertEquals(96L, barrier.snapshot().superseded)
                    assertEquals(4L, barrier.snapshot().ready)
                } finally {
                    executor.shutdownNow()
                }
            }
        }
    }

    @Test
    fun `unregister cancels every pending transport and rejects every late completion`() {
        ChromeMediaShieldParserBarrier().use { barrier ->
            val transportCancellations = AtomicInteger()
            val listener = QueueingListener(transportCancellations)
            val registration = barrier.register(listener)
            val executor = Executors.newFixedThreadPool(4)
            try {
                val results = List(4) { executor.submit<ChromeMediaShieldParserBarrierResult> { barrier.await() } }
                assertTrue(listener.awaitAdditional(4))

                registration.close()

                results.forEach { result ->
                    assertEquals(
                        ChromeMediaShieldParserBarrierResult.Unavailable,
                        result.get(TestWaitMillis, TimeUnit.MILLISECONDS),
                    )
                }
                assertEquals(4, transportCancellations.get())
                repeat(4) { index ->
                    assertFalse(listener.completion(index).ready())
                    assertFalse(listener.completion(index).reject())
                    assertFalse(listener.completion(index).supersede())
                }
                assertFalse(barrier.snapshot().listenerRegistered)
                assertEquals(0, barrier.snapshot().pendingRequests)
                assertEquals(4L, barrier.snapshot().unavailable)
            } finally {
                registration.close()
                executor.shutdownNow()
            }
        }
    }

    private class HoldingListener : ChromeMediaShieldParserBarrierListener {
        val dispatched = CountDownLatch(1)
        private val held = AtomicReference<ChromeMediaShieldParserBarrierCompletion>()

        override fun onParserBarrierRequest(completion: ChromeMediaShieldParserBarrierCompletion) {
            held.set(completion)
            dispatched.countDown()
        }

        fun completion(): ChromeMediaShieldParserBarrierCompletion = checkNotNull(held.get())
    }

    private class QueueingListener(
        private val transportCancellations: AtomicInteger? = null,
    ) : ChromeMediaShieldParserBarrierListener {
        private val dispatched = Semaphore(0)
        private val completions = mutableListOf<ChromeMediaShieldParserBarrierCompletion>()

        override fun onParserBarrierRequest(completion: ChromeMediaShieldParserBarrierCompletion) {
            transportCancellations?.let { cancellations ->
                completion.onTransportCancelled { cancellations.incrementAndGet() }
            }
            synchronized(completions) {
                completions += completion
                dispatched.release()
            }
        }

        fun awaitAdditional(
            expected: Int,
            waitMillis: Long = TestWaitMillis,
        ): Boolean = dispatched.tryAcquire(expected, waitMillis, TimeUnit.MILLISECONDS)

        fun completionCount(): Int = synchronized(completions) { completions.size }

        fun completion(index: Int): ChromeMediaShieldParserBarrierCompletion =
            synchronized(completions) { completions[index] }
    }

    private companion object {
        const val TestWaitMillis = 2_000L
        const val StressWaitMillis = 10_000L
        const val ShortTimeoutMillis = 25L
    }
}
