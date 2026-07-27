package com.contentfilter.user.dag

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DagPageUrlTrackerTest {
    @Test
    fun `starts empty and exposes the latest main-frame URL`() {
        val tracker = DagPageUrlTracker()

        assertNull(tracker.current())
        val first = tracker.begin("https://example.com/first")
        val latest = tracker.begin("https://example.com/latest")

        assertEquals("https://example.com/latest", tracker.current()?.url)
        assertNotEquals(first.generation, latest.generation)
        assertFalse(first.matches(latest.url))
        assertTrue(latest.matches("https://example.com/latest#section"))
    }
}
