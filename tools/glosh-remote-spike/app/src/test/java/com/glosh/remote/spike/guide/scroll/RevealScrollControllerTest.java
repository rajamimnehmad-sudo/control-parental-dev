package com.glosh.remote.spike.guide.scroll;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RevealScrollControllerTest {
    @Test
    public void noShowMeMeansZeroMovement() {
        RevealScrollController controller = new RevealScrollController();
        assertEquals(RevealScrollController.Action.STOP,
                controller.next(100, false, false, false, true, true, "a"));
        assertEquals(0, controller.actions());
    }

    @Test
    public void showMeArmsRevealAndShowOnScreenComesFirst() {
        RevealScrollController controller = new RevealScrollController();
        assertTrue(controller.arm(100));
        assertEquals(RevealScrollController.Action.SHOW_ON_SCREEN,
                controller.next(100, true, false, true, true, true, "a"));
    }

    @Test
    public void revealAllowsAtMostThreeActions() {
        RevealScrollController controller = new RevealScrollController();
        controller.arm(100);
        for (int index = 0; index < RevealScrollController.MAX_ACTIONS; index++) {
            assertEquals(RevealScrollController.Action.SCROLL_DOWN,
                    controller.next(100 + index, false, false, false, true, true, "page-" + index));
            controller.performed(100 + index);
        }
        assertEquals(RevealScrollController.Action.STOP,
                controller.next(200, false, false, false, true, true, "last"));
    }

    @Test
    public void humanScrollCancelsAndStartsCooldown() {
        RevealScrollController controller = new RevealScrollController();
        controller.arm(100);
        assertEquals(RevealScrollController.ScrollOrigin.HUMAN, controller.onScrolled(101));
        assertFalse(controller.isArmed());
        assertFalse(controller.arm(101 + HumanScrollCooldown.MIN_COOLDOWN_MS - 1));
        assertTrue(controller.arm(101 + HumanScrollCooldown.MIN_COOLDOWN_MS));
    }

    @Test
    public void expectedScrollEventIsAttributedToGlosh() {
        RevealScrollController controller = new RevealScrollController();
        controller.arm(100);
        controller.performed(100);
        assertEquals(RevealScrollController.ScrollOrigin.GLOSH, controller.onScrolled(200));
        assertTrue(controller.isArmed());
    }
}
