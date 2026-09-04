package com.contentfilter.user.chromedataplane

import com.glosh.visual.GloshiaImageContract
import com.glosh.visual.GloshiaPreparedImage
import kotlin.test.Test
import kotlin.test.assertEquals

class ChromeGifTemporalSelectorTest {
    @Test
    fun `stable frames are sampled at two frames per second`() {
        val selector = ChromeGifTemporalSelector()
        val decisions =
            (0 until 12).map { index ->
                selector.select(index, 50 + index * 50, image(20))
            }

        assertEquals(
            listOf(
                ChromeGifTemporalDecision.Analyze,
                ChromeGifTemporalDecision.Skip,
                ChromeGifTemporalDecision.Skip,
                ChromeGifTemporalDecision.Skip,
                ChromeGifTemporalDecision.Skip,
                ChromeGifTemporalDecision.Skip,
                ChromeGifTemporalDecision.Skip,
                ChromeGifTemporalDecision.Skip,
                ChromeGifTemporalDecision.Skip,
                ChromeGifTemporalDecision.Skip,
                ChromeGifTemporalDecision.Analyze,
                ChromeGifTemporalDecision.Skip,
            ),
            decisions,
        )
    }

    @Test
    fun `material change is analyzed immediately and out of order rejects`() {
        val selector = ChromeGifTemporalSelector()
        assertEquals(ChromeGifTemporalDecision.Analyze, selector.select(0, 50, image(10)))
        assertEquals(ChromeGifTemporalDecision.Analyze, selector.select(1, 100, image(220)))
        assertEquals(ChromeGifTemporalDecision.Reject, selector.select(3, 150, image(220)))
    }

    private fun image(value: Int) =
        GloshiaPreparedImage(
            width = GloshiaImageContract.TargetSize,
            height = GloshiaImageContract.TargetSize,
            rgb888 = ByteArray(GloshiaImageContract.PreparedByteCount) { value.toByte() },
        )
}
