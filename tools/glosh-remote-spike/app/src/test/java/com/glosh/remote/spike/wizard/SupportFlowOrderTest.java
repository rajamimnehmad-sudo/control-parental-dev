package com.glosh.remote.spike.wizard;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SupportFlowOrderTest {
    @Test
    public void discoversBeforeGuideAndRequestsOnlyAfterConfirmation() {
        OnboardingState state = new OnboardingState();
        assertEquals(OnboardingState.BrokerAction.DISCOVER, state.requestSupport());
        state.supportAvailable();
        assertEquals(OnboardingState.Step.DEVELOPER_OPTIONS, state.step());
        assertEquals(OnboardingState.BrokerAction.REQUEST, state.developerOptionsReady());
        assertEquals(OnboardingState.Step.REQUESTING_SUPPORT, state.step());
    }

    @Test
    public void disappearingSupportReturnsToACleanRetry() {
        OnboardingState state = new OnboardingState();
        state.requestSupport();
        state.supportAvailable();
        state.developerOptionsReady();
        state.unavailable();
        assertEquals(OnboardingState.Step.UNAVAILABLE, state.step());
        assertEquals(OnboardingState.BrokerAction.DISCOVER, state.requestSupport());
    }
}
