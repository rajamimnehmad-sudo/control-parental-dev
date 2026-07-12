package com.contentfilter.core.data

import com.contentfilter.core.database.entity.PolicyEntity
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import org.json.JSONObject
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

    @Test
    fun `policy mutation explicitly queues a disabled web rule`() {
        val operations =
            buildPolicyOutboxOperations(
                policy = PolicyEntity("policy-1", "device-1", 300L, true, 300L),
                rules =
                    listOf(
                        PolicyRule(
                            id = "web-rule",
                            scope = RuleScope.Domain,
                            target = "__web_navigation_blocked__",
                            action = RuleAction.Block,
                            priority = 5_000,
                            enabled = false,
                        ),
                    ),
                accountId = "account-1",
                revision = 300L,
                requestId = "allow-web",
                deviceId = "device-1",
            )

        val rulePayload = JSONObject(operations.single { it.tableName == "policy_rules" }.payload)

        assertEquals(false, rulePayload.getBoolean("enabled"))
        assertEquals("allow-web", operations.last().requestId)
        assertEquals(300L, operations.last().revision)
    }
}
