package com.contentfilter.dagbrowser

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DagVideoSeekRearmPolicyTest {
    @Test
    fun `exact active document may rearm after durable revoke`() {
        assertTrue(DagVideoSeekRearmPolicy.allow(true, true, true, true, true))
    }

    @Test
    fun `every mismatched or unavailable condition remains closed`() {
        for (index in 0 until 5) {
            val conditions = MutableList(5) { true }.also { it[index] = false }
            assertFalse(
                DagVideoSeekRearmPolicy.allow(
                    runtimeEnabled = conditions[0],
                    activeTab = conditions[1],
                    attachedSession = conditions[2],
                    openSession = conditions[3],
                    exactDocument = conditions[4],
                ),
            )
        }
    }
}
