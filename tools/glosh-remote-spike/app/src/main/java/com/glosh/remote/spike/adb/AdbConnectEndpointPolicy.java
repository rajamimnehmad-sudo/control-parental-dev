package com.glosh.remote.spike.adb;

final class AdbConnectEndpointPolicy {
    private static final long MAX_DISCOVERY_SLICE_MS = 3_000L;

    private AdbConnectEndpointPolicy() {
    }

    static boolean isUsable(String host, int port) {
        return host != null && !host.isBlank() && port > 0 && port <= 65_535;
    }

    static long discoverySliceMillis(long remainingMillis) {
        return Math.max(1L, Math.min(MAX_DISCOVERY_SLICE_MS, remainingMillis));
    }
}
