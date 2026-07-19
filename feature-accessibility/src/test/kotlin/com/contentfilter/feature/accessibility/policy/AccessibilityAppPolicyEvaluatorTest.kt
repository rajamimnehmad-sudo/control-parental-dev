package com.contentfilter.feature.accessibility.policy

import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.model.LicenseState
import com.contentfilter.core.domain.model.SystemHealthSnapshot
import com.contentfilter.core.domain.model.UpdateState
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccessibilityAppPolicyEvaluatorTest {
    private val evaluator = AccessibilityAppPolicyEvaluator(FixedClock)

    @Test
    fun `system clock always evaluates Argentina local time`() {
        val epochMillis = Instant.parse("2026-07-19T03:30:00Z").toEpochMilli()

        val clock = SystemAccessibilityClock()
        assertEquals(30, clock.minuteOfDay(epochMillis))
        assertEquals(7, clock.isoDayOfWeek(epochMillis))
    }

    @Test
    fun `builds app policy context without android framework state`() {
        val context =
            evaluator.buildContext(
                packageName = "com.example.app",
                usedMinutesToday = 12,
                health = activeHealth(),
            )

        assertEquals("com.example.app", context.packageName)
        assertEquals(12, context.usedMinutesToday)
        assertEquals(12 * 60, context.time.minuteOfDay)
        assertTrue(context.device.isActivated)
    }

    @Test
    fun `expired and suspended licenses do not allow enforcement`() {
        val expired =
            evaluator.buildContext(
                "com.example.app",
                0,
                activeHealth().copy(licenseState = LicenseState.Expired),
            )
        val suspended =
            evaluator.buildContext(
                "com.example.app",
                0,
                activeHealth().copy(licenseState = LicenseState.Suspended),
            )

        assertFalse(expired.device.isActivated)
        assertFalse(suspended.device.isActivated)
    }

    private fun activeHealth(): SystemHealthSnapshot =
        SystemHealthSnapshot(
            vpnState = ComponentState.Enabled,
            accessibilityState = ComponentState.Enabled,
            syncState = ComponentState.Enabled,
            integrityState = ComponentState.Enabled,
            databaseState = ComponentState.Enabled,
            licenseState = LicenseState.Active,
            updateState = UpdateState.Current,
            checkedAtEpochMillis = FixedEpochMillis,
        )

    private object FixedClock : AccessibilityClock {
        override fun elapsedRealtimeMillis(): Long = 42_000L

        override fun nowEpochMillis(): Long = FixedEpochMillis

        override fun minuteOfDay(epochMillis: Long): Int = 12 * 60

        override fun isoDayOfWeek(epochMillis: Long): Int = 3
    }

    private companion object {
        const val FixedEpochMillis = 1_735_689_600_000L
    }
}
