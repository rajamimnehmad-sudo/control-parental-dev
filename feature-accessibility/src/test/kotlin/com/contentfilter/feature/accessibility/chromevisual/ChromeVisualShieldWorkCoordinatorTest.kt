package com.contentfilter.feature.accessibility.chromevisual

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromeVisualShieldWorkCoordinatorTest {
    @Test
    fun `new inference waits until cancelled non cooperative inference exits and cleans up`() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val firstStarted = CountDownLatch(1)
            val releaseFirst = CountDownLatch(1)
            val secondStarted = CountDownLatch(1)
            val outstanding = AtomicInteger()
            val peak = AtomicInteger()
            val trace = Collections.synchronizedList(mutableListOf<String>())
            val coordinator =
                ChromeVisualShieldWorkCoordinator<String>(scope, {}, {}) { value ->
                    val now = outstanding.incrementAndGet()
                    peak.updateAndGet { previous -> maxOf(previous, now) }
                    trace += "$value:start"
                    try {
                        if (value == "E1") {
                            firstStarted.countDown()
                            releaseFirst.await()
                            trace += "E1:sync_exit"
                        } else {
                            secondStarted.countDown()
                        }
                    } finally {
                        outstanding.decrementAndGet()
                        trace += "$value:cleanup"
                    }
                }

            coordinator.request("E1")
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS))
            coordinator.request("E2")
            assertFalse(secondStarted.await(150, TimeUnit.MILLISECONDS))
            releaseFirst.countDown()
            assertTrue(secondStarted.await(2, TimeUnit.SECONDS))
            coordinator.shutdown()

            assertEquals(0, outstanding.get())
            assertEquals(1, peak.get())
            assertTrue(trace.indexOf("E1:cleanup") < trace.indexOf("E2:start"))
        }

    @Test
    fun `E1 E2 E3 keep all invalidations while only pending work is superseded`() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val firstStarted = CountDownLatch(1)
            val releaseFirst = CountDownLatch(1)
            val thirdStarted = CountDownLatch(1)
            val starts = Collections.synchronizedList(mutableListOf<String>())
            val invalidations = AtomicInteger()
            val superseded = AtomicInteger()
            val coordinator =
                ChromeVisualShieldWorkCoordinator<String>(scope, superseded::incrementAndGet, {}) { value ->
                    starts += value
                    if (value == "E1") {
                        firstStarted.countDown()
                        releaseFirst.await()
                    }
                    if (value == "E3") thirdStarted.countDown()
                }

            invalidations.incrementAndGet()
            coordinator.request("E1")
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS))
            invalidations.incrementAndGet()
            coordinator.invalidateAuthority()
            coordinator.request("E2")
            invalidations.incrementAndGet()
            coordinator.invalidateAuthority()
            coordinator.request("E3")
            releaseFirst.countDown()
            assertTrue(thirdStarted.await(2, TimeUnit.SECONDS))
            coordinator.shutdown()

            assertEquals(3, invalidations.get())
            assertEquals(listOf("E1", "E3"), starts)
            assertTrue(superseded.get() >= 2)
        }

    @Test
    fun `cancel and join returns only after active cleanup`() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            val outstanding = AtomicInteger()
            val coordinator =
                ChromeVisualShieldWorkCoordinator<Unit>(scope, {}, {}) {
                    outstanding.incrementAndGet()
                    try {
                        started.countDown()
                        release.await()
                    } finally {
                        outstanding.decrementAndGet()
                    }
                }

            coordinator.request(Unit)
            assertTrue(started.await(2, TimeUnit.SECONDS))
            val releaser = Thread { release.countDown() }
            releaser.start()
            coordinator.cancelAndJoin()
            releaser.join()

            assertEquals(0, outstanding.get())
            assertTrue(coordinator.isIdle())
            coordinator.shutdown()
        }
}
