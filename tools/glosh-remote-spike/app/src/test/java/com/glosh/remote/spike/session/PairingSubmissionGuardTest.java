package com.glosh.remote.spike.session;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PairingSubmissionGuardTest {
    @Test
    public void appAndNotificationCannotStartPairingTwice() {
        PairingSubmissionGuard guard = new PairingSubmissionGuard();
        assertTrue(guard.tryStart());
        assertFalse(guard.tryStart());
        guard.finish();
        assertTrue(guard.tryStart());
    }
}
