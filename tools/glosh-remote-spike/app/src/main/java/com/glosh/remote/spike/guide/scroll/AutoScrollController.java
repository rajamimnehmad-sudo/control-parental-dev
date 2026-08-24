package com.glosh.remote.spike.guide.scroll;

public final class AutoScrollController {
    public enum Action {
        SHOW_ON_SCREEN,
        SCROLL_DOWN,
        SCROLL_FORWARD,
        STOP
    }

    public static final int MAX_ATTEMPTS = 6;
    private final ScrollProgressDetector progress = new ScrollProgressDetector();
    private int attempts;

    public Action next(
            boolean targetKnown,
            boolean targetVisible,
            boolean showOnScreenSupported,
            boolean scrollDownSupported,
            boolean scrollForwardSupported,
            String fingerprint,
            boolean contextChanged,
            boolean serviceEnabled) {
        if (!serviceEnabled || contextChanged || targetVisible || attempts >= MAX_ATTEMPTS) {
            return Action.STOP;
        }
        if (!progress.record(fingerprint)) {
            return Action.STOP;
        }
        attempts++;
        if (targetKnown && showOnScreenSupported) {
            return Action.SHOW_ON_SCREEN;
        }
        if (scrollDownSupported) {
            return Action.SCROLL_DOWN;
        }
        if (scrollForwardSupported) {
            return Action.SCROLL_FORWARD;
        }
        return Action.STOP;
    }

    public void reset() {
        attempts = 0;
        progress.reset();
    }

    public int attempts() {
        return attempts;
    }
}
