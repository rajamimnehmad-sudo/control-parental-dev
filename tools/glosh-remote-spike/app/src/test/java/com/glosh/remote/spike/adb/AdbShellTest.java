package com.glosh.remote.spike.adb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Test
    public void gatesEverySequentialPreflightStreamAndRecoversStaleTransport() throws Exception {
        AtomicBoolean connected = new AtomicBoolean(true);
        AtomicInteger gateCount = new AtomicInteger();
        AtomicInteger recoveryCount = new AtomicInteger();
        List<String> events = new ArrayList<>();

        AdbShell.CheckedOperation gate = () -> {
            int invocation = gateCount.incrementAndGet();
            events.add("gate-" + invocation);
            if (!connected.get()) {
                recoveryCount.incrementAndGet();
                connected.set(true);
            }
        };

        for (int command = 1; command <= 3; command++) {
            int invocation = command;
            assertEquals(
                    "stream-" + invocation,
                    AdbShell.openAfterConnectionGate(gate, () -> {
                        events.add("open-" + invocation);
                        assertTrue(connected.get());
                        connected.set(false);
                        return "stream-" + invocation;
                    }));
        }

        assertEquals(3, gateCount.get());
        assertEquals(2, recoveryCount.get());
        assertEquals(
                Arrays.asList("gate-1", "open-1", "gate-2", "open-2", "gate-3", "open-3"),
                events);
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
