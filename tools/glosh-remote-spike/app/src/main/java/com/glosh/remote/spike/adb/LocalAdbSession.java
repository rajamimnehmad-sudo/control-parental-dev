package com.glosh.remote.spike.adb;

import android.content.Context;

import java.io.Closeable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns the long-lived local ADB transport, watchdog reconnects and screen-awake lease. */
public final class LocalAdbSession implements Closeable {
    public interface Listener {
        void onConnectionLost();
        void onConnectionRestored();
        void onReconnectError(Throwable error);
    }

    private static final long WATCHDOG_INTERVAL_SECONDS = 5L;
    private static final long RECONNECT_TIMEOUT_MS = 10_000L;

    private final Context context;
    private final AdbConnectionManager manager;
    private final AdbShell shell;
    private final ScreenAwakeLease screenAwakeLease;
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final ConnectionRecoveryLatch recoveryLatch = new ConnectionRecoveryLatch();

    private volatile Listener listener;

    public LocalAdbSession(Context context, AdbConnectionManager manager) {
        this.context = context.getApplicationContext();
        this.manager = manager;
        this.shell = new AdbShell(this.context, manager);
        this.screenAwakeLease = new ScreenAwakeLease(this.context);
    }

    public void activate(Listener listener) throws Exception {
        this.listener = listener;
        String canary = shell.execute("whoami").trim();
        if (canary.isEmpty()) {
            throw new IllegalStateException("ADB respondió sin identidad shell.");
        }
        screenAwakeLease.acquire(shell);
        watchdog.scheduleWithFixedDelay(
                this::checkConnection,
                WATCHDOG_INTERVAL_SECONDS,
                WATCHDOG_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
    }

    public AdbShell shell() {
        return shell;
    }

    public boolean isConnected() {
        return manager.isConnected();
    }

    private void checkConnection() {
        if (closed.get()) {
            return;
        }
        Listener current = listener;
        if (manager.isConnected()) {
            if (recoveryLatch.markHealthy() && current != null) {
                current.onConnectionRestored();
            }
            return;
        }
        if (recoveryLatch.markLost() && current != null) {
            current.onConnectionLost();
        }
        try {
            if (!manager.ensureConnected(context, RECONNECT_TIMEOUT_MS)) {
                throw new IllegalStateException("Wireless ADB reconnect returned false.");
            }
            screenAwakeLease.ensureApplied(shell);
            current = listener;
            if (recoveryLatch.markHealthy() && !closed.get() && current != null) {
                current.onConnectionRestored();
            }
        } catch (Throwable error) {
            current = listener;
            if (!closed.get() && current != null) {
                current.onReconnectError(error);
            }
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        watchdog.shutdownNow();
        if (manager.isConnected()) {
            try {
                screenAwakeLease.release(shell);
            } catch (Throwable ignored) {
                // Snapshot remains app-private so a later connected session can restore it.
            }
        }
        listener = null;
        AdbConnectionManager.releaseConnection();
    }
}
