package com.contentfilter.dagbrowser

import kotlin.test.Test
import kotlin.test.assertEquals

class DagTemporalFrameSelectorTest {
    @Test
    fun `stable animation uses the heavy model at two frames per second`() {
        val selector = DagTemporalFrameSelector()
        val image = image(20)
        val decisions =
            (0 until 12).map { index ->
                selector.select(index, 50 + index * 50, image)
            }

        assertEquals(
            listOf(
                DagTemporalFrameDecision.Analyze,
                DagTemporalFrameDecision.Skip,
                DagTemporalFrameDecision.Skip,
                DagTemporalFrameDecision.Skip,
                DagTemporalFrameDecision.Skip,
                DagTemporalFrameDecision.Skip,
                DagTemporalFrameDecision.Skip,
                DagTemporalFrameDecision.Skip,
                DagTemporalFrameDecision.Skip,
                DagTemporalFrameDecision.Skip,
                DagTemporalFrameDecision.Analyze,
                DagTemporalFrameDecision.Skip,
            ),
            decisions,
        )
    }

    @Test
    fun `brief material scene change triggers immediate analysis`() {
        val selector = DagTemporalFrameSelector()

        assertEquals(DagTemporalFrameDecision.Analyze, selector.select(0, 50, image(10)))
        assertEquals(DagTemporalFrameDecision.Skip, selector.select(1, 100, image(10)))
        assertEquals(DagTemporalFrameDecision.Analyze, selector.select(2, 150, image(220)))
    }

    @Test
    fun `small concentrated high contrast change triggers immediate analysis`() {
        val selector = DagTemporalFrameSelector()
        val baseline = image(10)
        val changed =
            image(10).also { image ->
                for (y in 0 until 28) {
                    for (x in 0 until 28) {
                        val offset = (y * image.width + x) * 3
                        image.rgb888[offset] = 240.toByte()
                        image.rgb888[offset + 1] = 240.toByte()
                        image.rgb888[offset + 2] = 240.toByte()
                    }
                }
            }

        assertEquals(DagTemporalFrameDecision.Analyze, selector.select(0, 50, baseline))
        assertEquals(DagTemporalFrameDecision.Analyze, selector.select(1, 100, changed))
    }

    @Test
    fun `out of order frame fails closed`() {
        val selector = DagTemporalFrameSelector()
        val image = image(10)

        assertEquals(DagTemporalFrameDecision.Analyze, selector.select(0, 50, image))
        assertEquals(DagTemporalFrameDecision.Reject, selector.select(2, 100, image))
    }

    private fun image(value: Int) =
        DagPreparedImage(
            width = DagImageDecodeContract.TargetSize,
            height = DagImageDecodeContract.TargetSize,
            rgb888 = ByteArray(DagImageDecodeContract.PreparedByteCount) { value.toByte() },
        )
}
