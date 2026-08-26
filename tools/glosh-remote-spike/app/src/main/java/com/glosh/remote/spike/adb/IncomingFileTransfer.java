package com.glosh.remote.spike.adb;

import android.content.Context;
import android.util.Base64;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;

public final class IncomingFileTransfer implements AutoCloseable {
    private final File cacheDirectory;
    private final AdbShell adbShell;

    private String transferId;
    private String expectedSha256;
    private String remotePath;
    private long expectedSize;
    private long receivedSize;
    private File stagingFile;
    private OutputStream output;
    private MessageDigest digest;

    public IncomingFileTransfer(Context context, AdbShell adbShell) {
        this.cacheDirectory = context.getCacheDir();
        this.adbShell = adbShell;
    }

    public synchronized String start(
            String newTransferId,
            long size,
            String sha256,
            String newRemotePath) throws Exception {
        abort();
        RemoteAccessPolicy.requireValidTransfer(newTransferId, size, sha256);
        RemoteAccessPolicy.requireValidRemotePath(newRemotePath);

        transferId = newTransferId;
        expectedSize = size;
        expectedSha256 = RemoteAccessPolicy.normalizedSha256(sha256);
        remotePath = newRemotePath;
        receivedSize = 0;
        digest = MessageDigest.getInstance("SHA-256");
        stagingFile = new File(cacheDirectory, "remote-upload-" + newTransferId + ".bin");
        output = new BufferedOutputStream(new FileOutputStream(stagingFile, false));
        return "Transferencia preparada: " + size + " bytes.";
    }

    public synchronized String append(String expectedTransferId, long offset, String encodedData)
            throws Exception {
        requireActive(expectedTransferId);
        if (offset != receivedSize) {
            throw new IllegalArgumentException("Offset de transferencia inesperado.");
        }
        byte[] data;
        try {
            data = Base64.decode(encodedData, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Chunk base64 inválido.", error);
        }
        if (data.length == 0 || data.length > RemoteAccessPolicy.MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("Chunk fuera de límites.");
        }
        if (receivedSize + data.length > expectedSize) {
            throw new IllegalArgumentException("La transferencia excede el tamaño declarado.");
        }
        output.write(data);
        digest.update(data);
        receivedSize += data.length;
        return "Chunk recibido: " + receivedSize + "/" + expectedSize;
    }

    public synchronized String finish(String expectedTransferId) throws Exception {
        requireActive(expectedTransferId);
        try {
            output.close();
            output = null;
            if (receivedSize != expectedSize) {
                throw new IllegalStateException("La transferencia quedó incompleta.");
            }
            String actualSha256 = hex(digest.digest());
            if (!MessageDigest.isEqual(
                    actualSha256.getBytes(), expectedSha256.getBytes())) {
                throw new SecurityException("El SHA-256 transferido no coincide.");
            }
            String result = adbShell.push(stagingFile, remotePath);
            return result + "\nSHA-256 verificado: " + actualSha256;
        } finally {
            clearState();
        }
    }

    public synchronized void abort() {
        if (output != null) {
            try {
                output.close();
            } catch (IOException ignored) {
            }
        }
        clearState();
    }

    @Override
    public void close() {
        abort();
    }

    private void requireActive(String expectedTransferId) {
        if (transferId == null || !transferId.equals(expectedTransferId) || output == null) {
            throw new IllegalStateException("No existe esa transferencia activa.");
        }
    }

    private void clearState() {
        output = null;
        if (stagingFile != null && stagingFile.exists()) {
            // The file is only an encrypted-session staging copy; never persist it.
            stagingFile.delete();
        }
        transferId = null;
        expectedSha256 = null;
        remotePath = null;
        expectedSize = 0;
        receivedSize = 0;
        stagingFile = null;
        digest = null;
    }

    private static String hex(byte[] value) {
        StringBuilder output = new StringBuilder(value.length * 2);
        for (byte item : value) {
            output.append(String.format("%02x", item & 0xff));
        }
        return output.toString();
    }
}
