package com.contentfilter.user.dag

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DagSearchRequestTrackerTest {
    @Test
    fun `duplicate in-flight Brave search is rejected`() {
        val tracker = DagSearchRequestTracker()
        val request = DagSearchRequest(query = "consulta", page = 0, append = false)

        assertTrue(tracker.begin(request) != null)
        assertNull(tracker.begin(request))
    }

    @Test
    fun `newer search invalidates the older response`() {
        val tracker = DagSearchRequestTracker()
        val oldRequest = requireNotNull(tracker.begin(DagSearchRequest("primera", 0, false)))
        val newRequest = requireNotNull(tracker.begin(DagSearchRequest("segunda", 0, false)))

        assertFalse(tracker.isCurrent(oldRequest))
        assertTrue(tracker.isCurrent(newRequest))
    }

    @Test
    fun `closing a tab invalidates its pending search`() {
        val tracker = DagSearchRequestTracker()
        val requestId = requireNotNull(tracker.begin(DagSearchRequest("consulta", 0, false)))

        tracker.cancel()

        assertFalse(tracker.isCurrent(requestId))
    }
}
