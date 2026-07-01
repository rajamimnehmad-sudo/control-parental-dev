package com.contentfilter.feature.accessibility.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppUsageTrackerTest {
    private val tracker = AppUsageTracker()

    @Test
    fun `counts foreground usage with elapsed realtime`() {
        tracker.onForegroundApp("com.example.app", elapsedRealtimeMillis = 1_000L, epochMillis = 10_000L)

        val usedMinutes = tracker.usedMinutes("com.example.app", elapsedRealtimeMillis = 181_000L)

        assertEquals(3, usedMinutes)
    }

    @Test
    fun `same foreground app does not close a session`() {
        tracker.onForegroundApp("com.example.app", elapsedRealtimeMillis = 1_000L, epochMillis = 10_000L)

        val transition = tracker.onForegroundApp("com.example.app", elapsedRealtimeMillis = 5_000L, epochMillis = 14_000L)

        assertNull(transition)
    }

    @Test
    fun `switching app closes previous session`() {
        tracker.onForegroundApp("com.example.first", elapsedRealtimeMillis = 1_000L, epochMillis = 10_000L)

        val transition = tracker.onForegroundApp("com.example.second", elapsedRealtimeMillis = 61_000L, epochMillis = 70_000L)

        assertEquals("com.example.first", transition?.packageName)
        assertEquals(10_000L, transition?.startedAtEpochMillis)
        assertEquals(70_000L, transition?.endedAtEpochMillis)
        assertEquals(1, tracker.usedMinutes("com.example.first", elapsedRealtimeMillis = 61_000L))
    }

    @Test
    fun `new tracker starts clean after service restart`() {
        tracker.onForegroundApp("com.example.app", elapsedRealtimeMillis = 1_000L, epochMillis = 10_000L)
        val restartedTracker = AppUsageTracker()

        val usedMinutes = restartedTracker.usedMinutes("com.example.app", elapsedRealtimeMillis = 181_000L)

        assertEquals(0, usedMinutes)
    }

    @Test
    fun `checkpoint closes active session without changing app`() {
        tracker.onForegroundApp("com.example.app", elapsedRealtimeMillis = 0L, epochMillis = 10_000L)

        val checkpoint = tracker.checkpointCurrent(
            elapsedRealtimeMillis = 300_000L,
            epochMillis = 310_000L,
            minimumDurationMillis = 300_000L,
        )

        assertEquals("com.example.app", checkpoint?.packageName)
        assertEquals(10_000L, checkpoint?.startedAtEpochMillis)
        assertEquals(310_000L, checkpoint?.endedAtEpochMillis)
        assertEquals(1, tracker.activeMinutes("com.example.app", elapsedRealtimeMillis = 360_000L))
    }
}
