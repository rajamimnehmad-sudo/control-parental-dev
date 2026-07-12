package com.contentfilter.feature.accessibility.service

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccessibilityWebActionDebouncerTest {
    @Test
    fun `same package host revision and action is performed once within debounce`() {
        val debouncer = AccessibilityWebActionDebouncer(debounceMillis = 1_000L)

        assertTrue(debouncer.shouldPerform(Chrome, "example.com", 10L, SearchNavigationAction.GoBack, 100L))
        assertFalse(debouncer.shouldPerform(Chrome, "example.com", 10L, SearchNavigationAction.GoBack, 101L))
        assertTrue(debouncer.shouldPerform(Chrome, "example.com", 10L, SearchNavigationAction.GoBack, 1_100L))
    }

    @Test
    fun `new host revision or action is independent`() {
        val debouncer = AccessibilityWebActionDebouncer(debounceMillis = 1_000L)

        assertTrue(debouncer.shouldPerform(Chrome, "one.example", 10L, SearchNavigationAction.GoBack, 100L))
        assertTrue(debouncer.shouldPerform(Chrome, "two.example", 10L, SearchNavigationAction.GoBack, 101L))
        assertTrue(debouncer.shouldPerform(Chrome, "two.example", 11L, SearchNavigationAction.GoBack, 102L))
        assertTrue(
            debouncer.shouldPerform(Chrome, "two.example", 11L, SearchNavigationAction.OpenDefaultSearch, 103L),
        )
        assertFalse(debouncer.shouldPerform(Chrome, "one.example", 10L, SearchNavigationAction.GoBack, 104L))
    }

    private companion object {
        const val Chrome = "com.android.chrome"
    }
}
