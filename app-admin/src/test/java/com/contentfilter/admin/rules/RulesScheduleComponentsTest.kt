package com.contentfilter.admin.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
