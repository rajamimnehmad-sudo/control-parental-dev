package com.contentfilter.user.chromedataplane

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromeProxyAdmissionTest {
    @Test
    fun `saturation blocks admission instead of rejecting connection`() {
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        val releaseSecond = CountDownLatch(1)
        val thirdStarted = CountDownLatch(1)
        val thirdReturned = CountDownLatch(1)
        val thirdResult = AtomicReference<ChromeProxyAdmissionResult>()
        val admission = ChromeProxyAdmission(workerCount = 1, queueCapacity = 1, threadNamePrefix = "test-proxy")

        try {
            assertEquals(
                ChromeProxyAdmissionResult.Accepted,
                admission.dispatch(onDiscard = {}) {
                    firstStarted.countDown()
                    releaseFirst.await()
                },
            )
            assertTrue(firstStarted.await(1, TimeUnit.SECONDS))
            assertEquals(
                ChromeProxyAdmissionResult.Accepted,
                admission.dispatch(onDiscard = {}) {
                    secondStarted.countDown()
                    releaseSecond.await()
                },
            )

            val submitter =
                Thread {
                    try {
                        thirdResult.set(
                            admission.dispatch(onDiscard = {}) {
                                thirdStarted.countDown()
                            },
                        )
                    } finally {
                        thirdReturned.countDown()
                    }
                }.apply { start() }

            assertFalse(thirdReturned.await(100, TimeUnit.MILLISECONDS))
            releaseFirst.countDown()
            assertTrue(secondStarted.await(1, TimeUnit.SECONDS))
            assertTrue(thirdReturned.await(1, TimeUnit.SECONDS))
            assertEquals(ChromeProxyAdmissionResult.Accepted, thirdResult.get())
            releaseSecond.countDown()
            assertTrue(thirdStarted.await(1, TimeUnit.SECONDS))
            submitter.join(1_000)
            assertFalse(submitter.isAlive)
        } finally {
            releaseFirst.countDown()
            releaseSecond.countDown()
            admission.close()
        }
    }

    @Test
    fun `close discards queued work and runs discard hook`() {
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val discarded = CountDownLatch(1)
        val queuedRan = AtomicBoolean(false)
        val admission = ChromeProxyAdmission(workerCount = 1, queueCapacity = 1, threadNamePrefix = "test-proxy")

        try {
            assertEquals(
                ChromeProxyAdmissionResult.Accepted,
                admission.dispatch(onDiscard = {}) {
                    firstStarted.countDown()
                    try {
                        releaseFirst.await()
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                },
            )
            assertTrue(firstStarted.await(1, TimeUnit.SECONDS))
            assertEquals(
                ChromeProxyAdmissionResult.Accepted,
                admission.dispatch(onDiscard = { discarded.countDown() }) {
                    queuedRan.set(true)
                },
            )

            admission.close()

            assertTrue(discarded.await(1, TimeUnit.SECONDS))
            assertFalse(queuedRan.get())
            assertTrue(admission.isShutdown())
        } finally {
            releaseFirst.countDown()
            admission.close()
        }
    }

    @Test
    fun `close unblocks a saturated submit and discards it exactly once`() {
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val queuedDiscarded = AtomicInteger()
        val blockedDiscarded = AtomicInteger()
        val blockedReturned = CountDownLatch(1)
        val blockedResult = AtomicReference<ChromeProxyAdmissionResult>()
        val admission = ChromeProxyAdmission(workerCount = 1, queueCapacity = 1, threadNamePrefix = "test-proxy")

        try {
            assertEquals(
                ChromeProxyAdmissionResult.Accepted,
                admission.dispatch(onDiscard = {}) {
                    firstStarted.countDown()
                    try {
                        releaseFirst.await()
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                },
            )
            assertTrue(firstStarted.await(1, TimeUnit.SECONDS))
            assertEquals(
                ChromeProxyAdmissionResult.Accepted,
                admission.dispatch(onDiscard = { queuedDiscarded.incrementAndGet() }) {},
            )

            val submitter =
                Thread {
                    try {
                        blockedResult.set(
                            admission.dispatch(onDiscard = { blockedDiscarded.incrementAndGet() }) {},
                        )
                    } finally {
                        blockedReturned.countDown()
                    }
                }.apply { start() }

            assertFalse(blockedReturned.await(100, TimeUnit.MILLISECONDS))
            admission.close()

            assertTrue(blockedReturned.await(1, TimeUnit.SECONDS))
            assertEquals(ChromeProxyAdmissionResult.Closed, blockedResult.get())
            assertEquals(1, queuedDiscarded.get())
            assertEquals(1, blockedDiscarded.get())
            submitter.join(1_000)
            assertFalse(submitter.isAlive)
        } finally {
            releaseFirst.countDown()
            admission.close()
        }
    }
}
