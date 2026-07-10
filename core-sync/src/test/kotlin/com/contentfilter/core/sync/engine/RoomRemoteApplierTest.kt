package com.contentfilter.core.sync.engine

import com.contentfilter.core.database.entity.PolicyEntity
import com.contentfilter.core.database.entity.PolicyRuleEntity
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoomRemoteApplierTest {
    @Test
    fun `older remote policy cannot replace newer local revision`() {
        val local = policy(version = 200L, updatedAt = 200L)
        val remote = policy(version = 100L, updatedAt = 100L)

        assertFalse(shouldApplyRemotePolicy(remote, currentById = local, currentActive = local))
    }

    @Test
    fun `older active policy cannot deactivate newer policy for same device`() {
        val local = policy(id = "local", version = 200L, updatedAt = 200L)
        val remote = policy(id = "remote", version = 100L, updatedAt = 100L)

        assertFalse(shouldApplyRemotePolicy(remote, currentById = null, currentActive = local))
    }

    @Test
    fun `newer remote policy replaces local revision`() {
        val local = policy(version = 100L, updatedAt = 100L)
        val remote = policy(version = 200L, updatedAt = 200L)

        assertTrue(shouldApplyRemotePolicy(remote, currentById = local, currentActive = local))
    }

    @Test
    fun `older remote rule cannot overwrite newer local request`() {
        assertFalse(shouldApplyRemoteRule(rule(updatedAt = 100L), rule(updatedAt = 200L)))
        assertTrue(shouldApplyRemoteRule(rule(updatedAt = 200L), rule(updatedAt = 100L)))
    }

    private fun policy(
        id: String = "policy",
        version: Long,
        updatedAt: Long,
    ): PolicyEntity =
        PolicyEntity(
            id = id,
            deviceId = "device",
            version = version,
            active = true,
            updatedAtEpochMillis = updatedAt,
        )

    private fun rule(updatedAt: Long): PolicyRuleEntity =
        PolicyRuleEntity(
            id = "rule",
            policyId = "policy",
            scope = "Domain",
            target = "__web_navigation_blocked__",
            action = "Block",
            priority = 5_000,
            enabled = true,
            updatedAtEpochMillis = updatedAt,
        )
}
