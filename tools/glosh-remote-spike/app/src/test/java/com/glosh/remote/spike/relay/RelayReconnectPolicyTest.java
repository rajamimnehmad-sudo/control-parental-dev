package com.glosh.remote.spike.relay;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class RelayReconnectPolicyTest {
    @Test
    public void reconnectWindowIsBounded() {
        assertEquals(1_000L, RelayReconnectPolicy.delayMillis(0));
        assertEquals(2_000L, RelayReconnectPolicy.delayMillis(1));
        assertEquals(30_000L, RelayReconnectPolicy.delayMillis(5));
        assertEquals(-1L, RelayReconnectPolicy.delayMillis(6));
        assertEquals(-1L, RelayReconnectPolicy.delayMillis(-1));
    }
}
