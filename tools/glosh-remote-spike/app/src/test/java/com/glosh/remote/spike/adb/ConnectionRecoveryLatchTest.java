package com.glosh.remote.spike.adb;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ConnectionRecoveryLatchTest {
    @Test
    public void emitsOneLostAndOneRestoredEdge() {
        ConnectionRecoveryLatch latch = new ConnectionRecoveryLatch();
        assertTrue(latch.markLost());
        assertFalse(latch.markLost());
        assertTrue(latch.markHealthy());
        assertFalse(latch.markHealthy());
    }

    @Test
    public void shellSideReconnectStillEmitsRestoredEdgeLater() {
        ConnectionRecoveryLatch latch = new ConnectionRecoveryLatch();
        assertTrue(latch.markLost());
        assertTrue(latch.markHealthy());
    }
}
