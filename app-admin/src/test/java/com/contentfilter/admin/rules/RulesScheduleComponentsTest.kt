package com.contentfilter.admin.rules

import com.contentfilter.core.domain.model.PolicyWeekdays
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RulesScheduleComponentsTest {
    @Test
    fun `parses strict 24 hour Argentina time`() {
        assertEquals(0, parseScheduleMinute("00:00"))
        assertEquals(23 * 60 + 59, parseScheduleMinute("23:59"))
        assertNull(parseScheduleMinute("24:00"))
        assertNull(parseScheduleMinute("9:30"))
    }

    @Test
    fun `schedule drafts survive saveable state round trip`() {
        val drafts =
            listOf(
                ScheduleWindowDraft(id = "existing", start = "08:15", end = "12:30", activeDaysMask = 31),
                ScheduleWindowDraft(start = "14:00", end = "18:45", activeDaysMask = 96),
            )

        assertEquals(drafts, decodeScheduleDrafts(encodeScheduleDrafts(drafts)))
    }

    @Test
    fun `separate windows on the same day do not overlap`() {
        val monday = PolicyWeekdays.bit(1)
        val drafts =
            listOf(
                ScheduleWindowDraft(start = "08:00", end = "12:00", activeDaysMask = monday),
                ScheduleWindowDraft(start = "13:00", end = "18:00", activeDaysMask = monday),
            )

        assertFalse(scheduleDraftsOverlap(drafts))
    }

    @Test
    fun `overlapping windows on the same day are rejected`() {
        val monday = PolicyWeekdays.bit(1)
        val drafts =
            listOf(
                ScheduleWindowDraft(start = "08:00", end = "12:00", activeDaysMask = monday),
                ScheduleWindowDraft(start = "11:30", end = "18:00", activeDaysMask = monday),
            )

        assertTrue(scheduleDraftsOverlap(drafts))
    }

    @Test
    fun `overnight window overlaps the following day`() {
        val monday = PolicyWeekdays.bit(1)
        val tuesday = PolicyWeekdays.bit(2)
        val drafts =
            listOf(
                ScheduleWindowDraft(start = "22:00", end = "02:00", activeDaysMask = monday),
                ScheduleWindowDraft(start = "01:00", end = "03:00", activeDaysMask = tuesday),
            )

        assertTrue(scheduleDraftsOverlap(drafts))
    }
}
