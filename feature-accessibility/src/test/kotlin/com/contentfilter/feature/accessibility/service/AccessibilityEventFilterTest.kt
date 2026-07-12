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
        assertTrue(AccessibilityEventFilter.isHandled(AccessibilityEvent.TYPE_VIEW_FOCUSED))
        assertTrue(AccessibilityEventFilter.isHandled(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED))
        assertFalse(AccessibilityEventFilter.isHandled(AccessibilityEvent.TYPE_VIEW_SCROLLED))
    }

    @Test
    fun `ignores unrelated events`() {
        assertFalse(AccessibilityEventFilter.isHandled(AccessibilityEvent.TYPE_VIEW_CLICKED))
    }
}
