package com.contentfilter.core.sync.engine

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EffectivePolicyApplicationTrackerTest {
    @Test
    fun `policy is effective only after vpn and accessibility report the revision`() =
        runBlocking {
            val tracker = EffectivePolicyApplicationTracker()

            tracker.report(PolicyConsumer.Vpn, "policy", 200L)
            assertFalse(tracker.isApplied("policy", 200L))

            tracker.report(PolicyConsumer.Accessibility, "policy", 200L)
            assertTrue(tracker.isApplied("policy", 200L))
        }

    @Test
    fun `older component response cannot replace newer effective revision`() {
        val tracker = EffectivePolicyApplicationTracker()
        tracker.report(PolicyConsumer.Vpn, "policy", 300L)
        tracker.report(PolicyConsumer.Vpn, "policy", 200L)
        tracker.report(PolicyConsumer.Accessibility, "policy", 300L)

        assertTrue(tracker.isApplied("policy", 300L))
    }
}
