package com.glosh.remote.spike.broker;

/** Bounds automatic renewal/retry of short-lived broker requests by elapsed time. */
final class BrokerWaitPolicy {
    static final long MAX_WAIT_MILLIS = 30L * 60L * 1_000L;
    static final int MAX_CONSECUTIVE_FAILURES = 6;
    static final long MAX_RETRY_DELAY_MILLIS = 5_000L;

    interface Clock {
        long nowMillis();
    }

    private final Clock clock;
    private long startedAtMillis = -1L;
    private int consecutiveFailures;

    BrokerWaitPolicy() {
        this(() -> System.nanoTime() / 1_000_000L);
    }

    BrokerWaitPolicy(Clock clock) {
        this.clock = clock;
    }

    boolean startNextRequest() {
        long now = clock.nowMillis();
        if (startedAtMillis < 0L) {
            startedAtMillis = now;
        }
        return now - startedAtMillis <= MAX_WAIT_MILLIS;
    }

    boolean shouldRenew(String state) {
        return "expired".equals(state) && withinWindow();
    }

    long nextRetryDelayMillis() {
        if (!withinWindow() || consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            return -1L;
        }
        long delay = 500L << consecutiveFailures;
        consecutiveFailures++;
        return Math.min(delay, MAX_RETRY_DELAY_MILLIS);
    }

    void recordSuccess() {
        consecutiveFailures = 0;
    }

    boolean withinWindow() {
        return startedAtMillis >= 0L
                && clock.nowMillis() - startedAtMillis <= MAX_WAIT_MILLIS;
    }

    void reset() {
        startedAtMillis = -1L;
        consecutiveFailures = 0;
    }
}
