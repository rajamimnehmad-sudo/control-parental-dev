package com.contentfilter.core.domain.chrome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class ChromeMediaShieldActiveDocumentHandshakeTest {
    @Test
    fun `all four phases have exact typed terminal results and bounded metrics`() {
        ChromeMediaShieldActiveDocumentHandshake().use { handshake ->
            handshake.register { request, completion ->
                when (request) {
                    is ChromeMediaShieldActiveDocumentRequest.Hello -> completion.issueChallenge(challenge())
                    is ChromeMediaShieldActiveDocumentRequest.Prove -> completion.acceptProof()
                    is ChromeMediaShieldActiveDocumentRequest.Present -> completion.acceptPresentation()
                    is ChromeMediaShieldActiveDocumentRequest.Revoke -> completion.acceptRevocation()
                }
            }.use {
                val issued = handshake.await(ChromeMediaShieldActiveDocumentRequest.Hello(claim()))
                assertTrue(issued is ChromeMediaShieldActiveDocumentHandshakeResult.ChallengeIssued)
                assertEquals(
                    challenge(),
                    (issued as ChromeMediaShieldActiveDocumentHandshakeResult.ChallengeIssued).challenge,
                )
                assertEquals(
                    ChromeMediaShieldActiveDocumentHandshakeResult.ProofAccepted,
                    handshake.await(ChromeMediaShieldActiveDocumentRequest.Prove(claim(), challenge())),
                )
                assertEquals(
                    ChromeMediaShieldActiveDocumentHandshakeResult.PresentationAccepted,
                    handshake.await(ChromeMediaShieldActiveDocumentRequest.Present(claim(), challenge())),
                )
                assertEquals(
                    ChromeMediaShieldActiveDocumentHandshakeResult.Revoked,
                    handshake.await(ChromeMediaShieldActiveDocumentRequest.Revoke(claim(), challenge())),
                )

                val snapshot = handshake.snapshot()
                assertEquals(4L, snapshot.requests)
                assertEquals(1L, snapshot.helloRequests)
                assertEquals(1L, snapshot.proveRequests)
                assertEquals(1L, snapshot.presentRequests)
                assertEquals(1L, snapshot.revokeRequests)
                assertEquals(1L, snapshot.challengesIssued)
                assertEquals(1L, snapshot.proofsAccepted)
                assertEquals(1L, snapshot.presentationsAccepted)
                assertEquals(1L, snapshot.revocationsAccepted)
                assertEquals(0, snapshot.pendingRequests)
            }
        }
    }

    @Test
    fun `challenge and result string representations never disclose the secret`() {
        val challenge = challenge()
        val result = ChromeMediaShieldActiveDocumentHandshakeResult.ChallengeIssued(challenge)

        assertFalse(challenge.toString().contains(challenge.encoded))
        assertFalse(result.toString().contains(challenge.encoded))
        assertTrue(challenge.toString().contains("redacted"))
        assertThrows(IllegalArgumentException::class.java) {
            ChromeMediaShieldActiveDocumentChallenge.fromEncoded("not valid because spaces")
        }
    }

    @Test
    fun `completion for the wrong phase terminates rejected and stays one shot`() {
        val held = AtomicReference<ChromeMediaShieldActiveDocumentHandshakeCompletion>()
        ChromeMediaShieldActiveDocumentHandshake().use { handshake ->
            handshake.register { _, completion ->
                held.set(completion)
                assertTrue(completion.acceptPresentation())
            }.use {
                assertEquals(
                    ChromeMediaShieldActiveDocumentHandshakeResult.Rejected,
                    handshake.await(ChromeMediaShieldActiveDocumentRequest.Hello(claim())),
                )
                assertFalse(checkNotNull(held.get()).reject())
                assertFalse(checkNotNull(held.get()).issueChallenge(challenge()))
                assertEquals(1L, handshake.snapshot().rejected)
            }
        }
    }

    @Test
    fun `only one request can be pending and capacity rejection does not queue`() {
        val listener = HoldingListener()
        ChromeMediaShieldActiveDocumentHandshake().use { handshake ->
            handshake.register(listener).use {
                val executor = Executors.newSingleThreadExecutor()
                try {
                    val first =
                        executor.submit<ChromeMediaShieldActiveDocumentHandshakeResult> {
                            handshake.await(ChromeMediaShieldActiveDocumentRequest.Hello(claim()))
                        }
                    assertTrue(listener.dispatched.await(TestWaitMillis, TimeUnit.MILLISECONDS))

                    assertEquals(
                        ChromeMediaShieldActiveDocumentHandshakeResult.Unavailable,
                        handshake.await(ChromeMediaShieldActiveDocumentRequest.Prove(claim(), challenge())),
                    )
                    assertEquals(1, handshake.snapshot().pendingRequests)
                    assertTrue(listener.completion().reject())
                    assertEquals(
                        ChromeMediaShieldActiveDocumentHandshakeResult.Rejected,
                        first.get(TestWaitMillis, TimeUnit.MILLISECONDS),
                    )
                    assertEquals(0, handshake.snapshot().pendingRequests)
                    assertEquals(1L, handshake.snapshot().unavailable)
                } finally {
                    executor.shutdownNow()
                }
            }
        }
    }

    @Test
    fun `only hello from another generation supersedes a pending phase`() {
        val presentDispatched = CountDownLatch(1)
        val presentCompletion = AtomicReference<ChromeMediaShieldActiveDocumentHandshakeCompletion>()
        ChromeMediaShieldActiveDocumentHandshake().use { handshake ->
            handshake.register { request, completion ->
                when (request) {
                    is ChromeMediaShieldActiveDocumentRequest.Hello -> completion.issueChallenge(challenge())
                    is ChromeMediaShieldActiveDocumentRequest.Present -> {
                        presentCompletion.set(completion)
                        presentDispatched.countDown()
                    }
                    else -> error("Only PRESENT A and HELLO B may reach the listener")
                }
            }.use {
                val executor = Executors.newSingleThreadExecutor()
                try {
                    val generationA = claim(documentSequence = 11L)
                    val generationB = claim(documentSequence = 12L)
                    val oldPresent =
                        executor.submit<ChromeMediaShieldActiveDocumentHandshakeResult> {
                            handshake.await(
                                ChromeMediaShieldActiveDocumentRequest.Present(generationA, challenge()),
                            )
                        }
                    assertTrue(presentDispatched.await(TestWaitMillis, TimeUnit.MILLISECONDS))

                    assertEquals(
                        ChromeMediaShieldActiveDocumentHandshakeResult.Unavailable,
                        handshake.await(ChromeMediaShieldActiveDocumentRequest.Prove(generationB, challenge())),
                    )
                    assertEquals(
                        ChromeMediaShieldActiveDocumentHandshakeResult.Unavailable,
                        handshake.await(ChromeMediaShieldActiveDocumentRequest.Present(generationB, challenge())),
                    )
                    assertEquals(
                        ChromeMediaShieldActiveDocumentHandshakeResult.Unavailable,
                        handshake.await(ChromeMediaShieldActiveDocumentRequest.Revoke(generationB, challenge())),
                    )
                    assertEquals(
                        ChromeMediaShieldActiveDocumentHandshakeResult.Unavailable,
                        handshake.await(ChromeMediaShieldActiveDocumentRequest.Hello(generationA)),
                    )

                    assertEquals(
                        ChromeMediaShieldActiveDocumentHandshakeResult.ChallengeIssued(challenge()),
                        handshake.await(ChromeMediaShieldActiveDocumentRequest.Hello(generationB)),
                    )
                    assertEquals(
                        ChromeMediaShieldActiveDocumentHandshakeResult.Unavailable,
                        oldPresent.get(TestWaitMillis, TimeUnit.MILLISECONDS),
                    )
                    assertFalse(checkNotNull(presentCompletion.get()).acceptPresentation())
                    assertEquals(0, handshake.snapshot().pendingRequests)
                } finally {
                    executor.shutdownNow()
                }
            }
        }
    }

    @Test
    fun `listener replacement supersedes its generation and makes old completion inert`() {
        val oldListener = HoldingListener()
        ChromeMediaShieldActiveDocumentHandshake().use { handshake ->
            val oldRegistration = handshake.register(oldListener)
            val executor = Executors.newSingleThreadExecutor()
            try {
                val oldResult =
                    executor.submit<ChromeMediaShieldActiveDocumentHandshakeResult> {
                        handshake.await(ChromeMediaShieldActiveDocumentRequest.Hello(claim()))
                    }
                assertTrue(oldListener.dispatched.await(TestWaitMillis, TimeUnit.MILLISECONDS))

                val newRegistration =
                    handshake.register { _, completion -> completion.issueChallenge(challenge()) }
                assertEquals(
                    ChromeMediaShieldActiveDocumentHandshakeResult.Unavailable,
                    oldResult.get(TestWaitMillis, TimeUnit.MILLISECONDS),
                )
                assertFalse(oldListener.completion().reject())

                // Closing an obsolete handle cannot unregister the replacement generation.
                oldRegistration.close()
                assertEquals(
                    ChromeMediaShieldActiveDocumentHandshakeResult.ChallengeIssued(challenge()),
                    handshake.await(ChromeMediaShieldActiveDocumentRequest.Hello(claim())),
                )
                assertTrue(handshake.snapshot().listenerRegistered)
                assertEquals(2L, handshake.snapshot().listenerGeneration)
                assertEquals(1L, handshake.snapshot().listenerSupersessions)
                newRegistration.close()
                assertFalse(handshake.snapshot().listenerRegistered)
            } finally {
                oldRegistration.close()
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun `timeout wins fail closed and all late completion methods are inert`() {
        val listener = HoldingListener()
        val transportCancellations = AtomicInteger()
        ChromeMediaShieldActiveDocumentHandshake(waitTimeoutMillis = ShortTimeoutMillis).use { handshake ->
            handshake.register { request, completion ->
                listener.onActiveDocumentRequest(request, completion)
                assertEquals(
                    ChromeMediaShieldActiveDocumentTransportCancellationRegistration.Registered,
                    completion.onTransportCancelled { transportCancellations.incrementAndGet() },
                )
            }.use {
                assertEquals(
                    ChromeMediaShieldActiveDocumentHandshakeResult.TimedOut,
                    handshake.await(ChromeMediaShieldActiveDocumentRequest.Hello(claim())),
                )
                assertEquals(1, transportCancellations.get())
                assertEquals(
                    ChromeMediaShieldActiveDocumentTransportCancellationRegistration.AlreadyCancelled,
                    listener.completion().onTransportCancelled { transportCancellations.incrementAndGet() },
                )
                assertEquals(2, transportCancellations.get())
                assertFalse(listener.completion().issueChallenge(challenge()))
                assertFalse(listener.completion().reject())
                assertEquals(0, handshake.snapshot().pendingRequests)
                assertEquals(1L, handshake.snapshot().timedOut)
            }
        }
    }

    @Test
    fun `normal phase completion clears transport cancellation without invoking it`() {
        val transportCancellations = AtomicInteger()
        ChromeMediaShieldActiveDocumentHandshake().use { handshake ->
            handshake.register { _, completion ->
                assertEquals(
                    ChromeMediaShieldActiveDocumentTransportCancellationRegistration.Registered,
                    completion.onTransportCancelled { transportCancellations.incrementAndGet() },
                )
                completion.issueChallenge(challenge())
            }.use {
                assertTrue(
                    handshake.await(ChromeMediaShieldActiveDocumentRequest.Hello(claim())) is
                        ChromeMediaShieldActiveDocumentHandshakeResult.ChallengeIssued,
                )
                assertEquals(0, transportCancellations.get())
            }
        }
    }

    @Test
    fun `transport cancellation before callback registration is reported and invoked immediately`() {
        val listenerEntered = CountDownLatch(1)
        val allowRegistration = CountDownLatch(1)
        val cancellationRegistration =
            AtomicReference<ChromeMediaShieldActiveDocumentTransportCancellationRegistration>()
        val transportCancellations = AtomicInteger()
        val handshake = ChromeMediaShieldActiveDocumentHandshake()
        val listenerRegistration =
            handshake.register { _, completion ->
                listenerEntered.countDown()
                assertTrue(allowRegistration.await(TestWaitMillis, TimeUnit.MILLISECONDS))
                cancellationRegistration.set(
                    completion.onTransportCancelled { transportCancellations.incrementAndGet() },
                )
            }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val pending =
                executor.submit<ChromeMediaShieldActiveDocumentHandshakeResult> {
                    handshake.await(ChromeMediaShieldActiveDocumentRequest.Hello(claim()))
                }
            assertTrue(listenerEntered.await(TestWaitMillis, TimeUnit.MILLISECONDS))

            listenerRegistration.close()
            allowRegistration.countDown()

            assertEquals(
                ChromeMediaShieldActiveDocumentHandshakeResult.Unavailable,
                pending.get(TestWaitMillis, TimeUnit.MILLISECONDS),
            )
            assertEquals(
                ChromeMediaShieldActiveDocumentTransportCancellationRegistration.AlreadyCancelled,
                cancellationRegistration.get(),
            )
            assertEquals(1, transportCancellations.get())
        } finally {
            allowRegistration.countDown()
            listenerRegistration.close()
            handshake.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun `callback registration after normal completion is already completed and stays inert`() {
        val cancellationRegistration =
            AtomicReference<ChromeMediaShieldActiveDocumentTransportCancellationRegistration>()
        val transportCancellations = AtomicInteger()
        ChromeMediaShieldActiveDocumentHandshake().use { handshake ->
            handshake.register { _, completion ->
                assertTrue(completion.issueChallenge(challenge()))
                cancellationRegistration.set(
                    completion.onTransportCancelled { transportCancellations.incrementAndGet() },
                )
            }.use {
                assertEquals(
                    ChromeMediaShieldActiveDocumentHandshakeResult.ChallengeIssued(challenge()),
                    handshake.await(ChromeMediaShieldActiveDocumentRequest.Hello(claim())),
                )
                assertEquals(
                    ChromeMediaShieldActiveDocumentTransportCancellationRegistration.AlreadyCompleted,
                    cancellationRegistration.get(),
                )
                assertEquals(0, transportCancellations.get())
            }
        }
    }

    @Test
    fun `thread interruption is terminal and preserves interrupted status`() {
        val listener = HoldingListener()
        ChromeMediaShieldActiveDocumentHandshake().use { handshake ->
            handshake.register(listener).use {
                val result = AtomicReference<ChromeMediaShieldActiveDocumentHandshakeResult>()
                val interrupted = AtomicBoolean(false)
                val thread =
                    Thread {
                        result.set(handshake.await(ChromeMediaShieldActiveDocumentRequest.Hello(claim())))
                        interrupted.set(Thread.currentThread().isInterrupted)
                    }
                thread.start()
                assertTrue(listener.dispatched.await(TestWaitMillis, TimeUnit.MILLISECONDS))

                thread.interrupt()
                thread.join(TestWaitMillis)

                assertFalse(thread.isAlive)
                assertEquals(ChromeMediaShieldActiveDocumentHandshakeResult.Interrupted, result.get())
                assertTrue(interrupted.get())
                assertFalse(listener.completion().issueChallenge(challenge()))
                assertEquals(1L, handshake.snapshot().interrupted)
            }
        }
    }

    @Test
    fun `unregister close and listener exception are unavailable or rejected without leaks`() {
        val listener = HoldingListener()
        val handshake = ChromeMediaShieldActiveDocumentHandshake()
        val registration = handshake.register(listener)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val pending =
                executor.submit<ChromeMediaShieldActiveDocumentHandshakeResult> {
                    handshake.await(ChromeMediaShieldActiveDocumentRequest.Hello(claim()))
                }
            assertTrue(listener.dispatched.await(TestWaitMillis, TimeUnit.MILLISECONDS))
            registration.close()
            assertEquals(
                ChromeMediaShieldActiveDocumentHandshakeResult.Unavailable,
                pending.get(TestWaitMillis, TimeUnit.MILLISECONDS),
            )
            assertFalse(listener.completion().reject())

            handshake.register { _, _ -> error("listener failure") }.use {
                assertEquals(
                    ChromeMediaShieldActiveDocumentHandshakeResult.Rejected,
                    handshake.await(ChromeMediaShieldActiveDocumentRequest.Hello(claim())),
                )
            }

            handshake.close()
            assertEquals(
                ChromeMediaShieldActiveDocumentHandshakeResult.Unavailable,
                handshake.await(ChromeMediaShieldActiveDocumentRequest.Hello(claim())),
            )
            assertTrue(handshake.snapshot().closed)
            assertEquals(0, handshake.snapshot().pendingRequests)
        } finally {
            registration.close()
            handshake.close()
            executor.shutdownNow()
        }
    }

    private class HoldingListener : ChromeMediaShieldActiveDocumentHandshakeListener {
        val dispatched = CountDownLatch(1)
        private val held = AtomicReference<ChromeMediaShieldActiveDocumentHandshakeCompletion>()

        override fun onActiveDocumentRequest(
            request: ChromeMediaShieldActiveDocumentRequest,
            completion: ChromeMediaShieldActiveDocumentHandshakeCompletion,
        ) {
            held.set(completion)
            dispatched.countDown()
        }

        fun completion(): ChromeMediaShieldActiveDocumentHandshakeCompletion = checkNotNull(held.get())
    }

    private fun challenge() = ChromeMediaShieldActiveDocumentChallenge.fromEncoded("c".repeat(43))

    private fun claim(
        documentSequence: Long = 11L,
        lifecycleSequence: Long = 3L,
    ): ChromeMediaShieldReadyClaim =
        ChromeMediaShieldReadyClaim(
            identity =
                ChromeMediaShieldDocumentIdentity(
                    protectionSessionId = "session-h19-active",
                    policyEpoch = 19L,
                    navigationSequence = 7L,
                    documentSequence = documentSequence,
                    tokenDigest = "a".repeat(64),
                    topLevel = true,
                ),
            lifecycleSequence = lifecycleSequence,
        )

    private companion object {
        const val TestWaitMillis = 2_000L
        const val ShortTimeoutMillis = 10L
    }
}
