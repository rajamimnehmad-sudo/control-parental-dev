package com.contentfilter.dagbrowser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DagPerformanceTrackerTest {
    @Test
    fun `metrics are ignored until a top level navigation begins`() {
        val tracker = DagPerformanceTracker { 1_000L }

        assertNull(tracker.mark(DagPerformanceMetric.PageVisible))
    }

    @Test
    fun `navigation emits each metric once with monotonic elapsed time`() {
        var now = 1_000L
        val tracker = DagPerformanceTracker { now }

        assertEquals(
            DagPerformanceEvent(
                navigationId = 1,
                metric = DagPerformanceMetric.PageLoadStarted,
                elapsedMillis = 0,
            ),
            tracker.begin(),
        )
        now = 1_125L
        assertEquals(
            DagPerformanceEvent(
                navigationId = 1,
                metric = DagPerformanceMetric.PageVisible,
                elapsedMillis = 125,
            ),
            tracker.mark(DagPerformanceMetric.PageVisible),
        )
        assertNull(tracker.mark(DagPerformanceMetric.PageVisible))
        now = 1_400L
        assertEquals(
            400,
            tracker.mark(DagPerformanceMetric.PageAnalysisReady)?.elapsedMillis,
        )
    }

    @Test
    fun `new navigation resets metrics and increments the identifier`() {
        var now = 2_000L
        val tracker = DagPerformanceTracker { now }
        tracker.begin()
        tracker.mark(DagPerformanceMetric.ViewportImagesReady)

        now = 3_000L
        assertEquals(2, tracker.begin().navigationId)
        now = 3_010L
        assertEquals(
            10,
            tracker.mark(DagPerformanceMetric.ViewportImagesReady)?.elapsedMillis,
        )
    }

    @Test
    fun `clock rollback cannot produce a negative measurement`() {
        var now = 500L
        val tracker = DagPerformanceTracker { now }
        tracker.begin()

        now = 400L
        assertEquals(
            0,
            tracker.mark(DagPerformanceMetric.PageVisible)?.elapsedMillis,
        )
    }
}
