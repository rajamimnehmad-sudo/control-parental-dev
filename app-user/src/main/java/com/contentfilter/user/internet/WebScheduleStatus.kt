package com.contentfilter.user.internet

import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicySchedulePolicy
import com.contentfilter.core.domain.model.PolicySchedulePolicy.isAllowedWindow
import com.contentfilter.core.domain.model.PolicySchedulePolicy.scheduleTarget
import com.contentfilter.core.domain.model.PolicyTimeWindow
import com.contentfilter.core.domain.model.PolicyWeekdays
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.model.TimePolicyContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

data class WebScheduleStatus(
    val isAllowed: Boolean,
    val summary: String,
)

internal fun resolveWebScheduleStatus(
    rules: List<PolicyRule>,
    nowEpochMillis: Long,
): WebScheduleStatus? {
    val schedules =
        rules.filter {
            it.scope == RuleScope.Domain &&
                it.isAllowedWindow() &&
                it.scheduleTarget() == PolicySchedulePolicy.WildcardTarget
        }
    if (schedules.isEmpty()) return null

    val now = Instant.ofEpochMilli(nowEpochMillis).atZone(WebScheduleZone)
    val time =
        TimePolicyContext(
            evaluatedAtEpochMillis = nowEpochMillis,
            minuteOfDay = now.hour * 60 + now.minute,
            isoDayOfWeek = now.dayOfWeek.value,
        )
    val activeSchedules = schedules.filter { rule -> rule.activeWindow?.contains(time, rule.activeDaysMask) == true }
    if (activeSchedules.isNotEmpty()) {
        if (activeSchedules.any { it.activeWindow?.isAllDay == true }) {
            return WebScheduleStatus(isAllowed = true, summary = "Disponible todo el día")
        }
        val end = activeSchedules.mapNotNull { it.endForActiveWindow(now) }.maxOrNull()
        return WebScheduleStatus(
            isAllowed = true,
            summary = end?.let { "Disponible hasta las ${it.minuteLabel()}" } ?: "Dentro del horario permitido",
        )
    }

    val nextStart = schedules.mapNotNull { it.nextStartAfter(now) }.minOrNull()
    return WebScheduleStatus(
        isAllowed = false,
        summary = nextStart?.availabilityLabel(now.toLocalDate()) ?: "Fuera del horario permitido",
    )
}

private fun PolicyRule.endForActiveWindow(now: ZonedDateTime): ZonedDateTime? {
    val window = activeWindow ?: return null
    val currentMinute = now.hour * 60 + now.minute
    val startDate =
        if (window.crossesMidnight && currentMinute < window.endMinuteOfDay) {
            now.toLocalDate().minusDays(1)
        } else {
            now.toLocalDate()
        }
    val endDate = if (window.crossesMidnight) startDate.plusDays(1) else startDate
    return endDate.atMinute(window.endMinuteOfDay)
}

private fun PolicyRule.nextStartAfter(now: ZonedDateTime): ZonedDateTime? {
    val window = activeWindow ?: return null
    return (0L..7L)
        .asSequence()
        .map { offset -> now.toLocalDate().plusDays(offset) }
        .filter { date -> PolicyWeekdays.includes(activeDaysMask, date.dayOfWeek.value) }
        .map { date -> date.atMinute(window.startMinuteOfDay) }
        .firstOrNull { candidate -> candidate.isAfter(now) }
}

private fun ZonedDateTime.availabilityLabel(today: LocalDate): String {
    val prefix =
        when (toLocalDate()) {
            today -> "Disponible desde las"
            today.plusDays(1) -> "Disponible mañana desde las"
            else -> "Disponible el ${SpanishWeekdays[dayOfWeek.value - 1]} desde las"
        }
    return "$prefix ${minuteLabel()}"
}

private fun LocalDate.atMinute(minuteOfDay: Int): ZonedDateTime =
    atTime(minuteOfDay / 60, minuteOfDay % 60).atZone(WebScheduleZone)

private fun ZonedDateTime.minuteLabel(): String = "%02d:%02d".format(hour, minute)

private val PolicyTimeWindow.crossesMidnight: Boolean
    get() = startMinuteOfDay > endMinuteOfDay

private val PolicyTimeWindow.isAllDay: Boolean
    get() = startMinuteOfDay == endMinuteOfDay

private val WebScheduleZone = ZoneId.of("America/Argentina/Buenos_Aires")
private val SpanishWeekdays = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
