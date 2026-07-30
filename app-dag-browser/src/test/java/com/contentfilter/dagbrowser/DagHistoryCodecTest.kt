package com.contentfilter.dagbrowser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DagHistoryCodecTest {
    @Test
    fun `history round trip keeps recent allowed entries`() {
        val entries =
            listOf(
                DagHistoryEntry("https://example.com/one", "One", 20L),
                DagHistoryEntry("https://example.com/two", "Two", 10L),
            )

        assertEquals(
            entries,
            DagHistoryCodec.decode(DagHistoryCodec.encode(entries), ::isAllowed),
        )
    }

    @Test
    fun `history rejects malformed and unsafe entries`() {
        val encoded =
            DagHistoryCodec.encode(
                listOf(
                    DagHistoryEntry("javascript:alert(1)", "Unsafe", 20L),
                    DagHistoryEntry("https://example.com", "Safe", 10L),
                ),
            )

        assertEquals(
            listOf(DagHistoryEntry("https://example.com", "Safe", 10L)),
            DagHistoryCodec.decode(encoded, ::isAllowed),
        )
        assertTrue(DagHistoryCodec.decode("{bad", ::isAllowed).isEmpty())
    }

    @Test
    fun `history is bounded and deduplicated while decoding`() {
        val entries =
            buildList {
                add(DagHistoryEntry("https://example.com/repeated", "Newest", 500L))
                add(DagHistoryEntry("https://example.com/repeated", "Older", 400L))
                repeat(DagHistoryCodec.MaxEntries + 20) { index ->
                    add(DagHistoryEntry("https://example.com/$index", "$index", index.toLong()))
                }
            }

        val decoded = DagHistoryCodec.decode(DagHistoryCodec.encode(entries), ::isAllowed)

        assertEquals(DagHistoryCodec.MaxEntries - 1, decoded.size)
        assertEquals("Newest", decoded.first().title)
    }

    private fun isAllowed(url: String): Boolean = url.startsWith("https://")
}
