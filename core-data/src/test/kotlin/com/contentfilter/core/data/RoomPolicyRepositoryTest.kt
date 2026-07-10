package com.contentfilter.core.data

import com.contentfilter.core.database.entity.PolicyEntity
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoomPolicyRepositoryTest {
    @Test
    fun `policy parent is queued before dependent rules`() {
        val operations = buildPolicyOutboxPlan(ruleCount = 2, revision = 100L)

        assertEquals(listOf("policies", "policy_rules", "policy_rules"), operations.map { it.tableName })
        assertTrue(
            operations.zipWithNext().all { (left, right) ->
                left.createdAtEpochMillis < right.createdAtEpochMillis
            },
        )
    }

    @Test
    fun `policy mutation operations keep request revision and deterministic row ids`() {
        val operations =
            buildPolicyOutboxOperations(
                policy = PolicyEntity("policy-1", "device-1", 200L, true, 200L),
                rules =
                    listOf(
                        PolicyRule(
                            id = "rule-1",
                            scope = RuleScope.App,
                            target = "com.example.app",
                            action = RuleAction.Block,
                            priority = 100,
                            enabled = true,
                        ),
                    ),
                accountId = "account-1",
                revision = 200L,
                requestId = "request-1",
                deviceId = "device-1",
            )

        assertEquals(listOf("policies:policy-1", "policy_rules:rule-1"), operations.map { it.id })
        assertTrue(operations.all { it.requestId == "request-1" && it.revision == 200L })
        assertTrue(operations.first().priority > operations.last().priority)
    }
}
