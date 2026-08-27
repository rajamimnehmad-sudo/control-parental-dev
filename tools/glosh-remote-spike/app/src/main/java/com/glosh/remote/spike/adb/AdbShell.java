package com.glosh.remote.spike.adb;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import io.github.muntashirakon.adb.AdbStream;

/** Deliberately small remote allowlist plus fixed internal session-maintenance operations. */
public final class AdbShell implements ScreenAwakeLease.SettingsAccess {
    private static final int MAX_OUTPUT_BYTES = 64 * 1024;
    private static final long RECONNECT_TIMEOUT_MS = 10_000L;
    private static final String NULL_SETTING = "null";

    private final Context context;
    private final AdbConnectionManager manager;

    public AdbShell(AdbConnectionManager manager) {
        this(null, manager);
    }

    public AdbShell(Context context, AdbConnectionManager manager) {
        this.context = context == null ? null : context.getApplicationContext();
        this.manager = manager;
    }

    public String execute(String action) throws Exception {
        ensureConnected();
        switch (action) {
            case "whoami":
                return runService("shell:id");
            case "device":
                return deviceSummary();
            case "owners":
                return runService("shell:dpm list-owners");
            case "users":
                return runService("shell:pm list users");
            case "battery":
                return runService("shell:dumpsys battery");
            default:
                throw new SecurityException("Acción remota no permitida: " + action);
        }
    }

    @Override
    public String readStayAwakeWhilePluggedIn() throws Exception {
        ensureConnected();
        return runService("shell:settings get global stay_on_while_plugged_in").trim();
    }

    @Override
    public void writeStayAwakeWhilePluggedIn(String value) throws Exception {
        writeNumericSetting("global", "stay_on_while_plugged_in", value, 0L, 7L);
    }

    @Override
    public String readScreenOffTimeout() throws Exception {
        ensureConnected();
        return runService("shell:settings get system screen_off_timeout").trim();
    }

    @Override
    public void writeScreenOffTimeout(String value) throws Exception {
        writeNumericSetting("system", "screen_off_timeout", value, 0L, Integer.MAX_VALUE);
    }

    private void writeNumericSetting(
            String namespace,
            String key,
            String rawValue,
            long min,
            long max) throws Exception {
        ensureConnected();
        String value = rawValue == null ? NULL_SETTING : rawValue.trim();
        if (value.isEmpty() || NULL_SETTING.equalsIgnoreCase(value)) {
            runService("shell:settings delete " + namespace + " " + key);
            return;
        }
        long parsed;
        try {
            parsed = Long.parseLong(value);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Valor de ajuste no numérico.", error);
        }
        if (parsed < min || parsed > max) {
            throw new IllegalArgumentException("Valor de ajuste fuera de rango.");
        }
        runService("shell:settings put " + namespace + " " + key + " " + parsed);
    }

    private void ensureConnected() throws Exception {
        if (manager.isConnected()) {
            return;
        }
        if (context == null || !manager.ensureConnected(context, RECONNECT_TIMEOUT_MS)) {
            throw new IllegalStateException("ADB local no está conectado.");
        }
    }

    private String deviceSummary() throws Exception {
        return "manufacturer=" + oneLine("shell:getprop ro.product.manufacturer") + "\n"
                + "model=" + oneLine("shell:getprop ro.product.model") + "\n"
                + "device=" + oneLine("shell:getprop ro.product.device") + "\n"
                + "android=" + oneLine("shell:getprop ro.build.version.release") + "\n"
                + "sdk=" + oneLine("shell:getprop ro.build.version.sdk") + "\n";
    }

    private String oneLine(String service) throws Exception {
        return runService(service).trim();
    }

    private String runService(String service) throws Exception {
        AdbStream stream = manager.openStream(service);
        try {
            try (InputStream input = stream.openInputStream()) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int total = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    int remaining = MAX_OUTPUT_BYTES - total;
                    if (remaining <= 0) {
                        output.write("\n[output truncated]\n".getBytes(StandardCharsets.UTF_8));
                        break;
                    }
                    int accepted = Math.min(read, remaining);
                    output.write(buffer, 0, accepted);
                    total += accepted;
                }
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
        }
    }
}
