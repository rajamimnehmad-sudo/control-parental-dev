package com.glosh.remote.spike.relay;

import com.glosh.remote.spike.adb.AdbShell;

import java.io.Closeable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Keeps the authenticated relay attached across short network/WebSocket cuts. */
public final class RelaySessionSupervisor implements Closeable {
    public interface Listener {
        void onState(String state, boolean recovery);
        void onAuthenticated(boolean recovery);
        void onPermanentFailure(String message, Throwable error);
    }

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);

    private RelayClient client;
    private Listener listener;
    private String descriptor;
    private boolean everAuthenticated;
    private int reconnectAttempt;

    public synchronized void start(String descriptor, AdbShell shell, Listener listener) {
        if (client != null) {
            throw new IllegalStateException("Relay supervisor already started.");
        }
        this.descriptor = descriptor;
        this.listener = listener;
        this.client = new RelayClient(shell);
        connectNow();
    }

    public synchronized boolean isAuthenticated() {
        return client != null && client.isAuthenticated();
    }

    private void connectNow() {
        if (closed.get()) {
            return;
        }
        RelayClient current;
        String rawDescriptor;
        synchronized (this) {
            current = client;
            rawDescriptor = descriptor;
        }
        if (current == null || rawDescriptor == null) {
            failPermanently("La sesión remota ya no está disponible.", null);
            return;
        }
        try {
            current.connect(rawDescriptor, relayListener());
        } catch (Throwable error) {
            scheduleReconnect(error);
        }
    }

    private RelayClient.Listener relayListener() {
        return new RelayClient.Listener() {
            @Override
            public void onState(String state) {
                Listener current = listener;
                if (!closed.get() && current != null) {
                    current.onState(state, everAuthenticated);
                }
            }

            @Override
            public void onAuthenticated() {
                boolean recovery;
                synchronized (RelaySessionSupervisor.this) {
                    recovery = everAuthenticated;
                    everAuthenticated = true;
                    reconnectAttempt = 0;
                }
                reconnectScheduled.set(false);
                Listener current = listener;
                if (!closed.get() && current != null) {
                    current.onAuthenticated(recovery);
                }
            }

            @Override
            public void onError(String message, Throwable error) {
                scheduleReconnect(error);
            }

            @Override
            public void onClosed() {
                scheduleReconnect(null);
            }
        };
    }

    private void scheduleReconnect(Throwable error) {
        if (closed.get() || !reconnectScheduled.compareAndSet(false, true)) {
            return;
        }
        final int attempt;
        final boolean recovery;
        synchronized (this) {
            attempt = reconnectAttempt++;
            recovery = everAuthenticated;
        }
        long delay = RelayReconnectPolicy.delayMillis(attempt);
        if (delay < 0L) {
            reconnectScheduled.set(false);
            failPermanently("No pudimos recuperar la conexión remota.", error);
            return;
        }
        Listener current = listener;
        if (current != null) {
            current.onState("Reconectando la sesión segura…", recovery);
        }
        scheduler.schedule(() -> {
            reconnectScheduled.set(false);
            connectNow();
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void failPermanently(String message, Throwable error) {
        Listener current = listener;
        if (!closed.get() && current != null) {
            current.onPermanentFailure(message, error);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        reconnectScheduled.set(false);
        scheduler.shutdownNow();
        RelayClient current;
        synchronized (this) {
            current = client;
            client = null;
            descriptor = null;
            listener = null;
        }
        if (current != null) {
            current.close();
        }
    }
}
