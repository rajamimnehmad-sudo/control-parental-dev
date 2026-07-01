package com.contentfilter.core.domain.usecase

import com.contentfilter.core.domain.model.UsageSession

/**
 * Calculates persisted session overlap for one local day using epoch boundaries.
 */
class CalculateDailyUsageUseCase {
    fun usedMinutes(
        sessions: List<UsageSession>,
        dayStartEpochMillis: Long,
        dayEndEpochMillis: Long,
    ): Int {
        val usedMillis = sessions.sumOf { session ->
            val endedAt = session.endedAtEpochMillis ?: return@sumOf 0L
            val start = maxOf(session.startedAtEpochMillis, dayStartEpochMillis)
            val end = minOf(endedAt, dayEndEpochMillis)
            (end - start).coerceAtLeast(0L)
        }
        return (usedMillis / MillisPerMinute).toInt()
    }

    private companion object {
        const val MillisPerMinute = 60_000L
    }
}
