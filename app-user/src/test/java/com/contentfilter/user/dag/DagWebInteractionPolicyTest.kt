package com.contentfilter.user.dag

import kotlin.test.Test
import kotlin.test.assertEquals

class DagWebInteractionPolicyTest {
    @Test
    fun `dynamic DOM security work is split into bounded frame batches`() {
        assertEquals(0, dagDomSecurityBatchCount(0))
        assertEquals(1, dagDomSecurityBatchCount(1))
        assertEquals(1, dagDomSecurityBatchCount(DagDomSecurityBatchPolicy.MaximumNodesPerFrame))
        assertEquals(2, dagDomSecurityBatchCount(DagDomSecurityBatchPolicy.MaximumNodesPerFrame + 1))
        assertEquals(21, dagDomSecurityBatchCount(1_000))
    }
}
