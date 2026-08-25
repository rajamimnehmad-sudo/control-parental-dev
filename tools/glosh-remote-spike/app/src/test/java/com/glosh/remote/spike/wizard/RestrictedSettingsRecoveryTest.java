package com.glosh.remote.spike.wizard;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RestrictedSettingsRecoveryTest {
    @Test
    public void recoveryIsNeverShownBeforeAccessibilityWasTried() {
        assertFalse(RestrictedSettingsPreflight.shouldOfferRecovery(true, false, false));
        assertFalse(RestrictedSettingsPreflight.shouldOfferRecovery(false, true, false));
    }

    @Test
    public void failedAttemptOffersRecoveryButEnabledAccessibilityWins() {
        assertTrue(RestrictedSettingsPreflight.shouldOfferRecovery(true, true, false));
        assertFalse(RestrictedSettingsPreflight.shouldOfferRecovery(true, true, true));
    }
}
