package com.glosh.remote.spike.relay;

/** Bounded exponential-ish reconnect schedule for transient relay cuts. */
public final class RelayReconnectPolicy {
    private static final long[] DELAYS_MS = {1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L};

    private RelayReconnectPolicy() {
    }

    public static long delayMillis(int zeroBasedAttempt) {
        if (zeroBasedAttempt < 0 || zeroBasedAttempt >= DELAYS_MS.length) {
            return -1L;
        }
        return DELAYS_MS[zeroBasedAttempt];
    }
}
