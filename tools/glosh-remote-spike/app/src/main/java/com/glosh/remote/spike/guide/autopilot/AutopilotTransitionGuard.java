package com.glosh.remote.spike.guide.autopilot;

import java.util.EnumSet;

/** Prevents a second action while Samsung is still applying the previous click. */
public final class AutopilotTransitionGuard {
    public enum Result { ACCEPT, WAIT, REJECT }

    private final long timeoutMs;
    private EnumSet<AutopilotContract.Screen> expected =
            EnumSet.noneOf(AutopilotContract.Screen.class);
    private Boolean expectedWirelessState;
    private long startedAtMs;

    public AutopilotTransitionGuard(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public void expect(
            EnumSet<AutopilotContract.Screen> screens,
            Boolean wirelessState,
            long nowMs) {
        expected = EnumSet.copyOf(screens);
        expectedWirelessState = wirelessState;
        startedAtMs = nowMs;
    }

    public Result evaluate(
            AutopilotContract.Screen screen,
            Boolean wirelessState,
            long nowMs) {
        if (expected.isEmpty()) {
            return Result.ACCEPT;
        }
        boolean matchingScreen = expected.contains(screen);
        boolean matchingPostcondition = expectedWirelessState == null
                || screen != AutopilotContract.Screen.WIRELESS_DEBUGGING
                || expectedWirelessState.equals(wirelessState);
        if (matchingScreen && matchingPostcondition) {
            clear();
            return Result.ACCEPT;
        }
        return nowMs - startedAtMs < timeoutMs ? Result.WAIT : Result.REJECT;
    }

    public void clear() {
        expected.clear();
        expectedWirelessState = null;
    }
}
