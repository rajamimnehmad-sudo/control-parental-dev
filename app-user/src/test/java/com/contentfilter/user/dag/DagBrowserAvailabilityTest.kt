package com.contentfilter.user.dag

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DagBrowserAvailabilityTest {
    @Test
    fun `closing dag clears the visual session but preserves local history`() {
        val history = listOf(historyEntry())
        val closed =
            DagBrowserUiState(
                dagEnabled = true,
                address = "https://example.com",
                view = DagView.Browser,
                pageStatus = DagPageStatus.Visible,
                results = listOf(searchResult()),
                history = history,
                requestedUrl = "https://example.com",
                loading = true,
                message = "Página visible",
                reviewCandidate = reviewCandidate(),
            ).withDagAvailability(enabled = false)

        assertFalse(closed.dagEnabled)
        assertTrue(closed.dagAvailabilityKnown)
        assertEquals("", closed.address)
        assertEquals(DagView.Start, closed.view)
        assertEquals(DagPageStatus.Idle, closed.pageStatus)
        assertTrue(closed.results.isEmpty())
        assertEquals(history, closed.history)
        assertNull(closed.requestedUrl)
        assertFalse(closed.loading)
        assertEquals("El administrador mantiene DAG cerrado.", closed.message)
        assertNull(closed.reviewCandidate)
    }

    @Test
    fun `reopening dag starts clean instead of restoring the revoked screen`() {
        val reopened =
            DagBrowserUiState(
                dagEnabled = false,
                address = "https://example.com",
                view = DagView.History,
                pageStatus = DagPageStatus.Uncertain,
                results = listOf(searchResult()),
                requestedUrl = "https://example.com",
                loading = true,
                message = "El administrador mantiene DAG cerrado.",
                reviewCandidate = reviewCandidate(),
            ).withDagAvailability(enabled = true)

        assertTrue(reopened.dagEnabled)
        assertTrue(reopened.dagAvailabilityKnown)
        assertEquals("", reopened.address)
        assertEquals(DagView.Start, reopened.view)
        assertEquals(DagPageStatus.Idle, reopened.pageStatus)
        assertTrue(reopened.results.isEmpty())
        assertNull(reopened.requestedUrl)
        assertFalse(reopened.loading)
        assertEquals("", reopened.message)
        assertNull(reopened.reviewCandidate)
    }

    private fun historyEntry() =
        DagHistoryEntry(
            id = "history-1",
            type = DagHistoryType.Search,
            value = "consulta segura",
            url = null,
            title = "consulta segura",
            visitedAtEpochMillis = 1L,
        )

    private fun searchResult() =
        DagSearchResult(
            title = "Ejemplo",
            url = "https://example.com",
            domain = "example.com",
            description = "Resultado de prueba",
            classification =
                DagClassificationResult(
                    decision = DagClassification.Allowed,
                    category = "general",
                    confidence = 1f,
                    modelVersion = DagContentClassifier.ModelVersion,
                ),
        )

    private fun reviewCandidate() =
        DagReviewCandidate(
            domain = "example.com",
            title = "Ejemplo",
            category = "unreadable",
            modelVersion = DagContentClassifier.ModelVersion,
        )
}
