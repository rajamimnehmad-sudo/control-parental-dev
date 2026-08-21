package com.contentfilter.feature.accessibility.chromevisual

import android.view.accessibility.AccessibilityEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
    fun `atomic mutation cancels a stale baseline`() {
        val coordinator = ChromeVisualBaselineCoordinator()
        val context = ChromeVisualBaselineContext(7, 10L, ChromeVisualViewport(0, 0, 1_080, 2_400))

        assertTrue(coordinator.startIfIdle(context))
        assertTrue(coordinator.cancelIfActive(context))
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
    fun `scroll and visible content mutations require atomic replay`() {
        assertTrue(
            ChromeVisualAtomicMutationPolicy.requiresReplay(
                AccessibilityEvent.TYPE_VIEW_SCROLLED,
                AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED,
            ),
        )
        assertTrue(
            ChromeVisualAtomicMutationPolicy.requiresReplay(
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED,
            ),
        )
        assertTrue(
            ChromeVisualAtomicMutationPolicy.requiresReplay(
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE,
            ),
        )
        assertFalse(
            ChromeVisualAtomicMutationPolicy.requiresReplay(
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT,
            ),
        )
        assertFalse(
            ChromeVisualAtomicMutationPolicy.requiresReplay(
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
                AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED,
            ),
        )
        assertTrue(
            ChromeVisualAtomicMutationPolicy.requiresGeometryRestart(
                AccessibilityEvent.TYPE_VIEW_SCROLLED,
            ),
        )
        assertFalse(
            ChromeVisualAtomicMutationPolicy.requiresGeometryRestart(
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            ),
        )
    }

    @Test
    fun `lazy content mutations reuse an active analysis but scroll restarts it`() {
        assertTrue(
            ChromeVisualAtomicMutationPolicy.shouldCoalesceIntoActiveAnalysis(
                replayRequired = true,
                geometryRestart = false,
                analysisActive = true,
            ),
        )
        assertFalse(
            ChromeVisualAtomicMutationPolicy.shouldCoalesceIntoActiveAnalysis(
                replayRequired = true,
                geometryRestart = true,
                analysisActive = true,
            ),
        )
        assertFalse(
            ChromeVisualAtomicMutationPolicy.shouldCoalesceIntoActiveAnalysis(
                replayRequired = true,
                geometryRestart = false,
                analysisActive = false,
            ),
        )
        assertFalse(
            ChromeVisualAtomicMutationPolicy.shouldCoalesceIntoActiveAnalysis(
                replayRequired = false,
                geometryRestart = false,
                analysisActive = true,
            ),
        )
    }

    @Test
    fun `newer replay revision cannot be completed by stale work`() {
        val coordinator = ChromeVisualAtomicReplayCoordinator()
        val first = coordinator.request()
        val second = coordinator.request()

        assertFalse(coordinator.complete(first))
        assertEquals(second, coordinator.currentRevision())
        assertTrue(coordinator.complete(second))
        assertNull(coordinator.currentRevision())
    }

    @Test
    fun `atomic replay evaluates every fallback tile`() {
        val fallback =
            listOf(
                ChromeVisualRegion("tile_0", 0, 0, 100, 100),
                ChromeVisualRegion("tile_1", 100, 0, 200, 100),
            )
        val changed = listOf(fallback.first())
        val confirmation = listOf(fallback.last())

        assertEquals(
            fallback,
            ChromeVisualReplayRegionPolicy.select(
                replayActive = true,
                fallbackTiles = fallback,
                visuallyChanged = changed,
                confirmations = emptyList(),
            ),
        )
        assertEquals(
            fallback,
            ChromeVisualReplayRegionPolicy.select(
                replayActive = false,
                fallbackTiles = fallback,
                visuallyChanged = changed,
                confirmations = confirmation,
            ),
        )
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
