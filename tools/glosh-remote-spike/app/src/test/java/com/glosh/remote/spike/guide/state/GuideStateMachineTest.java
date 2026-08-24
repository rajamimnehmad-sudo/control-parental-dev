package com.glosh.remote.spike.guide.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.glosh.remote.spike.wizard.OemFamily;

import org.junit.Test;

import java.util.Set;

public class GuideStateMachineTest {
    @Test
    public void stateSurvivesUiRecreationAndRecognizedRescue() {
        GuideStateMachine state = new GuideStateMachine();
        state.beginPermission();
        state.guideEnabled();
        state.targetReached(GuideStage.DEV_BUILD_NUMBER);
        GuideStage saved = state.stage();
        GuideStateMachine restored = new GuideStateMachine();
        restored.restore(saved);
        assertEquals(GuideStage.DEV_BUILD_NUMBER, restored.stage());
        restored.targetReached(GuideStage.DEV_SOFTWARE_INFO);
        assertEquals(GuideStage.DEV_SOFTWARE_INFO, restored.stage());
    }

    @Test
    public void connectLifecycleEndsAtOffAfterReset() {
        GuideStateMachine state = new GuideStateMachine();
        state.beginPermission();
        state.guideEnabled();
        state.developerReady();
        state.wirelessDebugging();
        state.pairingCodeExpected();
        state.pairing();
        state.connected();
        assertEquals(GuideStage.CONNECTED, state.stage());
        state.reset();
        assertEquals(GuideStage.OFF, state.stage());
    }

    @Test
    public void persistenceKeysContainNoSecrets() {
        assertEquals(Set.of("guide_stage", "oem_family", "onboarding_active"),
                GuideStateStore.persistedKeys());
        String joined = String.join(" ", GuideStateStore.persistedKeys()).toLowerCase();
        for (String forbidden : new String[] {"nonce", "descriptor", "pairing_code", "private_key", "session_key"}) {
            assertFalse(joined.contains(forbidden));
        }
        GuideStateStore.Snapshot snapshot = new GuideStateStore.Snapshot(
                GuideStage.WIRELESS_DEBUGGING, OemFamily.SAMSUNG, true);
        assertEquals(GuideStage.WIRELESS_DEBUGGING, snapshot.stage());
    }
}
