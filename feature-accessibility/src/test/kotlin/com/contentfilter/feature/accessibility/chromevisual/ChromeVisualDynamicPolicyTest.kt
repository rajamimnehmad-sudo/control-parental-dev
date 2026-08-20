package com.contentfilter.feature.accessibility.chromevisual

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromeVisualDynamicPolicyTest {
    @Test
    fun `stable pages back off without exceeding one second`() {
        assertTrue(ChromeVisualVerificationSchedule.delayMillis(0) == 500L)
        assertTrue(ChromeVisualVerificationSchedule.delayMillis(1) == 500L)
        assertTrue(ChromeVisualVerificationSchedule.delayMillis(2) == 1_000L)
        assertTrue(ChromeVisualVerificationSchedule.delayMillis(20) == 1_000L)
    }

    @Test
    fun `blocked visual area survives geometry churn until page identity changes`() {
        val ledger = ChromeVisualPageBlockLedger()
        val tiles =
            listOf(
                ChromeVisualRegion("tile_0", 0, 200, 500, 700),
                ChromeVisualRegion("tile_1", 500, 200, 1_000, 700),
            )

        assertTrue(ledger.beginPage(10L))
        ledger.recordBlocked(10L, ChromeVisualRegion("image", 400, 300, 600, 500), tiles)
        assertTrue(ledger.mustRemainBlocked(10L, "tile_0"))
        assertTrue(ledger.mustRemainBlocked(10L, "tile_1"))

        assertFalse(ledger.beginPage(10L))
        assertTrue(ledger.mustRemainBlocked(10L, "tile_0"))
        assertTrue(ledger.beginPage(11L))
        assertFalse(ledger.mustRemainBlocked(11L, "tile_0"))
    }
}
