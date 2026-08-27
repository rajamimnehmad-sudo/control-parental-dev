package com.glosh.remote.spike.adb;

import java.util.concurrent.atomic.AtomicBoolean;

/** Emits one loss edge and one matching restored edge even if another path reconnects first. */
public final class ConnectionRecoveryLatch {
    private final AtomicBoolean lost = new AtomicBoolean(false);

    public boolean markLost() {
        return lost.compareAndSet(false, true);
    }

    public boolean markHealthy() {
        return lost.compareAndSet(true, false);
    }
}
