package com.glosh.remote.spike.session;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PairingAuthorityPolicyTest {
    @Test
    public void pairingRequiresCurrentEndpointButNotRelayDescriptor() {
        assertFalse(PairingAuthorityPolicy.canSubmit(
                SessionState.PREPARING, PairingUiState.DISCOVERING_ENDPOINT,
                false, true));
        assertFalse(PairingAuthorityPolicy.canSubmit(
                SessionState.PREPARING, PairingUiState.WAITING_FOR_CODE,
                false, false));
        assertTrue(PairingAuthorityPolicy.canSubmit(
                SessionState.PREPARING, PairingUiState.WAITING_FOR_CODE,
                false, true));
    }

    @Test
    public void connectedRequiresActivePairingAndRequest() {
        assertFalse(PairingAuthorityPolicy.canBecomeConnected(
                SessionState.PREPARING, PairingUiState.CONNECTING, false, true));
        assertFalse(PairingAuthorityPolicy.canBecomeConnected(
                SessionState.PREPARING, PairingUiState.CONNECTING, true, false));
        assertTrue(PairingAuthorityPolicy.canBecomeConnected(
                SessionState.PREPARING, PairingUiState.CONNECTING, true, true));
    }
}
