package com.contentfilter.dagbrowser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DagImagePreprocessorTest {
    @Test
    fun `landscape image keeps every pixel and receives vertical padding`() {
        assertEquals(
            DagImageFitPlan(
                contentWidth = 224,
                contentHeight = 112,
                offsetX = 0,
                offsetY = 56,
            ),
            DagImageFitPlanner.plan(sourceWidth = 400, sourceHeight = 200),
        )
    }

    @Test
    fun `portrait image keeps every pixel and receives horizontal padding`() {
        assertEquals(
            DagImageFitPlan(
                contentWidth = 56,
                contentHeight = 224,
                offsetX = 84,
                offsetY = 0,
            ),
            DagImageFitPlanner.plan(sourceWidth = 100, sourceHeight = 400),
        )
    }

    @Test
    fun `square image fills the model input`() {
        assertEquals(
            DagImageFitPlan(
                contentWidth = 224,
                contentHeight = 224,
                offsetX = 0,
                offsetY = 0,
            ),
            DagImageFitPlanner.plan(sourceWidth = 300, sourceHeight = 300),
        )
    }

    @Test
    fun `invalid dimensions do not produce a resize plan`() {
        assertNull(DagImageFitPlanner.plan(sourceWidth = 0, sourceHeight = 100))
        assertNull(DagImageFitPlanner.plan(sourceWidth = 100, sourceHeight = -1))
        assertNull(DagImageFitPlanner.plan(sourceWidth = 100, sourceHeight = 100, targetSize = 0))
    }

    @Test
    fun `decode contract rejects oversized dimensions`() {
        assertTrue(DagImageDecodeContract.hasSafeDimensions(4_096, 4_096))
        assertFalse(DagImageDecodeContract.hasSafeDimensions(4_097, 100))
        assertFalse(DagImageDecodeContract.hasSafeDimensions(4_096, 4_097))
    }

    @Test
    fun `prepared image contract requires exact rgb tensor size`() {
        assertTrue(
            DagImageDecodeContract.isValid(
                DagPreparedImage(
                    width = 224,
                    height = 224,
                    rgb888 = ByteArray(DagImageDecodeContract.PreparedByteCount),
                ),
            ),
        )
        assertFalse(
            DagImageDecodeContract.isValid(
                DagPreparedImage(
                    width = 224,
                    height = 224,
                    rgb888 = ByteArray(DagImageDecodeContract.PreparedByteCount - 1),
                ),
            ),
        )
    }
}
