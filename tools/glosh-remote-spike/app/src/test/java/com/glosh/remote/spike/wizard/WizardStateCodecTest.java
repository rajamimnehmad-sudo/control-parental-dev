package com.glosh.remote.spike.wizard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Map;
import java.util.Set;

public class WizardStateCodecTest {
    @Test
    public void rotationAndRecreationPreserveNonSensitiveStage() {
        WizardSnapshot source = new WizardSnapshot(
                OemFamily.SAMSUNG,
                OnboardingState.Step.DEVELOPER_OPTIONS,
                DeveloperGuidePhase.HELP,
                false,
                false);
        WizardSnapshot restored = WizardStateCodec.decode(
                WizardStateCodec.encode(source),
                OemFamily.GENERIC);
        assertEquals(source, restored);
        assertEquals(OnboardingState.Step.DEVELOPER_OPTIONS, restored.step());
    }

    @Test
    public void persistenceSchemaContainsNoSecrets() {
        Map<String, String> encoded = WizardStateCodec.encode(new WizardSnapshot(
                OemFamily.XIAOMI_FAMILY,
                OnboardingState.Step.WIRELESS_DEBUGGING,
                DeveloperGuidePhase.CONFIRMATION,
                true,
                true));
        assertEquals(Set.of(
                "oem_family",
                "wizard_step",
                "developer_phase",
                "developer_confirmed",
                "wireless_help"), encoded.keySet());
        String joined = String.join(" ", encoded.keySet()).toLowerCase();
        for (String forbidden : new String[] {"nonce", "rsa", "descriptor", "session", "key", "secret"}) {
            assertFalse(joined.contains(forbidden));
        }
    }

    @Test
    public void processFailureReturnsToNearestSafePoint() {
        WizardSnapshot active = new WizardSnapshot(
                OemFamily.MOTOROLA,
                OnboardingState.Step.SESSION_ACTIVE,
                DeveloperGuidePhase.CONFIRMATION,
                true,
                false);
        WizardSnapshot safe = active.safeAfterProcessDeath();
        assertEquals(OnboardingState.Step.DEVELOPER_OPTIONS, safe.step());
        assertEquals(DeveloperGuidePhase.CONFIRMATION, safe.developerPhase());
        assertTrue(safe.developerConfirmed());
    }
}
