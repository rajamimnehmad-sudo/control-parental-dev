package com.glosh.remote.spike.adb;

import android.content.Context;
import android.os.Build;

import java.net.InetAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.adb.android.AdbMdns;

public final class PairingCoordinator implements AutoCloseable {
    public interface Listener {
        void onStatus(String status);
        void onConnected(String canaryOutput);
        void onError(String message, Throwable error);
    }

    private static final long DISCOVERY_TIMEOUT_SECONDS = 30;
    private static final long CONNECT_TIMEOUT_MS = 15_000;

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public PairingCoordinator(Context context) {
        this.context = context.getApplicationContext();
    }

    public void pairAndConnect(String pairingCode, Listener listener) {
        executor.execute(() -> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                listener.onError("Este spike requiere Android 11 o superior.", null);
                return;
            }
            if (pairingCode == null || !pairingCode.matches("\\d{6}")) {
                listener.onError("El código de emparejamiento debe tener 6 dígitos.", null);
                return;
            }

            AdbMdns pairingDiscovery = null;
            try {
                listener.onStatus("Buscando el ADB de este mismo teléfono…");
                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<String> host = new AtomicReference<>();
                AtomicInteger port = new AtomicInteger(-1);

                pairingDiscovery = new AdbMdns(
                        context,
                        AdbMdns.SERVICE_TYPE_TLS_PAIRING,
                        (InetAddress address, int discoveredPort) -> {
                            if (address != null && discoveredPort > 0 && host.compareAndSet(null, address.getHostAddress())) {
                                port.set(discoveredPort);
                                latch.countDown();
                            }
                        });
                pairingDiscovery.start();

                if (!latch.await(DISCOVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "No apareció el puerto de pairing. Dejá abierta la pantalla “Emparejar dispositivo con código”.");
                }

                listener.onStatus("Emparejando con ADB local…");
                AdbConnectionManager manager = AdbConnectionManager.getInstance(context);
                manager.pair(host.get(), port.get(), pairingCode);

                listener.onStatus("Pairing correcto. Conectando al canal TLS de ADB…");
                boolean connected = manager.connectTls(context, CONNECT_TIMEOUT_MS);
                if (!connected && !manager.isConnected()) {
                    throw new IllegalStateException("ADB quedó emparejado pero no se pudo abrir la conexión TLS.");
                }

                AdbShell shell = new AdbShell(manager);
                String canary = shell.execute("whoami");
                listener.onConnected(canary);
            } catch (Throwable error) {
                listener.onError(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(), error);
            } finally {
                if (pairingDiscovery != null) {
                    pairingDiscovery.stop();
                }
            }
        });
    }

    public boolean isConnected() {
        try {
            return AdbConnectionManager.getInstance(context).isConnected();
        } catch (Exception ignored) {
            return false;
        }
    }

    public void disconnect() {
        executor.execute(() -> {
            try {
                AdbConnectionManager.getInstance(context).disconnect();
            } catch (Exception ignored) {
            }
        });
    }

    public void revokeIdentity() {
        executor.execute(() -> AdbConnectionManager.resetIdentity(context));
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
