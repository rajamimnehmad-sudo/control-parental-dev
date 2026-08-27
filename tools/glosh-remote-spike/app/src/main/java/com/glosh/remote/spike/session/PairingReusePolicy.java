package com.glosh.remote.spike.session;

/** Prevents a late PIN submission from disrupting an already reused ADB identity bootstrap. */
public final class PairingReusePolicy {
    private PairingReusePolicy() {
    }

    public static boolean shouldIgnoreSubmittedCode(
            boolean reusedIdentity,
            PairingUiState pairingUiState) {
        return reusedIdentity && pairingUiState == PairingUiState.CONNECTING;
    }
}
