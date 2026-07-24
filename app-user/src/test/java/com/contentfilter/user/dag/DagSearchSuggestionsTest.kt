package com.contentfilter.user.dag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DagSearchSuggestionsTest {
    @Test
    fun `remote suggestions provide broad live context`() {
        val suggestions =
            dagSearchSuggestionCandidates(
                "coca",
                emptyList(),
                listOf("Coca-Cola gaseosa", "Precio de Coca-Cola", "Historia de Coca-Cola"),
            )

        assertEquals("Coca-Cola gaseosa", suggestions.first())
        assertTrue(suggestions.all { it.contains("Coca-Cola", ignoreCase = true) })
    }

    @Test
    fun `history suggestions are local deduplicated and limited`() {
        val history =
            (1..8).map { index ->
                DagHistoryEntry(
                    id = index.toString(),
                    type = DagHistoryType.Search,
                    value = "Carrefour $index",
                    url = null,
                    title = if (index == 1) "Carrefour 1" else null,
                    visitedAtEpochMillis = index.toLong(),
                )
            }

        val suggestions = dagSearchSuggestionCandidates("carre", history)

        assertEquals(8, suggestions.size)
        assertEquals(suggestions.distinct(), suggestions)
    }

    @Test
    fun `blank input has no suggestions`() {
        assertTrue(dagSearchSuggestionCandidates("  ", emptyList()).isEmpty())
    }

    @Test
    fun `history is shown before remote and duplicates are removed`() {
        val history =
            listOf(
                DagHistoryEntry(
                    id = "1",
                    type = DagHistoryType.Search,
                    value = "Frávega electrodomésticos",
                    url = null,
                    title = null,
                    visitedAtEpochMillis = 1L,
                ),
            )

        val suggestions =
            dagSearchSuggestionCandidates(
                "frav",
                history,
                listOf("Frávega electrodomésticos", "Frávega ofertas"),
            )

        assertEquals(listOf("Frávega electrodomésticos", "Frávega ofertas"), suggestions)
    }
}
