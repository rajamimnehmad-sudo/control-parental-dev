package com.contentfilter.dagbrowser

import kotlin.test.Test
import kotlin.test.assertEquals

class DagMediaInteractionPolicyTest {
    @Test
    fun `touch interaction keeps one analysis worker and restores the idle capacity`() {
        assertEquals(
            1,
            DagMediaInteractionPolicy.analysisThreads(interacting = true, idleThreads = 2),
        )
        assertEquals(
            2,
            DagMediaInteractionPolicy.analysisThreads(interacting = false, idleThreads = 2),
        )
        assertEquals(250L, DagMediaInteractionPolicy.RestoreDelayMillis)
    }
}
