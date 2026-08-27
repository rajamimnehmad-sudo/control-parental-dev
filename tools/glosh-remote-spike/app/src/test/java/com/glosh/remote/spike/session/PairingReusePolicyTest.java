package com.glosh.remote.spike.session;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PairingReusePolicyTest {
    @Test
    public void ignoresLatePinOnlyAfterStoredIdentityHasTakenAuthority() {
        assertTrue(PairingReusePolicy.shouldIgnoreSubmittedCode(
                true,
                PairingUiState.CONNECTING));
        assertFalse(PairingReusePolicy.shouldIgnoreSubmittedCode(
                true,
                PairingUiState.DISCOVERING_ENDPOINT));
        assertFalse(PairingReusePolicy.shouldIgnoreSubmittedCode(
                false,
                PairingUiState.CONNECTING));
    }
}
