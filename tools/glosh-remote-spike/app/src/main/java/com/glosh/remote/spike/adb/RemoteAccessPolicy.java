package com.glosh.remote.spike.adb;

import java.util.Locale;
import java.util.regex.Pattern;

public final class RemoteAccessPolicy {
    public static final long MAX_TRANSFER_BYTES = 512L * 1024L * 1024L;
    public static final int MAX_CHUNK_BYTES = 128 * 1024;

    private static final Pattern TRANSFER_ID = Pattern.compile("[A-Za-z0-9_-]{1,80}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");

    private RemoteAccessPolicy() {
    }

    public static void requireValidTransfer(String transferId, long size, String sha256) {
        if (transferId == null || !TRANSFER_ID.matcher(transferId).matches()) {
            throw new IllegalArgumentException("ID de transferencia inválido.");
        }
        if (size <= 0 || size > MAX_TRANSFER_BYTES) {
            throw new IllegalArgumentException("Tamaño de transferencia fuera de límites.");
        }
        if (sha256 == null || !SHA256.matcher(sha256).matches()) {
            throw new IllegalArgumentException("SHA-256 de transferencia inválido.");
        }
    }

    public static void requireValidRemotePath(String remotePath) {
        if (remotePath == null
                || !remotePath.startsWith("/")
                || remotePath.length() > 512
                || remotePath.indexOf('\0') >= 0
                || remotePath.indexOf(',') >= 0
                || remotePath.indexOf('\n') >= 0
                || remotePath.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Ruta remota inválida.");
        }
    }

    public static String normalizedSha256(String sha256) {
        return sha256.toLowerCase(Locale.US);
    }
}
