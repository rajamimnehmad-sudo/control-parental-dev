package com.contentfilter.core.data

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
}
