package com.glosh.remote.spike.guide.scroll;

public final class RevealScrollController {
    public enum Action {
        SHOW_ON_SCREEN,
        SCROLL_DOWN,
        SCROLL_FORWARD,
        STOP
    }

    public enum ScrollOrigin {
        GLOSH,
        HUMAN
    }

    public static final int MAX_ACTIONS = 3;
    private static final long PROGRAMMATIC_EVENT_WINDOW_MS = 900;

    private final HumanScrollCooldown cooldown = new HumanScrollCooldown();
    private boolean armed;
    private boolean showOnScreenTried;
    private int actions;
    private long programmaticEventDeadlineMs;
    private String lastFingerprint = "";
    private int noProgress;

    public boolean arm(long nowMs) {
        if (cooldown.isBlocked(nowMs)) {
            return false;
        }
        armed = true;
        showOnScreenTried = false;
        actions = 0;
        programmaticEventDeadlineMs = 0;
        lastFingerprint = "";
        noProgress = 0;
        return true;
    }

    public Action next(
            long nowMs,
            boolean targetKnown,
            boolean targetVisible,
            boolean supportsShow,
            boolean supportsDown,
            boolean supportsForward,
            String fingerprint) {
        if (!armed || cooldown.isBlocked(nowMs) || targetVisible || actions >= MAX_ACTIONS) {
            return Action.STOP;
        }
        String currentFingerprint = fingerprint == null ? "" : fingerprint;
        if (currentFingerprint.equals(lastFingerprint) && !currentFingerprint.isEmpty()) {
            noProgress++;
        } else {
            noProgress = 0;
            lastFingerprint = currentFingerprint;
        }
        if (noProgress >= 2) {
            cancel();
            return Action.STOP;
        }
        if (targetKnown && supportsShow && !showOnScreenTried) {
            showOnScreenTried = true;
            return Action.SHOW_ON_SCREEN;
        }
        if (supportsDown) {
            return Action.SCROLL_DOWN;
        }
        if (supportsForward) {
            return Action.SCROLL_FORWARD;
        }
        cancel();
        return Action.STOP;
    }

    public void performed(long nowMs) {
        actions++;
        programmaticEventDeadlineMs = nowMs + PROGRAMMATIC_EVENT_WINDOW_MS;
    }

    public ScrollOrigin onScrolled(long nowMs) {
        if (armed && nowMs <= programmaticEventDeadlineMs) {
            programmaticEventDeadlineMs = 0;
            return ScrollOrigin.GLOSH;
        }
        cancel();
        cooldown.start(nowMs);
        return ScrollOrigin.HUMAN;
    }

    public void cancel() {
        armed = false;
        programmaticEventDeadlineMs = 0;
    }

    public boolean isArmed() {
        return armed;
    }

    public int actions() {
        return actions;
    }

    public boolean movementAllowed(long nowMs) {
        return armed && !cooldown.isBlocked(nowMs);
    }

    public long cooldownRemainingMs(long nowMs) {
        return cooldown.remainingMs(nowMs);
    }

    public void reset() {
        cancel();
        cooldown.clear();
        actions = 0;
        showOnScreenTried = false;
        lastFingerprint = "";
        noProgress = 0;
    }
}
