package com.contentfilter.dagbrowser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DagVideoLabContractTest {
    @Test
    fun `exact revision advances through one bounded capture`() {
        val machine = DagVideoLabStateMachine()
        val key = key(revision = 4)
        val frame = frame(key, viewportEpoch = 3, frameSequence = 9)
        val rect = rect()

        assertTrue(machine.requestCover(key, rect))
        assertEquals(DagVideoLabState.Covering, machine.currentState)
        assertTrue(machine.markCovered(key))
        assertTrue(machine.requestCapture(frame, rect))
        assertFalse(machine.requestCapture(frame, rect))
        assertTrue(machine.completeCapture(frame, captured = true))
        assertEquals(DagVideoLabState.Covered, machine.currentState)
        assertEquals(frame, machine.currentFrameKey)
    }

    @Test
    fun `new source revision cannot overwrite a covered authority`() {
        val machine = DagVideoLabStateMachine()
        val old = key(revision = 7)
        val current = key(revision = 8)

        assertTrue(machine.requestCover(old, rect()))
        assertTrue(machine.markCovered(old))
        assertFalse(machine.requestCover(current, rect()))
        assertTrue(machine.beginClosing(old, closeNonce()))
        assertTrue(machine.acknowledgeClose(old, closeNonce()))
        assertTrue(machine.requestCover(current, rect()))
        assertTrue(machine.markCovered(current))
        assertEquals(current, machine.currentKey)
    }

    @Test
    fun `a new authority cannot overlap an active pixel capture`() {
        val machine = DagVideoLabStateMachine()
        val first = key(revision = 1)
        val second = key(revision = 2)

        assertTrue(machine.requestCover(first, rect()))
        assertTrue(machine.markCovered(first))
        assertTrue(machine.requestCapture(frame(first), rect()))
        assertFalse(machine.requestCover(second, rect()))
        assertEquals(first, machine.currentKey)
        assertTrue(machine.completeCapture(frame(first), captured = true))
        assertTrue(machine.beginClosing(first, closeNonce()))
        assertTrue(machine.acknowledgeClose(first, closeNonce()))
        assertTrue(machine.requestCover(second, rect()))
    }

    @Test
    fun `failure remains closed until the exact revision retires`() {
        val machine = DagVideoLabStateMachine()
        val key = key()

        assertTrue(machine.requestCover(key, rect()))
        assertTrue(machine.markCovered(key))
        val frame = frame(key)
        assertTrue(machine.requestCapture(frame, rect()))
        assertTrue(machine.completeCapture(frame, captured = false))
        assertEquals(DagVideoLabState.Failed, machine.currentState)
        assertFalse(machine.requestCapture(frame(key, frameSequence = 2), rect()))
        assertTrue(machine.beginClosing(key, closeNonce()))
        assertTrue(machine.acknowledgeClose(key, closeNonce()))
        assertNull(machine.currentKey)
    }

    @Test
    fun `frame identity rejects a stale viewport or repeated sequence`() {
        val machine = DagVideoLabStateMachine()
        val key = key()
        val first = frame(key, viewportEpoch = 1, frameSequence = 4)
        val stale = frame(key, viewportEpoch = 1, frameSequence = 3)
        val current = frame(key, viewportEpoch = 2, frameSequence = 5)

        assertTrue(machine.requestCover(key, rect()))
        assertTrue(machine.markCovered(key))
        assertTrue(machine.requestCapture(first, rect()))
        assertFalse(machine.completeCapture(current, captured = true))
        assertTrue(machine.completeCapture(first, captured = true))
        assertFalse(machine.requestCapture(stale, rect()))
        assertTrue(machine.requestCapture(current, rect()))
        assertTrue(machine.isCurrent(current, DagVideoLabState.Capturing))
    }

    @Test
    fun `close requires the exact acknowledgement and blocks on timeout`() {
        val machine = DagVideoLabStateMachine()
        val key = key()
        val nonce = closeNonce()

        assertTrue(machine.requestCover(key, rect()))
        assertTrue(machine.markCovered(key))
        assertTrue(machine.beginClosing(key, nonce))
        assertEquals(DagVideoLabState.Closing, machine.currentState)
        assertFalse(machine.acknowledgeClose(key, closeNonce("b")))
        assertTrue(machine.blockClosing(key, nonce))
        assertEquals(DagVideoLabState.Blocked, machine.currentState)
        assertFalse(machine.requestCover(key(revision = 2), rect()))
        assertFalse(machine.acknowledgeClose(key, nonce))
        assertEquals(key, machine.currentKey)
    }

    @Test
    fun `capture plan keeps source aspect while bounding its long edge`() {
        assertEquals(
            DagVideoLabCapturePlan(targetWidth = 512, targetHeight = 288),
            DagVideoLabCapturePlan.fromDimensions(1_920, 1_080),
        )
        assertEquals(
            DagVideoLabCapturePlan(targetWidth = 288, targetHeight = 512),
            DagVideoLabCapturePlan.fromDimensions(1_080, 1_920),
        )
        assertEquals(
            DagVideoLabCapturePlan(targetWidth = 320, targetHeight = 180),
            DagVideoLabCapturePlan.fromDimensions(320, 180),
        )
        assertNull(DagVideoLabCapturePlan.fromDimensions(0, 180))
        assertNull(DagVideoLabCapturePlan.fromDimensions(320, 180, maxLongEdge = 0))
        assertNull(DagVideoLabCapturePlan.fromDimensions(320, 180, maxLongEdge = 513))
    }

    @Test
    fun `invalid identity and offscreen or unbounded rectangles never arm`() {
        val machine = DagVideoLabStateMachine()

        assertFalse(machine.requestCover(key(videoId = "video_bad"), rect()))
        assertFalse(machine.requestCover(key(), rect(left = 500f, width = 40f, viewportWidth = 360f)))
        assertFalse(machine.requestCover(key(), rect(width = 20_000f)))
        assertNull(machine.currentKey)
    }

    @Test
    fun `fixture probe accepts only the four expected decoded quadrants`() {
        assertTrue(
            DagVideoLabFixtureProbe.matches(
                topLeft = 0xffef2020.toInt(),
                topRight = 0xff20cf40.toInt(),
                bottomLeft = 0xff204fef.toInt(),
                bottomRight = 0xfff5f5f5.toInt(),
            ),
        )
        assertFalse(
            DagVideoLabFixtureProbe.matches(
                topLeft = 0xffef2020.toInt(),
                topRight = 0xff202020.toInt(),
                bottomLeft = 0xff204fef.toInt(),
                bottomRight = 0xfff5f5f5.toInt(),
            ),
        )
        assertTrue(
            DagVideoLabFixtureProbe.matches(
                topLeft = 0xff20cf40.toInt(),
                topRight = 0xff20cf40.toInt(),
                bottomLeft = 0xfff5f5f5.toInt(),
                bottomRight = 0xfff5f5f5.toInt(),
                expectedTopLeft = DagVideoLabFixtureColor.Green,
                expectedTopRight = DagVideoLabFixtureColor.Green,
                expectedBottomLeft = DagVideoLabFixtureColor.LightNeutral,
                expectedBottomRight = DagVideoLabFixtureColor.LightNeutral,
            ),
        )
    }

    private fun key(
        revision: Int = 1,
        videoId: String = "video_0123456789abcdef",
    ) = DagVideoLabKey(
        tabId = 3,
        documentToken = "document_a1b2",
        videoId = videoId,
        revision = revision,
    )

    private fun frame(
        key: DagVideoLabKey,
        viewportEpoch: Int = 1,
        frameSequence: Int = 1,
    ) = DagVideoLabFrameKey(
        videoKey = key,
        viewportEpoch = viewportEpoch,
        frameSequence = frameSequence,
    )

    private fun closeNonce(fill: String = "a"): String = fill.repeat(32)

    private fun rect(
        left: Float = 10f,
        top: Float = 20f,
        width: Float = 320f,
        height: Float = 180f,
        viewportWidth: Float = 360f,
        viewportHeight: Float = 640f,
    ) = DagVideoLabClientRect(
        left = left,
        top = top,
        width = width,
        height = height,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
    )
}
