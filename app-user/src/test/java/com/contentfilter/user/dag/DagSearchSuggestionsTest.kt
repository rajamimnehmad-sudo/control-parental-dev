package com.contentfilter.user.dag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DagSearchSuggestionsTest {
    @Test
    fun `coca offers explicit soft drink context`() {
        val suggestions = dagSearchSuggestionCandidates("coca", emptyList())

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

        assertEquals(5, suggestions.size)
        assertEquals(suggestions.distinct(), suggestions)
    }

    @Test
    fun `blank input has no suggestions`() {
        assertTrue(dagSearchSuggestionCandidates("  ", emptyList()).isEmpty())
    }

    @Test
    fun `live local catalog suggests without history`() {
        assertTrue(dagSearchSuggestionCandidates("frav", emptyList()).contains("Frávega electrodomésticos"))
    }

    @Test
    fun `safe spelling proposes a close correction`() {
        assertEquals("fravega", dagDidYouMeanSuggestion("frabega"))
        assertEquals(null, dagDidYouMeanSuggestion("fravega"))
    }
}
