package com.glosh.remote.spike.broker;

/** Bounds automatic renewal of short-lived broker requests. */
final class BrokerWaitPolicy {
    static final int MAX_REQUEST_ATTEMPTS = 5;

    private int startedRequests;

    boolean startNextRequest() {
        if (startedRequests >= MAX_REQUEST_ATTEMPTS) {
            return false;
        }
        startedRequests++;
        return true;
    }

    boolean shouldRenew(String state) {
        return "expired".equals(state) && startedRequests < MAX_REQUEST_ATTEMPTS;
    }

    void reset() {
        startedRequests = 0;
    }
}
