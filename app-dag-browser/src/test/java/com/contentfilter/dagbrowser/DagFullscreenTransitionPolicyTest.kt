package com.contentfilter.dagbrowser

import kotlin.test.Test
import kotlin.test.assertEquals

class DagFullscreenTransitionPolicyTest {
    @Test
    fun `protected entry and exit cover then rearm exactly once`() {
        assertEquals(
            DagFullscreenTransitionPolicy.Action.CoverAndRearm,
            DagFullscreenTransitionPolicy.decide(false, true, protectedVideoActive = true),
        )
        assertEquals(
            DagFullscreenTransitionPolicy.Action.CoverAndRearm,
            DagFullscreenTransitionPolicy.decide(true, false, protectedVideoActive = true),
        )
        assertEquals("fullscreen_transition", DagFullscreenTransitionPolicy.rearmReason(true))
        assertEquals("fullscreen_exit_transition", DagFullscreenTransitionPolicy.rearmReason(false))
    }

    @Test
    fun `duplicate callbacks are ignored and ordinary pages only update chrome`() {
        assertEquals(
            DagFullscreenTransitionPolicy.Action.Ignore,
            DagFullscreenTransitionPolicy.decide(true, true, protectedVideoActive = true),
        )
        assertEquals(
            DagFullscreenTransitionPolicy.Action.UpdateChrome,
            DagFullscreenTransitionPolicy.decide(false, true, protectedVideoActive = false),
        )
    }
}
