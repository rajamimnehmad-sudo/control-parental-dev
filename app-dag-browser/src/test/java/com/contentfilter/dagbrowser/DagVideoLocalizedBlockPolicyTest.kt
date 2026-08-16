package com.contentfilter.dagbrowser

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DagVideoLocalizedBlockPolicyTest {
    @Test
    fun `content or bootstrap rejection stays localized to the video`() {
        assertTrue(DagVideoLocalizedBlockPolicy.supports("frame_blocked"))
        assertTrue(DagVideoLocalizedBlockPolicy.supports("bootstrap_no_backing_timeout"))
        assertTrue(DagVideoLocalizedBlockPolicy.supports("bootstrap_play_rejected"))
        assertTrue(DagVideoLocalizedBlockPolicy.supports("bootstrap_unavailable"))
    }

    @Test
    fun `authority and document failures do not become ordinary placeholders`() {
        assertFalse(DagVideoLocalizedBlockPolicy.supports("authority_changed"))
        assertFalse(DagVideoLocalizedBlockPolicy.supports("source_changed"))
        assertFalse(DagVideoLocalizedBlockPolicy.supports("document_retired"))
        assertFalse(DagVideoLocalizedBlockPolicy.supports(null))
    }
}
