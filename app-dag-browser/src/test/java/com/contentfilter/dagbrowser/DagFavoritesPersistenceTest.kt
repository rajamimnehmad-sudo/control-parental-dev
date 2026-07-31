package com.contentfilter.dagbrowser

import kotlin.test.Test
import kotlin.test.assertEquals

class DagFavoritesPersistenceTest {
    @Test
    fun roundTripKeepsOrderAndDropsUnsafeUrls() {
        val original =
            listOf(
                DagFavorite("https://example.com", "Ejemplo"),
                DagFavorite("https://example.org", "Otro"),
            )

        val restored =
            DagFavoritesCodec.decode(DagFavoritesCodec.encode(original)) {
                it.startsWith("https://")
            }

        assertEquals(original, restored)
    }

    @Test
    fun duplicateUrlsAreCollapsed() {
        val raw =
            DagFavoritesCodec.encode(
                listOf(
                    DagFavorite("https://example.com", "Primero"),
                    DagFavorite("https://example.com", "Segundo"),
                ),
            )

        val restored = DagFavoritesCodec.decode(raw) { true }

        assertEquals(1, restored.size)
        assertEquals("Primero", restored.single().title)
    }
}
