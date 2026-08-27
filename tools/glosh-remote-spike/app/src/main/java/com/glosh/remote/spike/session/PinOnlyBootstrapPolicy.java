package com.glosh.remote.spike.session;

/** Pure flow rules for the PIN-only bootstrap so broker readiness cannot expose a dead input state. */
public final class PinOnlyBootstrapPolicy {
    private PinOnlyBootstrapPolicy() {
    }

    public static boolean shouldLaunchWirelessSettings(
            SessionState session,
            PairingUiState pairing,
            boolean alreadyLaunched) {
        return !alreadyLaunched
                && session == SessionState.PREPARING
                && pairing == PairingUiState.DISCOVERING_ENDPOINT;
    }

    public static boolean shouldShowCodeInput(PairingUiState pairing) {
        return pairing == PairingUiState.WAITING_FOR_CODE
                || pairing == PairingUiState.CODE_FAILED;
    }

    public static boolean canAttachDescriptor(
            SessionState session,
            boolean descriptorAlreadyAttached) {
        return !descriptorAlreadyAttached
                && session != SessionState.IDLE
                && session != SessionState.CONNECTED;
    }
}
