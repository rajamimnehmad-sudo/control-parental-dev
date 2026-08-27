package com.glosh.remote.spike.adb;

final class AdbConnectEndpointPolicy {
    private static final String LOCAL_CONNECT_HOST = "127.0.0.1";
    private static final long MAX_DISCOVERY_SLICE_MS = 3_000L;

    private AdbConnectEndpointPolicy() {
    }

    static boolean isUsable(String discoveredHost, int port) {
        return discoveredHost != null
                && !discoveredHost.isBlank()
                && port > 0
                && port <= 65_535;
    }

    static String connectHost() {
        return LOCAL_CONNECT_HOST;
    }

    static long discoverySliceMillis(long remainingMillis) {
        return Math.max(1L, Math.min(MAX_DISCOVERY_SLICE_MS, remainingMillis));
    }
}
