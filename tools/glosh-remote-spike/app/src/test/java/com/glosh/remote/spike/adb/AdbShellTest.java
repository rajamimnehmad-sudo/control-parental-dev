package com.glosh.remote.spike.adb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public final class AdbShellTest {
    @Test
    public void readsNormalCommandOutput() throws Exception {
        assertEquals(
                "uid=2000(shell)\n",
                AdbShell.readCommandOutput(new ByteArrayInputStream(
                        "uid=2000(shell)\n".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    public void preservesPayloadWhenPeerCloseArrivesAfterIt() throws Exception {
        assertEquals(
                "uid=2000(shell)\n",
                AdbShell.readCommandOutput(new PayloadThenFailureInput(
                        "uid=2000(shell)\n",
                        new IOException("Stream closed."))));
    }

    @Test
    public void doesNotMaskPeerCloseBeforeAnyPayload() {
        assertThrows(
                IOException.class,
                () -> AdbShell.readCommandOutput(new PayloadThenFailureInput(
                        "",
                        new IOException("Stream closed."))));
    }

    @Test
    public void doesNotMaskDifferentFailureAfterPayload() {
        assertThrows(
                IOException.class,
                () -> AdbShell.readCommandOutput(new PayloadThenFailureInput(
                        "partial",
                        new IOException("socket reset"))));
    }

    private static final class PayloadThenFailureInput extends InputStream {
        private final byte[] payload;
        private final IOException failure;
        private boolean delivered;

        private PayloadThenFailureInput(String payload, IOException failure) {
            this.payload = payload.getBytes(StandardCharsets.UTF_8);
            this.failure = failure;
        }

        @Override
        public int read() throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (!delivered && payload.length > 0) {
                delivered = true;
                int count = Math.min(length, payload.length);
                System.arraycopy(payload, 0, buffer, offset, count);
                return count;
            }
            throw failure;
        }
    }
}
