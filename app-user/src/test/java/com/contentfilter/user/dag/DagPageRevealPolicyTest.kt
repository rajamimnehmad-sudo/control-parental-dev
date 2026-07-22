package com.contentfilter.user.dag

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DagPageRevealPolicyTest {
    private val allowed = DagClassificationResult(DagClassification.Allowed, "general", 0.82f, "test")

    @Test
    fun `electronics pages reveal structure without waiting for the whole viewport`() {
        val category =
            dagFastRevealCategory(
                url = "https://shop.example/galaxy",
                title = "Tecnología Samsung",
                text = "Smartphone, tablet, monitor y auriculares",
                classification = allowed,
            )

        assertEquals("electronics", category)
    }

    @Test
    fun `realistic electronics copy meets the two signal requirement`() {
        assertEquals(
            "electronics",
            dagFastRevealCategory(
                url = "https://www.samsung.com/ar/",
                title = "Samsung Argentina",
                text = "Smartphones Galaxy, televisores, monitores y electrodomésticos",
                classification = allowed,
            ),
        )
    }

    @Test
    fun `government domains use the fast reveal path`() {
        assertEquals(
            "government",
            dagFastRevealCategory(
                url = "https://tramites.buenosaires.gob.ar/consulta",
                title = "Consulta de trámite",
                text = "Ingresá los datos solicitados",
                classification = allowed,
            ),
        )
    }

    @Test
    fun `clothing and ambiguous pages keep the strict viewport gate`() {
        assertNull(
            dagFastRevealCategory(
                url = "https://shop.example/ropa",
                title = "Nueva colección",
                text = "Remeras, pantalones, vestidos y ropa para mujer",
                classification = allowed,
            ),
        )
        assertNull(
            dagFastRevealCategory(
                url = "https://example.com",
                title = "Página general",
                text = "Contenido sin una categoría suficientemente clara",
                classification = allowed,
            ),
        )
    }

    @Test
    fun `a blocked or uncertain decision can never use the fast path`() {
        listOf(DagClassification.Blocked, DagClassification.Uncertain).forEach { decision ->
            assertNull(
                dagFastRevealCategory(
                    url = "https://electronics.example",
                    title = "Tecnología y electrónica",
                    text = "Smartphone, tablet y notebook",
                    classification = allowed.copy(decision = decision),
                ),
            )
        }
    }
}
