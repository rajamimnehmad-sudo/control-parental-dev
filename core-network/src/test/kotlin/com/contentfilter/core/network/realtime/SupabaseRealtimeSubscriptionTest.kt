package com.contentfilter.core.network.realtime

import com.contentfilter.core.network.remote.SupabaseTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SupabaseRealtimeSubscriptionTest {
    @Test
    fun `policy broadcast produces directed revision event`() {
        val change =
            parseRealtimeChange(
                text =
                    """
                    {
                      "event":"broadcast",
                      "payload":{
                        "event":"policy_revision",
                        "payload":{
                          "request_id":"request-1",
                          "device_id":"device-1",
                          "policy_id":"policy-1",
                          "revision":200
                        }
                      }
                    }
                    """.trimIndent(),
                expectedDeviceId = "device-1",
            ) as RealtimeChange.PolicyRevision

        assertEquals("request-1", change.requestId)
        assertEquals(200L, change.revision)
    }

    @Test
    fun `broadcast for another device is ignored`() {
        val change =
            parseRealtimeChange(
                """{"event":"broadcast","payload":{"event":"policy_revision","payload":{"request_id":"r","device_id":"other","policy_id":"p","revision":2}}}""",
                expectedDeviceId = "device-1",
            )

        assertNull(change)
    }

    @Test
    fun `legacy table event remains available for maintenance sync`() {
        val change =
            parseRealtimeChange(
                """{"event":"postgres_changes","payload":{"table":"policies"}}""",
                expectedDeviceId = null,
            ) as RealtimeChange.Table

        assertEquals(SupabaseTable.Policies, change.table)
    }
}
