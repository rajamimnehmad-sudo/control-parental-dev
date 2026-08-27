package com.glosh.remote.spike.adb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AdbConnectEndpointPolicyTest {
    @Test
    public void serviceLostAndInvalidEndpointsAreIgnored() {
        assertFalse(AdbConnectEndpointPolicy.isUsable(null, -1));
        assertFalse(AdbConnectEndpointPolicy.isUsable("127.0.0.1", -1));
        assertFalse(AdbConnectEndpointPolicy.isUsable("", 37001));
        assertFalse(AdbConnectEndpointPolicy.isUsable("127.0.0.1", 0));
        assertFalse(AdbConnectEndpointPolicy.isUsable("127.0.0.1", 65_536));
    }

    @Test
    public void resolvedTlsEndpointIsAccepted() {
        assertTrue(AdbConnectEndpointPolicy.isUsable("127.0.0.1", 37001));
        assertTrue(AdbConnectEndpointPolicy.isUsable("192.168.1.20", 65535));
    }

    @Test
    public void discoveryUsesBoundedSlices() {
        assertEquals(1L, AdbConnectEndpointPolicy.discoverySliceMillis(0L));
        assertEquals(750L, AdbConnectEndpointPolicy.discoverySliceMillis(750L));
        assertEquals(3_000L, AdbConnectEndpointPolicy.discoverySliceMillis(10_000L));
    }
}
