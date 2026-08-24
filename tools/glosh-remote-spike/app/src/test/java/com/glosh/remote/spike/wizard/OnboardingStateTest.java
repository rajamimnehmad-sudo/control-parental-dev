package com.glosh.remote.spike.wizard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class OnboardingStateTest {
    @Test
    public void followsGuidedFlowAndResets() {
        OnboardingState state = new OnboardingState();
        assertEquals(OnboardingState.Step.HOME, state.step());
        state.requestSupport();
        assertEquals(OnboardingState.Step.REQUESTING_SUPPORT, state.step());
        state.sessionReady();
        assertEquals(OnboardingState.Step.DEVELOPER_OPTIONS, state.step());
        state.developerOptionsReady();
        assertEquals(OnboardingState.Step.WIRELESS_DEBUGGING, state.step());
        state.sessionStarted();
        assertEquals(OnboardingState.Step.SESSION_ACTIVE, state.step());
        state.reset();
        assertEquals(OnboardingState.Step.HOME, state.step());
    }

    @Test
    public void invalidTransitionFailsClosed() {
        OnboardingState state = new OnboardingState();
        try {
            state.sessionStarted();
            fail("Expected invalid transition");
        } catch (IllegalStateException expected) {
            assertEquals(OnboardingState.Step.HOME, state.step());
        }
    }

    @Test
    public void unavailableCanRetry() {
        OnboardingState state = new OnboardingState();
        state.requestSupport();
        state.unavailable();
        state.requestSupport();
        assertEquals(OnboardingState.Step.REQUESTING_SUPPORT, state.step());
    }
}
