package com.contentfilter.feature.accessibility.service

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ForegroundDecisionDiagnosticGateTest {
    @Test
    fun `records the first decision and suppresses identical repeats`() {
        val gate = ForegroundDecisionDiagnosticGate()

        assertTrue(gate.shouldRecord("calculator|Allow|2|0|4"))
        assertFalse(gate.shouldRecord("calculator|Allow|2|0|4"))
    }

    @Test
    fun `records a meaningful decision input change`() {
        val gate = ForegroundDecisionDiagnosticGate()

        assertTrue(gate.shouldRecord("calculator|Allow|2|0|4"))
        assertTrue(gate.shouldRecord("calculator|Allow|3|0|4"))
        assertTrue(gate.shouldRecord("calculator|Block|3|0|4"))
    }
}
