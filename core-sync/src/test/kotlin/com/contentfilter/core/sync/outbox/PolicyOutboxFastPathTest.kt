package com.contentfilter.core.sync.outbox

import com.contentfilter.core.database.entity.OutboxOperationEntity
import com.contentfilter.core.network.remote.RemoteResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PolicyOutboxFastPathTest {
    @Test
    fun `policy parent is ordered before rules and limits`() {
        val operations =
            listOf(
                operation("rule", "policy_rules", "rule-1", revision = 200L, priority = 90),
                operation("limit", "daily_limits", "limit-1", revision = 200L, priority = 80),
                operation("policy", "policies", "policy-1", revision = 200L, priority = 100),
            ).sortedWith(OutboxPriorityComparator)

        assertEquals(listOf("policies", "policy_rules", "daily_limits"), operations.map { it.tableName })
    }

    @Test
    fun `new revision supersedes legacy operation for the same remote row`() {
        val legacy = operation("legacy-random", "policy_rules", "rule-1", revision = 100L, priority = 0)
        val current = operation("policy_rules:rule-1", "policy_rules", "rule-1", revision = 200L, priority = 90)

        val plan = compactOutboxOperations(listOf(legacy, current))

        assertEquals(listOf(current.id), plan.pending.map { it.id })
        assertEquals(listOf(legacy.id), plan.superseded.map { it.id })
    }

    @Test
    fun `new critical operation sorts before old unrelated backlog`() {
        val oldRequest = operation("request", "access_requests", "request-1", revision = 1L, priority = 0)
        val currentPolicy = operation("policy", "policies", "policy-1", revision = 200L, priority = 100)

        val sorted = listOf(oldRequest, currentPolicy).sortedWith(OutboxPriorityComparator)

        assertEquals("policies", sorted.first().tableName)
    }

    @Test
    fun `rapid replacement keeps only newest payload`() {
        val revisions =
            listOf(100L, 200L, 300L).map { revision ->
                operation("policy-$revision", "policies", "policy-1", revision, priority = 100)
            }

        val plan = compactOutboxOperations(revisions)

        assertEquals(300L, plan.pending.single().revision)
        assertTrue(plan.superseded.mapNotNull { it.revision }.containsAll(listOf(100L, 200L)))
    }

    @Test
    fun `fast path selects only operations from requested policy`() {
        val requested = operation("policy", "policies", "policy-1", revision = 200L, priority = 100)
        val requestedRule = operation("rule", "policy_rules", "rule-1", revision = 200L, priority = 90)
        val unrelated =
            operation("other", "policy_rules", "rule-2", revision = 300L, priority = 90)
                .copy(aggregateId = "policy-2", payload = "{\"id\":\"rule-2\",\"policy_id\":\"policy-2\"}")

        val selected = filterPolicyMutationOperations(listOf(unrelated, requestedRule, requested), "policy-1")

        assertEquals(setOf(requested.id, requestedRule.id), selected.map { it.id }.toSet())
    }

    @Test
    fun `retryable offline failure stays pending`() {
        val status = nextOutboxStatus(RemoteResult.Failure("offline", retryable = true))

        assertEquals(OutboxStatus.Pending, status)
    }

    private fun operation(
        id: String,
        table: String,
        rowId: String,
        revision: Long,
        priority: Int,
    ): OutboxOperationEntity =
        OutboxOperationEntity(
            id = id,
            tableName = table,
            operation = "Upsert",
            payload =
                if (table == "policies") {
                    "{\"id\":\"$rowId\",\"version\":$revision}"
                } else {
                    "{\"id\":\"$rowId\",\"policy_id\":\"policy-1\",\"updated_at\":\"2026-07-10T00:00:00Z\"}"
                },
            status = "Pending",
            attemptCount = 0,
            createdAtEpochMillis = revision,
            updatedAtEpochMillis = revision,
            requestId = "request-$revision",
            aggregateId = "policy-1",
            deviceId = "device-1",
            revision = revision,
            priority = priority,
        )
}
