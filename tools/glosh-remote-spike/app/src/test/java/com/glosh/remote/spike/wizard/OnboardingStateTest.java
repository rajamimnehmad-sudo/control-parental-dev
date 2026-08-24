package com.glosh.remote.spike.wizard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class OnboardingStateTest {
    @Test
    public void followsGuidedFlowAndResets() {
        OnboardingState state = new OnboardingState();
        assertEquals(OnboardingState.Step.HOME, state.step());
        assertEquals(OnboardingState.BrokerAction.DISCOVER, state.requestSupport());
        assertEquals(OnboardingState.Step.CHECKING_SUPPORT, state.step());
        state.supportAvailable();
        assertEquals(OnboardingState.Step.GUIDE_PERMISSION, state.step());
        state.guideReady();
        assertEquals(OnboardingState.Step.DEVELOPER_OPTIONS, state.step());
        assertEquals(OnboardingState.BrokerAction.REQUEST, state.developerOptionsReady());
        assertEquals(OnboardingState.Step.REQUESTING_SUPPORT, state.step());
        state.sessionReady();
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
        assertEquals(OnboardingState.BrokerAction.DISCOVER, state.requestSupport());
        assertEquals(OnboardingState.Step.CHECKING_SUPPORT, state.step());
    }
}
