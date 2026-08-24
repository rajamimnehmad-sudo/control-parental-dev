package com.glosh.remote.spike.session;

import java.util.concurrent.atomic.AtomicBoolean;

public final class PairingSubmissionGuard {
    private final AtomicBoolean active = new AtomicBoolean(false);

    public boolean tryStart() {
        return active.compareAndSet(false, true);
    }

    public void finish() {
        active.set(false);
    }

    public boolean isActive() {
        return active.get();
    }
}
