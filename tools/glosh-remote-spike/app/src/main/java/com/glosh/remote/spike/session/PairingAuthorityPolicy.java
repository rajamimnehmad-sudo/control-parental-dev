package com.glosh.remote.spike.session;

public final class PairingAuthorityPolicy {
    private PairingAuthorityPolicy() {
    }

    public static boolean canSubmit(
            SessionState session,
            PairingUiState pairing,
            boolean submitAlreadyActive,
            boolean endpointReady) {
        return session == SessionState.PREPARING
                && (pairing == PairingUiState.WAITING_FOR_CODE
                || pairing == PairingUiState.CODE_FAILED)
                && !submitAlreadyActive
                && endpointReady;
    }

    public static boolean canBecomeConnected(
            SessionState session,
            PairingUiState pairing,
            boolean submitActive,
            boolean requestReady) {
        return session == SessionState.PREPARING
                && pairing == PairingUiState.CONNECTING
                && submitActive
                && requestReady;
    }
}
