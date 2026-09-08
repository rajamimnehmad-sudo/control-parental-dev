package com.contentfilter.user.chromedataplane

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChromeImageProcessingAdmissionTest {
    @Test
    fun `cached waiter completes after notification while an older cold waiter occupies the slot`() {
        val admission = ChromeImageProcessingAdmission(1)
        val pool = Executors.newFixedThreadPool(3)
        val held = CountDownLatch(1)
        val release = CountDownLatch(1)
        val coldChecked = CountDownLatch(1)
        val coldHeld = CountDownLatch(1)
        val coldRelease = CountDownLatch(1)
        val cacheChecked = CountDownLatch(1)
        val cached = AtomicBoolean()
        try {
            pool.submit {
                admission.run({ error("holder rejected") }, { null }) {
                    held.countDown()
                    release.await()
                    cached.set(true)
                }
            }
            assertTrue(held.await(2, TimeUnit.SECONDS))
            pool.submit {
                admission.run({ error("cold rejected") }, {
                    coldChecked.countDown()
                    null
                }) {
                    coldHeld.countDown()
                    coldRelease.await()
                }
            }
            assertTrue(coldChecked.await(2, TimeUnit.SECONDS))
            val result =
                pool.submit<String> {
                    admission.run({ "rejected" }, {
                        cacheChecked.countDown()
                        if (cached.get()) "cached" else null
                    }) { "must not process again" }
                }
            assertTrue(cacheChecked.await(2, TimeUnit.SECONDS))
            release.countDown()
            assertTrue(coldHeld.await(2, TimeUnit.SECONDS))
            assertEquals("cached", result.get(2, TimeUnit.SECONDS))
            assertEquals(1, admission.peak())
            assertEquals(0L, admission.rejections())
        } finally {
            release.countDown()
            coldRelease.countDown()
            pool.shutdownNow()
        }
    }

    @Test
    fun `interrupted waiter rejects without consuming a processing slot`() {
        val admission = ChromeImageProcessingAdmission(1)
        val checked = CountDownLatch(1)
        val interrupted = AtomicBoolean()
        val waiter =
            Thread {
                admission.run({ interrupted.set(Thread.currentThread().isInterrupted) }, {
                    checked.countDown()
                    null
                }) { error("must not process") }
            }
        try {
            admission.run({ error("holder rejected") }, { null }) {
                waiter.start()
                assertTrue(checked.await(2, TimeUnit.SECONDS))
                waiter.interrupt()
                waiter.join(2000)
                assertTrue(!waiter.isAlive)
                assertTrue(interrupted.get())
            }
            assertEquals("available", admission.run({ "rejected" }, { null }) { "available" })
            assertEquals(1L, admission.rejections())
        } finally {
            waiter.interrupt()
            waiter.join(2000)
        }
    }
}
