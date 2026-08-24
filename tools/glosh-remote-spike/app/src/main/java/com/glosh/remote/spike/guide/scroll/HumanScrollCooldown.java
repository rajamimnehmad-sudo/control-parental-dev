package com.glosh.remote.spike.guide.scroll;

public final class HumanScrollCooldown {
    public static final long MIN_COOLDOWN_MS = 1_400;

    private long blockedUntilMs;

    public void start(long nowMs) {
        blockedUntilMs = Math.max(blockedUntilMs, nowMs + MIN_COOLDOWN_MS);
    }

    public boolean isBlocked(long nowMs) {
        return nowMs < blockedUntilMs;
    }

    public long remainingMs(long nowMs) {
        return Math.max(0, blockedUntilMs - nowMs);
    }

    public void clear() {
        blockedUntilMs = 0;
    }
}
