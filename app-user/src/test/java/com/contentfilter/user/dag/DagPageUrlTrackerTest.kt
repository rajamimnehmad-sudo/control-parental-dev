package com.contentfilter.user.dag

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DagPageUrlTrackerTest {
    @Test
    fun `starts empty and exposes the latest main-frame URL`() {
        val tracker = DagPageUrlTracker()

        assertNull(tracker.current())
        tracker.update("https://example.com/first")
        tracker.update("https://example.com/latest")

        assertEquals("https://example.com/latest", tracker.current())
    }

    @Test
    fun `new page analysis invalidates an older result for the same url`() {
        val tracker = DagPageAnalysisTracker()
        val oldAnalysis = tracker.begin("https://example.com")
        val currentAnalysis = tracker.begin("https://example.com")

        assertTrue(currentAnalysis.revision > oldAnalysis.revision)
        assertFalse(
            dagPageAnalysisMatches(
                activeUrl = currentAnalysis.url,
                activeAnalysis = tracker.current(currentAnalysis.url),
                candidate = oldAnalysis,
                dagEnabled = true,
            ),
        )
        assertTrue(
            dagPageAnalysisMatches(
                activeUrl = currentAnalysis.url,
                activeAnalysis = tracker.current(currentAnalysis.url),
                candidate = currentAnalysis,
                dagEnabled = true,
            ),
        )
    }

    @Test
    fun `page analysis requires both current url and current revision`() {
        val tracker = DagPageAnalysisTracker()
        val analysis = tracker.begin("https://example.com")

        assertFalse(
            dagPageAnalysisMatches(
                activeUrl = "https://other.example",
                activeAnalysis = tracker.current(analysis.url),
                candidate = analysis,
                dagEnabled = true,
            ),
        )
        tracker.cancel()
        assertFalse(
            dagPageAnalysisMatches(
                activeUrl = analysis.url,
                activeAnalysis = tracker.current(analysis.url),
                candidate = analysis,
                dagEnabled = true,
            ),
        )
    }
}
