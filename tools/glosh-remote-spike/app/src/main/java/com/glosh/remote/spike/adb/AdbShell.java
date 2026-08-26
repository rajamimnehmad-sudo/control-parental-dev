package com.glosh.remote.spike.adb;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbStream;

/**
 * ADB surface exposed only after the user-authorized, encrypted support session authenticates.
 */
public final class AdbShell {
    private static final int MAX_OUTPUT_BYTES = 2 * 1024 * 1024;
    private static final int MAX_COMMAND_CHARS = 32 * 1024;
    private static final int SYNC_CHUNK_BYTES = 64 * 1024;

    private final AbsAdbConnectionManager manager;

    public AdbShell(AbsAdbConnectionManager manager) {
        this.manager = manager;
    }

    public String execute(String action) throws Exception {
        return execute(action, null);
    }

    public String execute(String action, String command) throws Exception {
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
            case "shell":
                if (command == null || command.trim().isEmpty()) {
                    throw new IllegalArgumentException("El comando shell está vacío.");
                }
                if (command.length() > MAX_COMMAND_CHARS || command.indexOf('\0') >= 0) {
                    throw new IllegalArgumentException("El comando shell excede los límites.");
                }
                return runService("shell:" + command);
            default:
                throw new IllegalArgumentException("Acción ADB desconocida: " + action);
        }
    }

    public String push(File source, String remotePath) throws Exception {
        if (!manager.isConnected()) {
            throw new IllegalStateException("ADB local no está conectado.");
        }
        if (source == null || !source.isFile()) {
            throw new IllegalArgumentException("El archivo local no existe.");
        }
        RemoteAccessPolicy.requireValidRemotePath(remotePath);

        AdbStream stream = manager.openStream("sync:");
        try (FileInputStream fileInput = new FileInputStream(source)) {
            InputStream input = stream.openInputStream();
            OutputStream output = stream.openOutputStream();
            byte[] sendPath = (remotePath + ",33188").getBytes(StandardCharsets.UTF_8);
            writeSyncHeader(output, "SEND", sendPath.length);
            output.write(sendPath);

            byte[] buffer = new byte[SYNC_CHUNK_BYTES];
            int read;
            while ((read = fileInput.read(buffer)) != -1) {
                writeSyncHeader(output, "DATA", read);
                output.write(buffer, 0, read);
            }
            writeSyncHeader(output, "DONE", (int) Instant.now().getEpochSecond());
            output.flush();

            byte[] response = readExact(input, 8);
            String status = new String(response, 0, 4, StandardCharsets.US_ASCII);
            int payloadLength = littleEndianInt(response, 4);
            if ("OKAY".equals(status)) {
                return "Archivo transferido por ADB: " + source.length() + " bytes -> " + remotePath;
            }
            if ("FAIL".equals(status)) {
                if (payloadLength < 0 || payloadLength > MAX_OUTPUT_BYTES) {
                    throw new IOException("ADB Sync devolvió un error fuera de límites.");
                }
                throw new IOException("ADB Sync: "
                        + new String(readExact(input, payloadLength), StandardCharsets.UTF_8));
            }
            throw new IOException("Respuesta ADB Sync desconocida: " + status);
        } finally {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
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

    private static void writeSyncHeader(OutputStream output, String id, int value) throws IOException {
        output.write(id.getBytes(StandardCharsets.US_ASCII));
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
        output.write((value >>> 16) & 0xff);
        output.write((value >>> 24) & 0xff);
    }

    private static byte[] readExact(InputStream input, int length) throws IOException {
        byte[] value = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(value, offset, length - offset);
            if (read == -1) {
                throw new IOException("ADB Sync cerró la respuesta antes de tiempo.");
            }
            offset += read;
        }
        return value;
    }

    private static int littleEndianInt(byte[] value, int offset) {
        return (value[offset] & 0xff)
                | ((value[offset + 1] & 0xff) << 8)
                | ((value[offset + 2] & 0xff) << 16)
                | ((value[offset + 3] & 0xff) << 24);
    }
}
