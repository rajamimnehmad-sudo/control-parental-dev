package com.contentfilter.feature.accessibility.service

import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InstallApprovalPolicyTest {
    @Test
    fun `requires an enabled explicit allow for the exact package`() {
        val approved = rule(target = "com.example.approved", action = RuleAction.Allow, enabled = true)

        assertTrue(hasExplicitAppApproval("com.example.approved", listOf(approved)))
        assertFalse(hasExplicitAppApproval("com.example.other", listOf(approved)))
        assertFalse(hasExplicitAppApproval("com.example.approved", listOf(approved.copy(enabled = false))))
        assertFalse(
            hasExplicitAppApproval(
                "com.example.approved",
                listOf(rule("com.example.approved", RuleAction.Block, enabled = true)),
            ),
        )
    }

    private fun rule(
        target: String,
        action: RuleAction,
        enabled: Boolean,
    ): PolicyRule {
        return PolicyRule(
            id = "rule-$target-$action",
            scope = RuleScope.App,
            target = target,
            action = action,
            priority = 1_000,
            enabled = enabled,
        )
    }
}
