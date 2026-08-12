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
        val rect = rect()

        assertTrue(machine.requestCover(key, rect))
        assertEquals(DagVideoLabState.Covering, machine.currentState)
        assertTrue(machine.markCovered(key))
        assertTrue(machine.requestCapture(key, rect))
        assertFalse(machine.requestCapture(key, rect))
        assertTrue(machine.completeCapture(key, captured = true))
        assertEquals(DagVideoLabState.Covered, machine.currentState)
    }

    @Test
    fun `new source revision makes every old callback stale`() {
        val machine = DagVideoLabStateMachine()
        val old = key(revision = 7)
        val current = key(revision = 8)

        assertTrue(machine.requestCover(old, rect()))
        assertTrue(machine.requestCover(current, rect()))
        assertFalse(machine.markCovered(old))
        assertTrue(machine.markCovered(current))
        assertFalse(machine.retire(old))
        assertEquals(current, machine.currentKey)
    }

    @Test
    fun `a new authority cannot overlap an active pixel capture`() {
        val machine = DagVideoLabStateMachine()
        val first = key(revision = 1)
        val second = key(revision = 2)

        assertTrue(machine.requestCover(first, rect()))
        assertTrue(machine.markCovered(first))
        assertTrue(machine.requestCapture(first, rect()))
        assertFalse(machine.requestCover(second, rect()))
        assertEquals(first, machine.currentKey)
        assertTrue(machine.completeCapture(first, captured = true))
        assertTrue(machine.requestCover(second, rect()))
    }

    @Test
    fun `failure remains closed until the exact revision retires`() {
        val machine = DagVideoLabStateMachine()
        val key = key()

        assertTrue(machine.requestCover(key, rect()))
        assertTrue(machine.markCovered(key))
        assertTrue(machine.requestCapture(key, rect()))
        assertTrue(machine.completeCapture(key, captured = false))
        assertEquals(DagVideoLabState.Failed, machine.currentState)
        assertFalse(machine.requestCapture(key, rect()))
        assertTrue(machine.retire(key))
        assertNull(machine.currentKey)
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
