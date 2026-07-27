package com.contentfilter.dagbrowser

import kotlin.test.Test
import kotlin.test.assertEquals

class DagMediaAnalysisPolicyTest {
    @Test
    fun `valid candidate remains blocked while analyzer is unavailable`() {
        val decision =
            DagMediaAnalysisPolicy.decide(
                candidate(
                    sourceUrl = "https://images.example/photo.jpg",
                    documentUrl = "https://search.example/results",
                ),
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals(DagMediaAnalysisPolicy.AnalyzerUnavailableReason, decision.reason)
    }

    @Test
    fun `invalid or local source remains blocked`() {
        val decision =
            DagMediaAnalysisPolicy.decide(
                candidate(
                    sourceUrl = "file:///sdcard/private.jpg",
                    documentUrl = "https://search.example/results",
                ),
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals(DagMediaAnalysisPolicy.InvalidCandidateReason, decision.reason)
    }

    @Test
    fun `oversized metadata remains blocked`() {
        val decision =
            DagMediaAnalysisPolicy.decide(
                candidate(
                    sourceUrl = "https://images.example/photo.jpg",
                    documentUrl = "https://search.example/results",
                    altText = "x".repeat(257),
                ),
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals(DagMediaAnalysisPolicy.InvalidCandidateReason, decision.reason)
    }

    private fun candidate(
        sourceUrl: String,
        documentUrl: String,
        altText: String = "resultado",
    ) = DagMediaCandidate(
        candidateId = "candidate_1",
        sourceUrl = sourceUrl,
        documentUrl = documentUrl,
        altText = altText,
        width = 320,
        height = 240,
    )
}
