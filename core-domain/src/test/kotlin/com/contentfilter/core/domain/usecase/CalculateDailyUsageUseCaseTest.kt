package com.contentfilter.core.domain.usecase

import com.contentfilter.core.domain.model.UsageSession
import kotlin.test.Test
import kotlin.test.assertEquals

class CalculateDailyUsageUseCaseTest {
    private val useCase = CalculateDailyUsageUseCase()

    @Test
    fun `accumulates usage within day`() {
        val sessions = listOf(
            session(start = 10_000L, end = 70_000L),
            session(start = 100_000L, end = 220_000L),
        )

        val minutes = useCase.usedMinutes(sessions, dayStartEpochMillis = 0L, dayEndEpochMillis = 300_000L)

        assertEquals(3, minutes)
    }

    @Test
    fun `counts only overlap inside local day`() {
        val sessions = listOf(
            session(start = -60_000L, end = 60_000L),
            session(start = 120_000L, end = 240_000L),
            session(start = 290_000L, end = 360_000L),
        )

        val minutes = useCase.usedMinutes(sessions, dayStartEpochMillis = 0L, dayEndEpochMillis = 300_000L)

        assertEquals(3, minutes)
    }

    private fun session(
        start: Long,
        end: Long,
    ): UsageSession =
        UsageSession(
            id = "$start-$end",
            deviceId = UsageSession.LocalDeviceId,
            packageName = "com.example.app",
            startedAtEpochMillis = start,
            endedAtEpochMillis = end,
        )
}
