package com.glosh.remote.spike.guide.accessibility;

import android.os.Handler;

public final class GuideEventActor {
    public interface SnapshotSource {
        SettingsSnapshot capture();
    }

    public interface Listener {
        void onGenerationInvalidated(long generation);

        void onStableSnapshot(ScanGenerationGuard.Token token, SettingsSnapshot snapshot);

        void onNoTrustedWindow(long generation);
    }

    public static final long STABILITY_DELAY_MS = 400;
    public static final long SECOND_FINGERPRINT_DELAY_MS = 80;

    private final Handler handler;
    private final SnapshotSource source;
    private final Listener listener;
    private final ScanGenerationGuard guard;
    private final SnapshotStabilityGate stability = new SnapshotStabilityGate();
    private final Runnable scan = this::scanOnce;
    private boolean closed;

    public GuideEventActor(
            Handler handler,
            SnapshotSource source,
            Listener listener,
            ScanGenerationGuard guard) {
        this.handler = handler;
        this.source = source;
        this.listener = listener;
        this.guard = guard;
    }

    public void relevantEvent() {
        handler.post(() -> {
            if (closed) {
                return;
            }
            long generation = guard.invalidate();
            stability.reset();
            handler.removeCallbacks(scan);
            listener.onGenerationInvalidated(generation);
            handler.postDelayed(scan, STABILITY_DELAY_MS);
        });
    }

    public void runSerialized(Runnable action) {
        handler.post(() -> {
            if (!closed) {
                action.run();
            }
        });
    }

    public void close() {
        closed = true;
        guard.invalidate();
        stability.reset();
        handler.removeCallbacksAndMessages(null);
    }

    private void scanOnce() {
        if (closed) {
            return;
        }
        long expectedGeneration = guard.generation();
        SettingsSnapshot snapshot = source.capture();
        if (!guard.isGenerationCurrent(expectedGeneration)) {
            return;
        }
        if (snapshot == null) {
            stability.reset();
            listener.onNoTrustedWindow(expectedGeneration);
            return;
        }
        if (!stability.observe(snapshot)) {
            handler.postDelayed(scan, SECOND_FINGERPRINT_DELAY_MS);
            return;
        }
        ScanGenerationGuard.Token token = guard.token(snapshot);
        if (guard.isCurrent(token, snapshot)) {
            listener.onStableSnapshot(token, snapshot);
        }
    }
}
