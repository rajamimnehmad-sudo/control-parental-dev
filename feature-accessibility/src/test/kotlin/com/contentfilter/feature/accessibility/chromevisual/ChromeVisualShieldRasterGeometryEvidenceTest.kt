package com.contentfilter.feature.accessibility.chromevisual

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChromeVisualShieldRasterGeometryEvidenceTest {
    @Test
    fun `physical offset is reported without changing any raster or planner input`() {
        val search = box(162, 602, 918, 1324)
        val carrier = box(162, 600, 918, 1324)
        val expectedFull = box(266, 688, 814, 1236)
        val observedFull = box(265, 645, 815, 1194)
        val expectedCrop = box(104, 86, 652, 634)
        val observedCrop = box(103, 43, 653, 592)

        val evidence =
            ChromeVisualShieldRasterGeometryEvidenceFactory.create(
                search,
                carrier,
                expectedFull,
                observedFull,
                expectedCrop,
                observedCrop,
            )

        assertEquals(-42.5, evidence.mappingDelta?.deltaY)
        assertTrue(evidence.oracleCoverage < 0.98)
        assertTrue(evidence.candidateAreaRatio < 1.5)
        assertEquals(1.0, evidence.insideSearchFraction)
        assertEquals(search, evidence.searchEnvelope)
        assertEquals(observedCrop, evidence.cropObservedCard)
    }

    private fun box(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) = ChromeVisualShieldRasterBox(left, top, right, bottom)
}
