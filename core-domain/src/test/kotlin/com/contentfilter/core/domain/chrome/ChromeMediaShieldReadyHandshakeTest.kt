package com.contentfilter.core.domain.chrome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class ChromeMediaShieldReadyHandshakeTest {
    @Test
    fun `accepted is impossible until listener explicitly confirms opaque commit`() {
        ChromeMediaShieldReadyHandshake().use { handshake ->
            val listener = HoldingListener()
            handshake.register(listener).use {
                val executor = Executors.newSingleThreadExecutor()
                try {
                    val result =
                        executor.submit<ChromeMediaShieldReadyHandshakeResult> {
                            handshake.awaitOpaqueCommit(claim(documentSequence = 1L))
                        }
                    assertTrue(listener.dispatched.await(TestWaitMillis, TimeUnit.MILLISECONDS))
                    assertFalse(result.isDone)

                    assertTrue(listener.completion().acceptAfterOpaqueCommit())
                    assertEquals(
                        ChromeMediaShieldReadyHandshakeResult.Accepted,
                        result.get(TestWaitMillis, TimeUnit.MILLISECONDS),
                    )
                    assertFalse(listener.completion().acceptAfterOpaqueCommit())
                    assertFalse(listener.completion().reject())
                    assertEquals(0, handshake.snapshot().pendingRequests)
                } finally {
                    executor.shutdownNow()
                }
            }
        }
    }

    @Test
    fun `explicit listener rejection is terminal and fail closed`() {
        ChromeMediaShieldReadyHandshake().use { handshake ->
            handshake.register { _, completion -> completion.reject() }.use {
                assertEquals(
                    ChromeMediaShieldReadyHandshakeResult.Rejected,
                    handshake.awaitOpaqueCommit(claim(documentSequence = 1L)),
                )
            }
        }
    }

    @Test
    fun `no listener and capacity exhaustion are unavailable`() {
        val handshake = ChromeMediaShieldReadyHandshake(maximumPendingRequests = 1)
        assertEquals(
            ChromeMediaShieldReadyHandshakeResult.Unavailable,
            handshake.awaitOpaqueCommit(claim(documentSequence = 1L)),
        )

        val listener = HoldingListener()
        handshake.register(listener).use {
            val executor = Executors.newSingleThreadExecutor()
            try {
                val first =
                    executor.submit<ChromeMediaShieldReadyHandshakeResult> {
                        handshake.awaitOpaqueCommit(claim(documentSequence = 1L))
                    }
                assertTrue(listener.dispatched.await(TestWaitMillis, TimeUnit.MILLISECONDS))

                assertEquals(
                    ChromeMediaShieldReadyHandshakeResult.Unavailable,
                    handshake.awaitOpaqueCommit(claim(documentSequence = 2L)),
                )
                assertTrue(listener.completion().reject())
                assertEquals(
                    ChromeMediaShieldReadyHandshakeResult.Rejected,
                    first.get(TestWaitMillis, TimeUnit.MILLISECONDS),
                )
            } finally {
                executor.shutdownNow()
            }
        }
        handshake.close()
    }

    @Test
    fun `timeout wins fail closed and makes late callback inert`() {
        val listener = HoldingListener()
        ChromeMediaShieldReadyHandshake(waitTimeoutMillis = ShortTimeoutMillis).use { handshake ->
            handshake.register(listener).use {
                assertEquals(
                    ChromeMediaShieldReadyHandshakeResult.TimedOut,
                    handshake.awaitOpaqueCommit(claim(documentSequence = 1L)),
                )
                assertFalse(listener.completion().acceptAfterOpaqueCommit())
                assertEquals(0, handshake.snapshot().pendingRequests)
            }
        }
    }

    @Test
    fun `unregister fails pending request closed and late callback is inert`() {
        ChromeMediaShieldReadyHandshake().use { handshake ->
            val listener = HoldingListener()
            val registration = handshake.register(listener)
            val executor = Executors.newSingleThreadExecutor()
            try {
                val result =
                    executor.submit<ChromeMediaShieldReadyHandshakeResult> {
                        handshake.awaitOpaqueCommit(claim(documentSequence = 1L))
                    }
                assertTrue(listener.dispatched.await(TestWaitMillis, TimeUnit.MILLISECONDS))

                registration.close()

                assertEquals(
                    ChromeMediaShieldReadyHandshakeResult.Unavailable,
                    result.get(TestWaitMillis, TimeUnit.MILLISECONDS),
                )
                assertFalse(listener.completion().acceptAfterOpaqueCommit())
                assertFalse(handshake.snapshot().listenerRegistered)
            } finally {
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun `coordinator close fails all independent requests and rejects late callbacks`() {
        val handshake = ChromeMediaShieldReadyHandshake()
        val listener = QueueingListener(expectedRequests = 2)
        handshake.register(listener)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first =
                executor.submit<ChromeMediaShieldReadyHandshakeResult> {
                    handshake.awaitOpaqueCommit(claim(documentSequence = 1L))
                }
            val second =
                executor.submit<ChromeMediaShieldReadyHandshakeResult> {
                    handshake.awaitOpaqueCommit(claim(documentSequence = 2L))
                }
            assertTrue(listener.dispatched.await(TestWaitMillis, TimeUnit.MILLISECONDS))

            handshake.close()

            assertEquals(
                ChromeMediaShieldReadyHandshakeResult.Unavailable,
                first.get(TestWaitMillis, TimeUnit.MILLISECONDS),
            )
            assertEquals(
                ChromeMediaShieldReadyHandshakeResult.Unavailable,
                second.get(TestWaitMillis, TimeUnit.MILLISECONDS),
            )
            listener.completions.forEach { completion ->
                assertFalse(completion.acceptAfterOpaqueCommit())
            }
            assertEquals(
                ChromeMediaShieldReadyHandshakeSnapshot(
                    listenerRegistered = false,
                    pendingRequests = 0,
                    closed = true,
                ),
                handshake.snapshot(),
            )
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `thread interruption cancels wait and preserves interrupted status`() {
        ChromeMediaShieldReadyHandshake().use { handshake ->
            val listener = HoldingListener()
            handshake.register(listener).use {
                val result = AtomicReference<ChromeMediaShieldReadyHandshakeResult>()
                val interrupted = AtomicBoolean(false)
                val thread =
                    Thread {
                        result.set(handshake.awaitOpaqueCommit(claim(documentSequence = 1L)))
                        interrupted.set(Thread.currentThread().isInterrupted)
                    }
                thread.start()
                assertTrue(listener.dispatched.await(TestWaitMillis, TimeUnit.MILLISECONDS))

                thread.interrupt()
                thread.join(TestWaitMillis)

                assertFalse(thread.isAlive)
                assertEquals(ChromeMediaShieldReadyHandshakeResult.Interrupted, result.get())
                assertTrue(interrupted.get())
                assertFalse(listener.completion().acceptAfterOpaqueCommit())
            }
        }
    }

    @Test
    fun `requests complete independently and listener failure rejects only its request`() {
        ChromeMediaShieldReadyHandshake().use { handshake ->
            var invocation = 0
            handshake.register { _, completion ->
                invocation += 1
                if (invocation == 1) {
                    completion.acceptAfterOpaqueCommit()
                } else {
                    error("listener failure")
                }
            }.use {
                assertEquals(
                    ChromeMediaShieldReadyHandshakeResult.Accepted,
                    handshake.awaitOpaqueCommit(claim(documentSequence = 1L)),
                )
                assertEquals(
                    ChromeMediaShieldReadyHandshakeResult.Rejected,
                    handshake.awaitOpaqueCommit(claim(documentSequence = 2L)),
                )
            }
        }
    }

    @Test
    fun `only one live listener can own the bridge`() {
        ChromeMediaShieldReadyHandshake().use { handshake ->
            val registration = handshake.register { _, completion -> completion.reject() }
            assertThrows(IllegalStateException::class.java) {
                handshake.register { _, completion -> completion.reject() }
            }
            registration.close()
            handshake.register { _, completion -> completion.reject() }.close()
        }
    }

    private class HoldingListener : ChromeMediaShieldReadyHandshakeListener {
        val dispatched = CountDownLatch(1)
        private val held = AtomicReference<ChromeMediaShieldReadyHandshakeCompletion>()

        override fun onReadyClaim(
            claim: ChromeMediaShieldReadyClaim,
            completion: ChromeMediaShieldReadyHandshakeCompletion,
        ) {
            held.set(completion)
            dispatched.countDown()
        }

        fun completion(): ChromeMediaShieldReadyHandshakeCompletion = checkNotNull(held.get())
    }

    private class QueueingListener(expectedRequests: Int) : ChromeMediaShieldReadyHandshakeListener {
        val dispatched = CountDownLatch(expectedRequests)
        val completions = CopyOnWriteArrayList<ChromeMediaShieldReadyHandshakeCompletion>()

        override fun onReadyClaim(
            claim: ChromeMediaShieldReadyClaim,
            completion: ChromeMediaShieldReadyHandshakeCompletion,
        ) {
            completions += completion
            dispatched.countDown()
        }
    }

    private fun claim(documentSequence: Long): ChromeMediaShieldReadyClaim =
        ChromeMediaShieldReadyClaim(
            identity =
                ChromeMediaShieldDocumentIdentity(
                    protectionSessionId = "session-h19",
                    policyEpoch = 19L,
                    navigationSequence = documentSequence,
                    documentSequence = documentSequence,
                    tokenDigest = "a".repeat(64),
                    topLevel = true,
                ),
            lifecycleSequence = 1L,
        )

    private companion object {
        const val TestWaitMillis = 2_000L
        const val ShortTimeoutMillis = 10L
    }
}
