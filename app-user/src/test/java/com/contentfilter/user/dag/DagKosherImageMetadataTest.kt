package com.contentfilter.user.dag

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DagKosherImageMetadataTest {
    @Test
    fun `intimate product metadata is hidden per image`() {
        assertTrue(isIntimateImageMetadata("Lencería íntima para mujer"))
        assertTrue(isIntimateImageMetadata("Women's underwear product photo"))
        assertTrue(isIntimateImageMetadata("bralette-and-panties.webp"))
        assertTrue(isIntimateImageMetadata("traje de baño femenino"))
    }

    @Test
    fun `ordinary store images remain eligible for visual classification`() {
        assertFalse(isIntimateImageMetadata("Campera de mujer color negro"))
        assertFalse(isIntimateImageMetadata("brand-logo.svg"))
        assertFalse(isIntimateImageMetadata("pantalón y camisa"))
    }
}
