package com.contentfilter.dagbrowser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DagPartialRedactionTest {
    private val topCrop = DagImageCropPlan(left = 0, top = 0, width = 300, height = 420)
    private val bottomCrop = DagImageCropPlan(left = 0, top = 580, width = 300, height = 420)

    @Test
    fun `one moderate regional signal selects one frosted crop`() {
        assertEquals(
            listOf(topCrop),
            DagPartialRedactionPolicy.select(
                fullProbability = 0.45f,
                regionalRisks = listOf(DagRegionalRisk(topCrop, 0.61f)),
            ),
        )
    }

    @Test
    fun `full image risk blocks instead of redacting`() {
        assertNull(
            DagPartialRedactionPolicy.select(
                fullProbability = 0.72f,
                regionalRisks = listOf(DagRegionalRisk(topCrop, 0.61f)),
            ),
        )
    }

    @Test
    fun `multiple or very strong regions block instead of redacting`() {
        assertNull(
            DagPartialRedactionPolicy.select(
                fullProbability = 0.45f,
                regionalRisks =
                    listOf(
                        DagRegionalRisk(topCrop, 0.61f),
                        DagRegionalRisk(bottomCrop, 0.62f),
                    ),
            ),
        )
        assertNull(
            DagPartialRedactionPolicy.select(
                fullProbability = 0.45f,
                regionalRisks = listOf(DagRegionalRisk(topCrop, 0.85f)),
            ),
        )
    }
}
