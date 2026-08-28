package com.glosh.remote.spike.session;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.PowerManager;

/** Process-bound screen/CPU/Wi-Fi lease; Android releases every lock if the service dies. */
public final class ConnectionAwakeLease {
    private final PowerManager.WakeLock wakeLock;
    private final WifiManager.WifiLock wifiLock;

    public ConnectionAwakeLease(Context context) {
        Context app = context.getApplicationContext();
        PowerManager power = app.getSystemService(PowerManager.class);
        WifiManager wifi = app.getSystemService(WifiManager.class);
        if (power == null || wifi == null) {
            throw new IllegalStateException("No se pudieron preparar los locks de conexión.");
        }
        wakeLock = power.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK,
                "glosh-remote:active-support-screen");
        wakeLock.setReferenceCounted(false);
        wifiLock = wifi.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "glosh-remote:active-support-wifi");
        wifiLock.setReferenceCounted(false);
    }

    public synchronized void acquire() {
        if (!wakeLock.isHeld()) {
            wakeLock.acquire();
        }
        if (!wifiLock.isHeld()) {
            wifiLock.acquire();
        }
    }

    public synchronized void release() {
        if (wifiLock.isHeld()) {
            wifiLock.release();
        }
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
    }
}
