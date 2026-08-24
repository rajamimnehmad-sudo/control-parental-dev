package com.glosh.remote.spike.guide.accessibility;

public final class ScanGenerationGuard {
    public record Token(long generation, int windowId, String fingerprint) {
        public Token {
            fingerprint = fingerprint == null ? "" : fingerprint;
        }
    }

    private long generation;

    public synchronized long invalidate() {
        return ++generation;
    }

    public synchronized long generation() {
        return generation;
    }

    public synchronized Token token(SettingsSnapshot snapshot) {
        return new Token(generation, snapshot.windowId(), snapshot.fingerprint());
    }

    public synchronized boolean isCurrent(Token token, SettingsSnapshot snapshot) {
        return token != null
                && snapshot != null
                && token.generation() == generation
                && token.windowId() == snapshot.windowId()
                && token.fingerprint().equals(snapshot.fingerprint());
    }

    public synchronized boolean isGenerationCurrent(long expected) {
        return expected == generation;
    }
}
