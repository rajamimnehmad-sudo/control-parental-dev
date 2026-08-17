package com.contentfilter.dagbrowser

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DagAutomaticDiagnosticPolicyTest {
    @Test
    fun `reports only failures that can leave video black`() {
        listOf(
            "cover_timeout",
            "frame_ready_timeout",
            "revoke_timeout",
            "revoke_request_not_delivered",
        ).forEach { reason ->
            assertTrue(
                DagAutomaticDiagnosticPolicy.shouldReport(reason, false, true, 10_000L, 0L),
                reason,
            )
        }
        assertFalse(DagAutomaticDiagnosticPolicy.shouldReport("model_filter", false, true, 10_000L, 0L))
        assertFalse(DagAutomaticDiagnosticPolicy.shouldReport("safe_skip", false, true, 10_000L, 0L))
        assertFalse(DagAutomaticDiagnosticPolicy.shouldReport("viewport_changed", false, true, 10_000L, 0L))
    }

    @Test
    fun `never auto uploads private activity or unavailable reports`() {
        assertFalse(DagAutomaticDiagnosticPolicy.shouldReport("cover_timeout", true, true, 10_000L, 0L))
        assertFalse(DagAutomaticDiagnosticPolicy.shouldReport("cover_timeout", false, false, 10_000L, 0L))
    }

    @Test
    fun `one incident starts per bounded cooldown and clock rollback recovers`() {
        val start = 100_000L
        assertFalse(
            DagAutomaticDiagnosticPolicy.shouldReport(
                "cover_timeout",
                false,
                true,
                start + DagAutomaticDiagnosticPolicy.CooldownMillis - 1,
                start,
            ),
        )
        assertTrue(
            DagAutomaticDiagnosticPolicy.shouldReport(
                "cover_timeout",
                false,
                true,
                start + DagAutomaticDiagnosticPolicy.CooldownMillis,
                start,
            ),
        )
        assertTrue(DagAutomaticDiagnosticPolicy.shouldReport("cover_timeout", false, true, start - 1, start))
    }
}
