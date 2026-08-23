package com.contentfilter.feature.accessibility.chromevisual

import android.view.accessibility.AccessibilityEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromePhotosPresentationIndependencePolicyTest {
    @Test
    fun `healthy data plane keeps transparency and capture independence on same context scroll`() {
        val action = decide(AccessibilityEvent.TYPE_VIEW_SCROLLED, verified = true)

        assertEquals(ChromePhotosPresentationAction.PreserveVerifiedDataPlane, action)
        assertFalse(
            ChromePhotosPresentationIndependencePolicy.captureRequiredAfterOpaqueCommit(
                verifiedDataPlanePresentation = true,
            ),
        )
    }

    @Test
    fun `healthy data plane keeps transparency for ordinary same context content`() {
        val action =
            decide(
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                verified = true,
                contentChangeTypes = AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE,
            )

        assertEquals(ChromePhotosPresentationAction.PreserveVerifiedDataPlane, action)
    }

    @Test
    fun `unhealthy attestation fails closed and preserves legacy capture path`() {
        val scroll = decide(AccessibilityEvent.TYPE_VIEW_SCROLLED, verified = false)
        val content =
            decide(
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                verified = false,
                contentChangeTypes = AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE,
            )

        assertEquals(ChromePhotosPresentationAction.FailClosedAndRearm, scroll)
        assertEquals(ChromePhotosPresentationAction.FailClosedAndRearm, content)
        assertTrue(
            ChromePhotosPresentationIndependencePolicy.captureRequiredAfterOpaqueCommit(
                verifiedDataPlanePresentation = false,
            ),
        )
    }

    @Test
    fun `window viewport and rotation context changes always fail closed`() {
        val action =
            decide(
                eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
                verified = true,
                contextChanged = true,
            )

        assertEquals(ChromePhotosPresentationAction.FailClosedAndRearm, action)
        assertFalse(
            ChromePhotosPresentationIndependencePolicy.captureRequiredAfterOpaqueCommit(
                verifiedDataPlanePresentation = true,
            ),
        )
    }

    @Test
    fun `window lifecycle events are never treated as ordinary same context activity`() {
        assertEquals(
            ChromePhotosPresentationAction.FailClosedAndRearm,
            decide(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, verified = true),
        )
        assertEquals(
            ChromePhotosPresentationAction.FailClosedAndRearm,
            decide(AccessibilityEvent.TYPE_WINDOWS_CHANGED, verified = true),
        )
    }

    @Test
    fun `unrelated or non visual events remain ignored`() {
        assertEquals(
            ChromePhotosPresentationAction.Ignore,
            decide(AccessibilityEvent.TYPE_VIEW_CLICKED, verified = true),
        )
        assertEquals(
            ChromePhotosPresentationAction.Ignore,
            decide(AccessibilityEvent.TYPE_VIEW_SCROLLED, verified = true, chromeEvent = false),
        )
    }

    @Test
    fun `capture metrics expose requests failures error code three and ready baseline`() {
        val metrics = ChromePhotosCaptureMetrics()
        metrics.onRequest()
        metrics.onSuccess()
        metrics.markPresentationReady()
        assertEquals(0L, metrics.snapshot().captureRequestsSincePresentationReady)

        metrics.onRequest()
        val failed = metrics.onFailure(errorCode = 3)

        assertEquals(2L, failed.captureRequests)
        assertEquals(1L, failed.captureSuccess)
        assertEquals(1L, failed.captureFailures)
        assertEquals(1L, failed.errorCode3)
        assertEquals(1L, failed.captureRequestsSincePresentationReady)
    }

    private fun decide(
        eventType: Int,
        verified: Boolean,
        contextChanged: Boolean = false,
        chromeEvent: Boolean = true,
        contentChangeTypes: Int = AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED,
    ) = ChromePhotosPresentationIndependencePolicy.decide(
        contextChanged = contextChanged,
        chromeEvent = chromeEvent,
        eventType = eventType,
        contentChangeTypes = contentChangeTypes,
        verifiedDataPlanePresentation = verified,
    )
}
