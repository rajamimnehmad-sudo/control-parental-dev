package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ChromeMediaShieldActiveDocumentReplayContractTest {
    @Test
    fun `replay command is fixed dev dump surface and carries no capability extras`() {
        assertEquals(
            ChromePhotosDataPlaneLabContract.ActionActiveDocumentReplay,
            ChromePhotosDataPlaneLabReceiver.ActionActiveDocumentReplay,
        )
        assertEquals(
            "com.contentfilter.user.chromedataplane.command.ACTIVE_DOCUMENT_REPLAY",
            ChromePhotosDataPlaneLabReceiver.ActionActiveDocumentReplay,
        )
        assertFalse(
            ChromePhotosDataPlaneLabReceiver.ActionActiveDocumentReplay.contains("token", ignoreCase = true),
        )
        assertFalse(
            ChromePhotosDataPlaneLabReceiver.ActionActiveDocumentReplay.contains("challenge", ignoreCase = true),
        )
    }
}
