package com.glosh.remote.spike.session;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PinOnlyBootstrapPolicyTest {
    @Test
    public void brokerPendingCannotExposePinInput() {
        assertFalse(PinOnlyBootstrapPolicy.shouldShowCodeInput(
                PairingUiState.CHECKING_SAVED_IDENTITY));
        assertFalse(PinOnlyBootstrapPolicy.shouldShowCodeInput(
                PairingUiState.DISCOVERING_ENDPOINT));
        assertTrue(PinOnlyBootstrapPolicy.shouldShowCodeInput(
                PairingUiState.WAITING_FOR_CODE));
    }

    @Test
    public void wirelessSettingsWaitForSupportSessionAndLaunchOnce() {
        assertFalse(PinOnlyBootstrapPolicy.shouldLaunchWirelessSettings(
                SessionState.PREPARING,
                PairingUiState.CHECKING_SAVED_IDENTITY,
                false,
                false));
        assertFalse(PinOnlyBootstrapPolicy.shouldLaunchWirelessSettings(
                SessionState.PREPARING,
                PairingUiState.DISCOVERING_ENDPOINT,
                false,
                false));
        assertTrue(PinOnlyBootstrapPolicy.shouldLaunchWirelessSettings(
                SessionState.PREPARING,
                PairingUiState.DISCOVERING_ENDPOINT,
                false,
                true));
        assertFalse(PinOnlyBootstrapPolicy.shouldLaunchWirelessSettings(
                SessionState.PREPARING,
                PairingUiState.DISCOVERING_ENDPOINT,
                true,
                true));
    }

    @Test
    public void descriptorCanAttachAfterAdbIsReady() {
        assertFalse(PinOnlyBootstrapPolicy.canAttachDescriptor(SessionState.IDLE, false));
        assertTrue(PinOnlyBootstrapPolicy.canAttachDescriptor(SessionState.PREPARING, false));
        assertTrue(PinOnlyBootstrapPolicy.canAttachDescriptor(SessionState.ADB_READY, false));
        assertFalse(PinOnlyBootstrapPolicy.canAttachDescriptor(SessionState.ADB_READY, true));
    }
}
