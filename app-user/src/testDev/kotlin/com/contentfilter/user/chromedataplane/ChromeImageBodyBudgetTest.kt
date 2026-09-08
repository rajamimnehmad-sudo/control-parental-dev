package com.contentfilter.user.chromedataplane

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ChromeImageBodyBudgetTest {
    @Test
    fun `weighted reservations never exceed capacity and release on completion`() {
        val budget = ChromeImageBodyBudget(128)
        val pool = Executors.newFixedThreadPool(3)
        val occupied = CountDownLatch(2)
        val release = CountDownLatch(1)
        try {
            repeat(2) {
                pool.submit {
                    budget.withReservation(64, { error("rejected") }) {
                        occupied.countDown()
                        release.await()
                    }
                }
            }
            assertTrue(occupied.await(2, TimeUnit.SECONDS))
            val third = pool.submit<Int> { budget.withReservation(1, { -1 }) { 1 } }
            assertFailsWith<TimeoutException> { third.get(100, TimeUnit.MILLISECONDS) }
            assertEquals(128, budget.peakBytes())
            release.countDown()
            assertEquals(1, third.get(2, TimeUnit.SECONDS))
            assertTrue(budget.peakBytes() <= budget.capacity)
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

    @Test
    fun `oversized reservation rejects and exception returns all permits`() {
        val budget = ChromeImageBodyBudget(16)
        assertEquals(-1, budget.withReservation(17, { -1 }) { error("must not run") })
        assertFailsWith<IllegalStateException> {
            budget.withReservation(16, { error("rejected") }) { error("processing failure") }
        }
        assertEquals(16, budget.withReservation(16, { -1 }) { 16 })
    }
}
