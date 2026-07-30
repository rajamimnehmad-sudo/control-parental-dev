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
    fun `ordinary aspect ratios keep the single full image analysis`() {
        assertTrue(DagRegionalCropPlanner.plan(800, 600).isEmpty())
        assertTrue(DagRegionalCropPlanner.plan(600, 800).isEmpty())
        assertNull(DagRegionalCropPlanner.decodeSize(800, 600))
    }

    @Test
    fun `wide images receive three overlapping regional views`() {
        assertEquals(
            listOf(
                DagImageCropPlan(left = 0, top = 0, width = 420, height = 300),
                DagImageCropPlan(left = 290, top = 0, width = 420, height = 300),
                DagImageCropPlan(left = 580, top = 0, width = 420, height = 300),
            ),
            DagRegionalCropPlanner.plan(1_000, 300),
        )
        assertEquals(Pair(672, 202), DagRegionalCropPlanner.decodeSize(1_000, 300))
    }

    @Test
    fun `tall images receive top middle and bottom regional views`() {
        assertEquals(
            listOf(
                DagImageCropPlan(left = 0, top = 0, width = 300, height = 420),
                DagImageCropPlan(left = 0, top = 290, width = 300, height = 420),
                DagImageCropPlan(left = 0, top = 580, width = 300, height = 420),
            ),
            DagRegionalCropPlanner.plan(300, 1_000),
        )
        assertEquals(Pair(202, 672), DagRegionalCropPlanner.decodeSize(300, 1_000))
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
