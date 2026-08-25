package com.glosh.remote.spike.guide.autopilot;

/**
 * Pure state policy for the Samsung Wireless Debugging deep-link.
 *
 * <p>The direct route is attempted once. If Samsung resolves it back to Developer options, Glosh
 * waits there instead of relaunching. One retry is allowed only after a genuinely different trusted
 * Developer-options snapshot, which represents a user-driven state change such as enabling the
 * master Developer options switch. A second return to Developer options becomes a stable visual
 * fallback and can never loop.</p>
 */
public final class WirelessDirectRoutePolicy {
    public enum Decision {
        WAIT_FOR_USER,
        RETRY_DIRECT_ONCE,
        VISUAL_FALLBACK
    }

    private int attempts;
    private String firstReturnedFingerprint = "";

    public void reset() {
        attempts = 0;
        firstReturnedFingerprint = "";
    }

    public void markDirectAttempt() {
        attempts = Math.min(2, attempts + 1);
    }

    public int attempts() {
        return attempts;
    }

    public Decision onDeveloperOptions(String fingerprint) {
        String current = fingerprint == null ? "" : fingerprint;
        if (attempts <= 0) {
            return Decision.VISUAL_FALLBACK;
        }
        if (attempts >= 2) {
            return Decision.VISUAL_FALLBACK;
        }
        if (firstReturnedFingerprint.isEmpty()) {
            firstReturnedFingerprint = current;
            return Decision.WAIT_FOR_USER;
        }
        if (!current.equals(firstReturnedFingerprint)) {
            return Decision.RETRY_DIRECT_ONCE;
        }
        return Decision.WAIT_FOR_USER;
    }
}
