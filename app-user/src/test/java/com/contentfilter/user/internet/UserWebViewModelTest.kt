package com.contentfilter.user.internet

import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicySchedulePolicy
import com.contentfilter.core.domain.model.PolicyTimeWindow
import com.contentfilter.core.domain.model.PolicyWeekdays
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserWebViewModelTest {
    @Test
    fun `DAG is closed in the initial state and appears only when enabled`() {
        val closed = UserWebUiState()
        val open = closed.copy(dagEnabled = true)

        assertFalse(closed.dagEnabled)
        assertFalse("DAG" in closed.activeLayers)
        assertTrue("DAG" in open.activeLayers)
    }

    @Test
    fun `global web schedule reports the current closing time`() {
        val state = resolveWebScheduleStatus(listOf(schedule(8 * 60, 12 * 60)), instant(2026, 7, 20, 10, 30))

        assertEquals(true, state?.isAllowed)
        assertEquals("Disponible hasta las 12:00", state?.summary)
    }

    @Test
    fun `global web schedule reports when navigation returns`() {
        val state = resolveWebScheduleStatus(listOf(schedule(8 * 60, 12 * 60)), instant(2026, 7, 20, 13, 0))

        assertEquals(false, state?.isAllowed)
        assertEquals("Disponible mañana desde las 08:00", state?.summary)
    }

    @Test
    fun `overnight web schedule remains active after midnight`() {
        val state = resolveWebScheduleStatus(listOf(schedule(22 * 60, 6 * 60)), instant(2026, 7, 21, 1, 0))

        assertEquals(true, state?.isAllowed)
        assertEquals("Disponible hasta las 06:00", state?.summary)
    }

    private fun schedule(
        startMinute: Int,
        endMinute: Int,
    ) = PolicyRule(
        id = "schedule",
        scope = RuleScope.Domain,
        target = PolicySchedulePolicy.encodedTarget(PolicySchedulePolicy.WildcardTarget),
        action = RuleAction.Allow,
        priority = PolicySchedulePolicy.RulePriority,
        enabled = true,
        activeWindow = PolicyTimeWindow(startMinute, endMinute),
        activeDaysMask = PolicyWeekdays.All,
    )

    private fun instant(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneId.of("America/Argentina/Buenos_Aires"))
            .toInstant()
            .toEpochMilli()
}
