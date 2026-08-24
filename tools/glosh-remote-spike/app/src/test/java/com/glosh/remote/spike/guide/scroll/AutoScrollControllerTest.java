package com.glosh.remote.spike.guide.scroll;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AutoScrollControllerTest {
    @Test
    public void visibleTargetStops() {
        assertEquals(AutoScrollController.Action.STOP, next(true, true, true, true, true, "a", false, true));
    }

    @Test
    public void showOnScreenHasPriority() {
        assertEquals(AutoScrollController.Action.SHOW_ON_SCREEN, next(true, false, true, true, true, "a", false, true));
    }

    @Test
    public void scrollDownPrecedesForward() {
        assertEquals(AutoScrollController.Action.SCROLL_DOWN, next(false, false, false, true, true, "a", false, true));
    }

    @Test
    public void forwardIsFallback() {
        assertEquals(AutoScrollController.Action.SCROLL_FORWARD, next(false, false, false, false, true, "a", false, true));
    }

    @Test
    public void twoNoProgressAttemptsStop() {
        AutoScrollController controller = new AutoScrollController();
        controller.next(false, false, false, true, true, "same", false, true);
        controller.next(false, false, false, true, true, "same", false, true);
        assertEquals(AutoScrollController.Action.STOP,
                controller.next(false, false, false, true, true, "same", false, true));
    }

    @Test
    public void maxAttemptsStops() {
        AutoScrollController controller = new AutoScrollController();
        for (int index = 0; index < AutoScrollController.MAX_ATTEMPTS; index++) {
            controller.next(false, false, false, true, true, "page-" + index, false, true);
        }
        assertEquals(AutoScrollController.Action.STOP,
                controller.next(false, false, false, true, true, "last", false, true));
    }

    @Test
    public void contextChangeOrDisabledServiceStops() {
        assertEquals(AutoScrollController.Action.STOP, next(false, false, false, true, true, "a", true, true));
        assertEquals(AutoScrollController.Action.STOP, next(false, false, false, true, true, "a", false, false));
    }

    private AutoScrollController.Action next(
            boolean known, boolean visible, boolean show, boolean down, boolean forward,
            String fingerprint, boolean changed, boolean enabled) {
        return new AutoScrollController().next(
                known, visible, show, down, forward, fingerprint, changed, enabled);
    }
}
