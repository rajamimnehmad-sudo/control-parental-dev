package com.contentfilter.feature.accessibility.chromevisual

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChromePhotosProtectedSurfaceStateTest {
    private val viewport = ChromeVisualViewport(0, 0, 1_080, 2_408)

    @Test
    fun `epoch remains monotonic across disarm and rearm`() {
        val state = ChromePhotosProtectedSurfaceState()
        val first = state.arm(windowId = 7, viewport = viewport)
        val disarmed = state.disarm()
        val second = state.arm(windowId = 7, viewport = viewport)

        assertTrue(first.epoch > 0L)
        assertTrue(disarmed.epoch > first.epoch)
        assertTrue(second.epoch > disarmed.epoch)
        assertEquals(ChromePhotosProtectedSurfacePhase.Covered, second.phase)
    }

    @Test
    fun `stale capture cannot commit after scroll invalidation`() {
        val state = ChromePhotosProtectedSurfaceState()
        val armed = state.arm(windowId = 7, viewport = viewport)
        val token = assertNotNull(state.beginCapture(armed.epoch))

        val motion = state.invalidate(windowId = 7, viewport = viewport, motion = true)

        assertEquals(ChromePhotosProtectedSurfacePhase.Motion, motion.phase)
        assertFalse(state.markCommitReady(token))
        assertFalse(state.markPresented(token))
    }

    @Test
    fun `latest capture can stage one complete generation`() {
        val state = ChromePhotosProtectedSurfaceState()
        val armed = state.arm(windowId = 7, viewport = viewport)

        assertTrue(state.markSettling(armed.epoch))
        val token = assertNotNull(state.beginCapture(armed.epoch))
        assertTrue(state.markCommitReady(token))
        assertTrue(state.markPresented(token))

        val snapshot = state.snapshot()
        assertEquals(ChromePhotosProtectedSurfacePhase.Presented, snapshot.phase)
        assertEquals(token.sequence, snapshot.presentedSequence)
    }

    @Test
    fun `window or geometry change invalidates prior authority`() {
        val state = ChromePhotosProtectedSurfaceState()
        val armed = state.arm(windowId = 7, viewport = viewport)
        val token = assertNotNull(state.beginCapture(armed.epoch))

        val changed = state.invalidate(windowId = 8, viewport = viewport.copy(bottom = 2_000), motion = false)

        assertTrue(changed.epoch > token.epoch)
        assertFalse(state.fail(token))
        assertEquals(8, changed.windowId)
    }
}
