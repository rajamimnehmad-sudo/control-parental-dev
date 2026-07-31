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
                        DagPersistedTab(
                            "https://example.com/path",
                            "Ejemplo",
                            "0123456789abcdef0123456789abcdef",
                        ),
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

    @Test
    fun `legacy tabs restore without inventing a preview key`() {
        val restored =
            DagTabStateCodec.decode(
                """{"version":1,"activeIndex":0,"tabs":[{"url":"https://example.com","title":"Ejemplo"}]}""",
            ) { true }

        assertEquals(null, restored?.tabs?.single()?.previewKey)
    }

    @Test
    fun `unsafe preview keys are discarded`() {
        val restored =
            DagTabStateCodec.decode(
                """{"version":2,"activeIndex":0,"tabs":[{"url":"https://example.com","title":"Ejemplo","previewKey":"../outside"}]}""",
            ) { true }

        assertEquals(null, restored?.tabs?.single()?.previewKey)
    }

    @Test
    fun fiftyTabsRoundTripWithLastTabActive() {
        val original =
            DagPersistedTabs(
                tabs =
                    List(DagTabCapacityPolicy.MaxTabs) { index ->
                        DagPersistedTab("https://example.com/$index", "Pestaña $index")
                    },
                activeIndex = DagTabCapacityPolicy.MaxTabs - 1,
            )

        val restored =
            DagTabStateCodec.decode(
                DagTabStateCodec.encode(original),
                isAllowedUrl = { it.startsWith("https://") },
            )

        assertEquals(DagTabCapacityPolicy.MaxTabs, restored?.tabs?.size)
        assertEquals(DagTabCapacityPolicy.MaxTabs - 1, restored?.activeIndex)
    }

    @Test
    fun persistedStateNeverExceedsCapacity() {
        val oversized =
            DagPersistedTabs(
                tabs =
                    List(DagTabCapacityPolicy.MaxTabs + 1) { index ->
                        DagPersistedTab("https://example.com/$index", "Pestaña $index")
                    },
                activeIndex = DagTabCapacityPolicy.MaxTabs,
            )

        val restored =
            DagTabStateCodec.decode(
                DagTabStateCodec.encode(oversized),
                isAllowedUrl = { true },
            )

        assertEquals(DagTabCapacityPolicy.MaxTabs, restored?.tabs?.size)
        assertEquals(DagTabCapacityPolicy.MaxTabs - 1, restored?.activeIndex)
    }
}
