package com.contentfilter.dagbrowser

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DagVideoDocumentRearmPolicyTest {
    @Test
    fun `safe media identity changes may rearm the surviving document`() {
        assertTrue(DagVideoDocumentRearmPolicy.supports("seek_requested"))
        assertTrue(DagVideoDocumentRearmPolicy.supports("authority_changed"))
        assertTrue(DagVideoDocumentRearmPolicy.supports("viewport_changed"))
        assertTrue(DagVideoDocumentRearmPolicy.supports("source_changed"))
        assertTrue(DagVideoDocumentRearmPolicy.supports("active_video_mutated"))
    }

    @Test
    fun `security and terminal failures never rearm automatically`() {
        assertFalse(DagVideoDocumentRearmPolicy.supports("frame_blocked"))
        assertFalse(DagVideoDocumentRearmPolicy.supports("unsafe_presentation"))
        assertFalse(DagVideoDocumentRearmPolicy.supports("frame_ready_timeout"))
        assertFalse(DagVideoDocumentRearmPolicy.supports("capture_limit"))
        assertFalse(DagVideoDocumentRearmPolicy.supports("document_retired"))
    }

    @Test
    fun `exact active document may rearm after durable close`() {
        assertTrue(DagVideoDocumentRearmPolicy.allow(true, true, true, true, true))
    }

    @Test
    fun `every mismatched or unavailable condition remains closed`() {
        for (index in 0 until 5) {
            val conditions = MutableList(5) { true }.also { it[index] = false }
            assertFalse(
                DagVideoDocumentRearmPolicy.allow(
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
