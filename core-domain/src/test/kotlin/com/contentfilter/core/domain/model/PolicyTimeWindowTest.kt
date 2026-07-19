package com.contentfilter.core.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PolicyTimeWindowTest {
    @Test
    fun `end minute is exclusive`() {
        val window = PolicyTimeWindow(startMinuteOfDay = 8 * 60, endMinuteOfDay = 12 * 60)

        assertTrue(window.contains(11 * 60 + 59))
        assertFalse(window.contains(12 * 60))
    }

    @Test
    fun `window spanning midnight uses the start weekday`() {
        val window = PolicyTimeWindow(startMinuteOfDay = 22 * 60, endMinuteOfDay = 2 * 60)
        val mondayOnly = PolicyWeekdays.bit(1)

        assertTrue(window.contains(TimePolicyContext(0L, 23 * 60, isoDayOfWeek = 1), mondayOnly))
        assertTrue(window.contains(TimePolicyContext(0L, 60, isoDayOfWeek = 2), mondayOnly))
        assertFalse(window.contains(TimePolicyContext(0L, 60, isoDayOfWeek = 3), mondayOnly))
    }
}
