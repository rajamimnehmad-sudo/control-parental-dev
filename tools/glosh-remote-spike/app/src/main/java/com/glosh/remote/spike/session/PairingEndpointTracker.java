package com.glosh.remote.spike.session;

/** Tracks the current mDNS pairing endpoint and makes stale snapshots detectable. */
public final class PairingEndpointTracker {
    private long generation;
    private Endpoint current;

    public synchronized Endpoint observe(String host, int port) {
        if (host == null || host.trim().isEmpty() || port <= 0) {
            throw new IllegalArgumentException("Pairing endpoint must contain host and port.");
        }
        String normalizedHost = host.trim();
        if (current != null
                && current.host.equals(normalizedHost)
                && current.port == port) {
            return current;
        }
        current = new Endpoint(normalizedHost, port, ++generation);
        return current;
    }

    public synchronized void lost(String host) {
        if (current == null) {
            return;
        }
        String normalizedHost = host == null ? null : host.trim();
        if (normalizedHost == null
                || normalizedHost.isEmpty()
                || current.host.equals(normalizedHost)) {
            generation++;
            current = null;
        }
    }

    public synchronized Endpoint current() {
        return current;
    }

    public synchronized boolean isCurrent(Endpoint endpoint) {
        return endpoint != null
                && current != null
                && current.generation == endpoint.generation
                && current.port == endpoint.port
                && current.host.equals(endpoint.host);
    }

    public synchronized void clear() {
        generation++;
        current = null;
    }

    public static final class Endpoint {
        private final String host;
        private final int port;
        private final long generation;

        private Endpoint(String host, int port, long generation) {
            this.host = host;
            this.port = port;
            this.generation = generation;
        }

        public String host() {
            return host;
        }

        public int port() {
            return port;
        }

        public long generation() {
            return generation;
        }
    }
}
