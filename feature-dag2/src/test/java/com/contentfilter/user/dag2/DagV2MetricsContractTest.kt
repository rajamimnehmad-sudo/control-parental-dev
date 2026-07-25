package com.contentfilter.user.dag2

import kotlin.test.Test
import kotlin.test.assertEquals

class DagV2MetricsContractTest {
    @Test
    fun `foundation metrics expose only the required sanitized event names`() {
        assertEquals(
            setOf(
                "document_started",
                "document_committed",
                "full_page_analysis_started",
                "full_page_analysis_completed",
                "full_page_analysis_count",
                "structure_visible",
                "visual_placeholder_ready",
                "stale_result_discarded",
                "session_cancelled",
                "functional_stable_20s",
            ),
            DagV2MetricNames.RequiredFoundationEvents,
        )
    }
}
