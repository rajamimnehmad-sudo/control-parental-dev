package com.glosh.remote.spike.adb;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbStream;

/**
 * Deliberately small allowlist. The remote side sends action names, never raw shell text.
 */
public final class AdbShell {
    private static final int MAX_OUTPUT_BYTES = 64 * 1024;

    private final AbsAdbConnectionManager manager;

    public AdbShell(AbsAdbConnectionManager manager) {
        this.manager = manager;
    }

    public String execute(String action) throws Exception {
        if (!manager.isConnected()) {
            throw new IllegalStateException("ADB local no está conectado.");
        }
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
