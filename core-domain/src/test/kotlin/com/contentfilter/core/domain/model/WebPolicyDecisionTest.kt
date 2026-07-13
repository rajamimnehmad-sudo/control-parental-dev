package com.contentfilter.core.domain.model

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebPolicyDecisionTest {
    @Test
    fun `all supported outcomes retain complete metadata`() {
        WebPolicyOutcome.entries.forEach { outcome ->
            val decision = decision(outcome)

            assertTrue(decision.category.isNotBlank())
            assertTrue(decision.technicalReason.isNotBlank())
            assertFalse(decision.isExpired(EvaluatedAt))
            assertTrue(decision.isExpired(ExpiresAt))
        }
    }

    @Test
    fun `invalid metadata is rejected`() {
        val valid = decision()

        assertFailsWith<IllegalArgumentException> { valid.copy(category = "") }
        assertFailsWith<IllegalArgumentException> { valid.copy(confidence = -0.01) }
        assertFailsWith<IllegalArgumentException> { valid.copy(confidence = 1.01) }
        assertFailsWith<IllegalArgumentException> { valid.copy(version = -1) }
        assertFailsWith<IllegalArgumentException> { valid.copy(technicalReason = " ") }
        assertFailsWith<IllegalArgumentException> { valid.copy(evaluatedAtEpochMillis = -1) }
        assertFailsWith<IllegalArgumentException> { valid.copy(expiresAtEpochMillis = EvaluatedAt) }
    }

    private fun decision(outcome: WebPolicyOutcome = WebPolicyOutcome.Allow): WebPolicyDecision =
        WebPolicyDecision(
            outcome = outcome,
            category = "uncategorized",
            confidence = 1.0,
            source = WebPolicyDecisionSource.DefaultPolicy,
            version = 1,
            technicalReason = "test-decision",
            evaluatedAtEpochMillis = EvaluatedAt,
            expiresAtEpochMillis = ExpiresAt,
        )

    private companion object {
        const val EvaluatedAt = 1_000L
        const val ExpiresAt = 2_000L
    }
}
