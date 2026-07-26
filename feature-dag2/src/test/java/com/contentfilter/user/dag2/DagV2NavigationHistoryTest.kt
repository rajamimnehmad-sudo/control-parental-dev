package com.contentfilter.user.dag2

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DagV2NavigationHistoryTest {
    @Test
    fun `spa location replaces current reload target without adding a document`() {
        val history = DagV2NavigationHistory()
        history.push("https://example.com/products")

        history.replaceCurrent("https://example.com/products?filter=1")

        assertEquals(
            "https://example.com/products?filter=1",
            history.currentTarget()?.url,
        )
        assertFalse(history.canGoBack())
        assertFalse(history.canGoForward())
    }

    @Test
    fun `back forward and refresh targets do not depend on stale webview sessions`() {
        val history = DagV2NavigationHistory()
        history.push("https://example.com/a")
        history.push("https://example.com/b")

        assertTrue(history.canGoBack())
        assertFalse(history.canGoForward())
        val back = history.backTarget()
        assertEquals("https://example.com/a", back?.url)
        history.commit(requireNotNull(back))
        assertTrue(history.canGoForward())
        assertEquals("https://example.com/a", history.currentTarget()?.url)
        val forward = history.forwardTarget()
        assertEquals("https://example.com/b", forward?.url)
        history.commit(requireNotNull(forward))
        assertEquals("https://example.com/b", history.currentTarget()?.url)
    }

    @Test
    fun `new navigation after back removes obsolete forward target`() {
        val history = DagV2NavigationHistory()
        history.push("https://example.com/a")
        history.push("https://example.com/b")
        history.commit(requireNotNull(history.backTarget()))

        history.push("https://example.com/c")

        assertNull(history.forwardTarget())
        assertEquals("https://example.com/c", history.currentTarget()?.url)
    }
}
