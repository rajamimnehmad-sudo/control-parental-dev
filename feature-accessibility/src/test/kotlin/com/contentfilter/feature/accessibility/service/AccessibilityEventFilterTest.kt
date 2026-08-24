package com.contentfilter.feature.accessibility.service

import android.view.accessibility.AccessibilityEvent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccessibilityEventFilterTest {
    @Test
    fun `handles content changed events declared by accessibility xml`() {
        assertTrue(AccessibilityEventFilter.isHandled(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED))
    }

    @Test
    fun `keeps existing handled events`() {
        assertTrue(AccessibilityEventFilter.isHandled(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED))
        assertTrue(AccessibilityEventFilter.isHandled(AccessibilityEvent.TYPE_WINDOWS_CHANGED))
        assertTrue(AccessibilityEventFilter.isHandled(AccessibilityEvent.TYPE_VIEW_CLICKED))
        assertTrue(AccessibilityEventFilter.isHandled(AccessibilityEvent.TYPE_VIEW_FOCUSED))
        assertTrue(AccessibilityEventFilter.isHandled(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED))
    }

    @Test
    fun `scroll is routed only to chrome visual`() {
        assertTrue(AccessibilityEventFilter.isChromeVisualOnly(AccessibilityEvent.TYPE_VIEW_SCROLLED))
        assertFalse(AccessibilityEventFilter.isHandled(AccessibilityEvent.TYPE_VIEW_SCROLLED))
    }

    @Test
    fun `chrome content changes bypass general traversal only during protected session`() {
        assertTrue(
            AccessibilityEventFilter.isProtectedChromeVisualOnly(
                eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                packageName = "com.android.chrome",
                protectedSessionActive = true,
            ),
        )
        assertFalse(
            AccessibilityEventFilter.isProtectedChromeVisualOnly(
                eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                packageName = "com.android.chrome",
                protectedSessionActive = false,
            ),
        )
        assertFalse(
            AccessibilityEventFilter.isProtectedChromeVisualOnly(
                eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                packageName = "com.example.other",
                protectedSessionActive = true,
            ),
        )
        assertFalse(
            AccessibilityEventFilter.isProtectedChromeVisualOnly(
                eventType = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
                packageName = "com.android.chrome",
                protectedSessionActive = true,
            ),
        )
    }

    @Test
    fun `ignores unrelated events`() {
        assertFalse(AccessibilityEventFilter.isHandled(AccessibilityEvent.TYPE_VIEW_HOVER_ENTER))
        assertFalse(AccessibilityEventFilter.isChromeVisualOnly(AccessibilityEvent.TYPE_VIEW_HOVER_ENTER))
    }
}
