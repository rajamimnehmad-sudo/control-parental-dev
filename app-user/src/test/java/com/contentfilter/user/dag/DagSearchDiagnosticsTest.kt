package com.contentfilter.user.dag

import kotlin.test.Test
import kotlin.test.assertEquals

class DagSearchDiagnosticsTest {
    @Test
    fun `every Brave result has exactly one aggregate destination`() {
        val diagnostics =
            dagSearchDiagnostics(
                braveReceived = 10,
                serverRejected = 2,
                decisions =
                    listOf(
                        DagSearchDecisionReason.Allowed,
                        DagSearchDecisionReason.Allowed,
                        DagSearchDecisionReason.Uncertain,
                        DagSearchDecisionReason.DomainListBlock,
                        DagSearchDecisionReason.AdminRuleBlock,
                        DagSearchDecisionReason.PlatformBlock,
                        DagSearchDecisionReason.LocalClassifierBlock,
                        DagSearchDecisionReason.LocalClassifierBlock,
                    ),
            )

        assertEquals(10, diagnostics.accounted)
        assertEquals(3, diagnostics.shown)
        assertEquals(2, diagnostics.localClassifierBlocked)
    }
}
