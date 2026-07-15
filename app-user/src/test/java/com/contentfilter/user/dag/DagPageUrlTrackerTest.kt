package com.contentfilter.user.dag

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DagPageUrlTrackerTest {
    @Test
    fun `starts empty and exposes the latest main-frame URL`() {
        val tracker = DagPageUrlTracker()

        assertNull(tracker.current())
        tracker.update("https://example.com/first")
        tracker.update("https://example.com/latest")

        assertEquals("https://example.com/latest", tracker.current())
    }
}
