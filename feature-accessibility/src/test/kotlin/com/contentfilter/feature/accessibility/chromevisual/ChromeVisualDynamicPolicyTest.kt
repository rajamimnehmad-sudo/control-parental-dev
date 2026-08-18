package com.contentfilter.feature.accessibility.chromevisual

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromeVisualDynamicPolicyTest {
    @Test
    fun `event storm coalesces into one baseline and one follow-up scan`() {
        val coordinator = ChromeVisualBaselineCoordinator()
        val context = ChromeVisualBaselineContext(7, 10L, ChromeVisualViewport(0, 0, 1_080, 2_400))

        assertTrue(coordinator.startIfIdle(context))
        repeat(20) { assertTrue(coordinator.coalesceIfActive(context)) }
        assertTrue(coordinator.finish(context))
        assertFalse(coordinator.finish(context))
        assertTrue(coordinator.startIfIdle(context))
    }

    @Test
    fun `ordinary events on a known geometry never request full precover`() {
        val viewport = ChromeVisualViewport(0, 100, 1_080, 2_400)
        assertFalse(
            ChromeVisualEventModePolicy.requiresBaseline(
                pageChanged = false,
                activeWindowId = 7,
                eventWindowId = 7,
                lastViewport = viewport,
                eventViewport = viewport,
                hasSignatures = true,
            ),
        )
        assertTrue(
            ChromeVisualEventModePolicy.requiresBaseline(false, 7, 7, viewport, viewport.copy(bottom = 1_080), true),
        )
        assertTrue(ChromeVisualEventModePolicy.requiresBaseline(true, 7, 7, viewport, viewport, true))
    }

    @Test
    fun `stable pages back off without exceeding one second`() {
        assertTrue(ChromeVisualVerificationSchedule.delayMillis(0) == 500L)
        assertTrue(ChromeVisualVerificationSchedule.delayMillis(1) == 500L)
        assertTrue(ChromeVisualVerificationSchedule.delayMillis(2) == 1_000L)
        assertTrue(ChromeVisualVerificationSchedule.delayMillis(20) == 1_000L)
        assertTrue(ChromeVisualVerificationSchedule.delayMillis(20, hasDynamicRegions = true) == 500L)
    }

    @Test
    fun `blocked visual area survives geometry churn until page identity changes`() {
        val ledger = ChromeVisualPageBlockLedger()
        val tiles =
            listOf(
                ChromeVisualRegion("tile_0", 0, 200, 500, 700),
                ChromeVisualRegion("tile_1", 500, 200, 1_000, 700),
            )

        assertTrue(ledger.beginPage(10L))
        ledger.recordBlocked(10L, ChromeVisualRegion("image", 400, 300, 600, 500), tiles)
        assertTrue(ledger.mustRemainBlocked(10L, "tile_0"))
        assertTrue(ledger.mustRemainBlocked(10L, "tile_1"))

        assertFalse(ledger.beginPage(10L))
        assertTrue(ledger.mustRemainBlocked(10L, "tile_0"))
        assertTrue(ledger.beginPage(11L))
        assertFalse(ledger.mustRemainBlocked(11L, "tile_0"))
    }
}
