package com.contentfilter.user.dag

import kotlin.test.Test
import kotlin.test.assertEquals

class DagHistoryStoreTest {
    @Test
    fun `history codec preserves searches pages and unicode`() {
        val expected =
            listOf(
                DagHistoryEntry(
                    id = "search-1",
                    type = DagHistoryType.Search,
                    value = "זמני תפילה",
                    url = null,
                    title = "זמני תפילה",
                    visitedAtEpochMillis = 10L,
                ),
                DagHistoryEntry(
                    id = "page-1",
                    type = DagHistoryType.Page,
                    value = "https://example.com/guía",
                    url = "https://example.com/guía",
                    title = "Guía segura",
                    visitedAtEpochMillis = 20L,
                ),
            )

        val decoded = DagHistoryStore.decodeEntries(DagHistoryStore.encodeEntries(expected))

        assertEquals(expected, decoded)
    }
}
