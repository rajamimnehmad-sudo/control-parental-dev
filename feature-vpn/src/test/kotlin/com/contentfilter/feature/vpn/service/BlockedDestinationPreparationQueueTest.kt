package com.contentfilter.feature.vpn.service

import kotlin.test.Test
import kotlin.test.assertEquals

class BlockedDestinationPreparationQueueTest {
    @Test
    fun `one worker drains a concurrent destination wave`() {
        val queue = BlockedDestinationPreparationQueue<String>()

        assertEquals(
            BlockedDestinationPreparationQueue.OfferResult.StartWorker,
            queue.offer("first", "first-value"),
        )
        assertEquals(
            BlockedDestinationPreparationQueue.OfferResult.Queued,
            queue.offer("second", "second-value"),
        )
        assertEquals(listOf("first-value", "second-value"), queue.drain())
        assertEquals(true, queue.markIdleIfEmpty())
    }

    @Test
    fun `work arriving while draining stays in the same worker`() {
        val queue = BlockedDestinationPreparationQueue<String>()

        queue.offer("first", "first-value")
        assertEquals(listOf("first-value"), queue.drain())
        assertEquals(
            BlockedDestinationPreparationQueue.OfferResult.Queued,
            queue.offer("second", "second-value"),
        )
        assertEquals(false, queue.markIdleIfEmpty())
        assertEquals(listOf("second-value"), queue.drain())
        assertEquals(true, queue.markIdleIfEmpty())
    }

    @Test
    fun `bounded queue rejects overflow and can start again after clear`() {
        val queue = BlockedDestinationPreparationQueue<String>(maxPending = 1)

        assertEquals(
            BlockedDestinationPreparationQueue.OfferResult.StartWorker,
            queue.offer("first", "first-value"),
        )
        assertEquals(
            BlockedDestinationPreparationQueue.OfferResult.Rejected,
            queue.offer("second", "second-value"),
        )
        assertEquals(listOf("first-value"), queue.clear())
        assertEquals(
            BlockedDestinationPreparationQueue.OfferResult.StartWorker,
            queue.offer("second", "second-value"),
        )
    }

    @Test
    fun `same key updates queued preparation without growing the queue`() {
        val queue = BlockedDestinationPreparationQueue<String>(maxPending = 1)

        queue.offer("same", "old")
        assertEquals(
            BlockedDestinationPreparationQueue.OfferResult.Queued,
            queue.offer("same", "new"),
        )
        assertEquals(listOf("new"), queue.drain())
    }

    @Test
    fun `first invalidation is immediate and later waves respect minimum interval`() {
        assertEquals(
            0L,
            blockedDestinationInvalidationDelayMillis(
                lastStartedAtMillis = null,
                nowMillis = 1_000L,
                minimumIntervalMillis = 1_500L,
            ),
        )
        assertEquals(
            1_100L,
            blockedDestinationInvalidationDelayMillis(
                lastStartedAtMillis = 1_000L,
                nowMillis = 1_400L,
                minimumIntervalMillis = 1_500L,
            ),
        )
        assertEquals(
            0L,
            blockedDestinationInvalidationDelayMillis(
                lastStartedAtMillis = 1_000L,
                nowMillis = 2_500L,
                minimumIntervalMillis = 1_500L,
            ),
        )
    }
}
