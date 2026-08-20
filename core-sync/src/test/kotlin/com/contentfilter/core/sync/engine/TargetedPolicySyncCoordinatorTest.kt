package com.contentfilter.core.sync.engine

import com.contentfilter.core.domain.model.PolicyMutationReceipt
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TargetedPolicySyncCoordinatorTest {
    @Test
    fun `ack is sent only after consumers report same revision`(): Unit =
        runBlocking {
            val tracker = EffectivePolicyApplicationTracker()
            val syncEngine = FakeSyncEngine()
            val coordinator = TargetedPolicySyncCoordinator(syncEngine, tracker)

            val refresh =
                async {
                    coordinator.refresh(
                        deviceId = DeviceId,
                        policyId = PolicyId,
                        requestId = "request-1",
                        reason = "manual",
                    )
                }

            delay(100)
            assertEquals(0, syncEngine.acknowledgeCalls)

            tracker.report(PolicyConsumer.Vpn, PolicyId, 200L)
            delay(100)
            assertEquals(0, syncEngine.acknowledgeCalls)

            tracker.report(PolicyConsumer.Accessibility, PolicyId, 200L)
            val result = refresh.await()

            assertTrue(result.success)
            assertEquals(1, syncEngine.acknowledgeCalls)
        }

    @Test
    fun `ack is not sent when policy sync is not applied locally`(): Unit =
        runBlocking {
            val tracker = EffectivePolicyApplicationTracker()
            val syncEngine =
                FakeSyncEngine().apply {
                    roomApplied = false
                    success = false
                }
            val coordinator = TargetedPolicySyncCoordinator(syncEngine, tracker)

            val result = coordinator.refresh(DeviceId, PolicyId, reason = "manual")

            assertFalse(result.roomApplied)
            assertEquals(0, syncEngine.acknowledgeCalls)
            assertEquals(false, result.success)
        }

    private class FakeSyncEngine : SyncEngine {
        var acknowledgeCalls = 0
        var roomApplied = true
        var success = true

        override suspend fun syncOnce() = SyncResult(true, "ok")

        override suspend fun syncCoreDataFull() = SyncResult(true, "ok")

        override suspend fun syncDevicesFull() = SyncResult(true, "ok")

        override suspend fun syncAccessRequestsFull() = SyncResult(true, "ok")

        override suspend fun syncRequestResultsFull() = SyncResult(true, "ok")

        override suspend fun syncPolicyChanges(receipt: PolicyMutationReceipt): PolicyFastSyncResult =
            PolicyFastSyncResult(
                localSaved = true,
                serverConfirmed = true,
                notificationDelivered = true,
                policyId = PolicyId,
                revision = 200L,
                pendingOperationIds = emptyList(),
            )

        override suspend fun pullPolicyRevision(
            requestId: String,
            deviceId: String,
            policyId: String?,
            minimumRevision: Long?,
            reason: String,
        ): PolicyPullResult =
            PolicyPullResult(
                success = success,
                requestId = requestId,
                deviceId = deviceId,
                policyId = PolicyId,
                revision = 200L,
                roomApplied = roomApplied,
            )

        override suspend fun acknowledgePolicyApplied(
            requestId: String,
            deviceId: String,
            policyId: String,
            revision: Long,
        ): Boolean {
            acknowledgeCalls++
            return true
        }

        override suspend fun waitForPolicyApplied(
            receipt: PolicyMutationReceipt,
            timeoutMillis: Long,
        ) = PolicyApplicationResult(
            state = PolicyApplicationState.Pending,
            revision = receipt.revision,
        )
    }

    private companion object {
        const val DeviceId = "device-1"
        const val PolicyId = "policy-1"
    }
}
