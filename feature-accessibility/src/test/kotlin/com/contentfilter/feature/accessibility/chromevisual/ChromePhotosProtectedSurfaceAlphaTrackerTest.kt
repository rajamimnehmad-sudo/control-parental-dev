package com.contentfilter.feature.accessibility.chromevisual

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromePhotosProtectedSurfaceAlphaTrackerTest {
    @Test
    fun `transparent submission stays potentially visible until a committed opaque successor`() {
        val tracker = ChromePhotosProtectedSurfaceAlphaTracker()
        val transparent = tracker.begin(ChromePhotosProtectedSurfaceAlpha.Transparent)

        assertTrue(tracker.snapshot().mayBeTransparent)

        val opaque = tracker.begin(ChromePhotosProtectedSurfaceAlpha.Opaque)
        tracker.commit(opaque)
        tracker.commit(transparent)

        assertFalse(tracker.snapshot().mayBeTransparent)
        assertEquals(0, tracker.snapshot().pendingTransitions)
    }

    @Test
    fun `failed opaque restoration never reports a safe opaque surface`() {
        val tracker = ChromePhotosProtectedSurfaceAlphaTracker()
        val transparent = tracker.begin(ChromePhotosProtectedSurfaceAlpha.Transparent)
        tracker.commit(transparent)
        val opaque = tracker.begin(ChromePhotosProtectedSurfaceAlpha.Opaque)

        tracker.submissionFailed(opaque)

        assertTrue(tracker.snapshot().mayBeTransparent)
        assertEquals(1, tracker.snapshot().submitFailures)
    }

    @Test
    fun `failed transparent submission preserves committed opaque state`() {
        val tracker = ChromePhotosProtectedSurfaceAlphaTracker()
        val transparent = tracker.begin(ChromePhotosProtectedSurfaceAlpha.Transparent)

        tracker.submissionFailed(transparent)

        assertFalse(tracker.snapshot().mayBeTransparent)
        assertEquals(1, tracker.snapshot().submitFailures)
    }

    @Test
    fun `historical synchronous path records the submitted alpha deterministically`() {
        val tracker = ChromePhotosProtectedSurfaceAlphaTracker()
        val transparent = tracker.begin(ChromePhotosProtectedSurfaceAlpha.Transparent)
        tracker.submittedWithoutCallback(transparent)

        assertTrue(tracker.snapshot().mayBeTransparent)

        tracker.reset()

        assertFalse(tracker.snapshot().mayBeTransparent)
        assertEquals(0, tracker.snapshot().pendingTransitions)
    }
}
