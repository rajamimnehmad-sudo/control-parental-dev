package com.contentfilter.dagbrowser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DagTabStateCodecTest {
    @Test
    fun roundTripPreservesOrderedTabsAndActiveIndex() {
        val original =
            DagPersistedTabs(
                tabs =
                    listOf(
                        DagPersistedTab("about:blank", "Nueva pestaña"),
                        DagPersistedTab("https://example.com/path", "Ejemplo"),
                    ),
                activeIndex = 1,
            )

        val restored =
            DagTabStateCodec.decode(
                DagTabStateCodec.encode(original),
                isAllowedUrl = { it == "about:blank" || it.startsWith("https://") },
            )

        assertEquals(original, restored)
    }

    @Test
    fun decodeDropsUnsafeUrlsAndClampsActiveIndex() {
        val raw =
            """
            {
              "version": 1,
              "activeIndex": 7,
              "tabs": [
                {"url":"javascript:alert(1)","title":"Mala"},
                {"url":"https://example.com","title":"Buena"}
              ]
            }
            """.trimIndent()

        val restored =
            DagTabStateCodec.decode(raw) { it.startsWith("https://") }

        assertEquals(listOf(DagPersistedTab("https://example.com", "Buena")), restored?.tabs)
        assertEquals(0, restored?.activeIndex)
    }

    @Test
    fun invalidStateFailsClosed() {
        assertNull(DagTabStateCodec.decode("""{"version":99}""") { true })
    }
}
