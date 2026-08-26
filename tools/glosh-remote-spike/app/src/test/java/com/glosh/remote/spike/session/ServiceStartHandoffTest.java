package com.glosh.remote.spike.session;

import static org.junit.Assert.assertEquals;

import com.glosh.remote.spike.wizard.OnboardingState;

import org.junit.Test;

public class ServiceStartHandoffTest {
    @Test
    public void readyDescriptorDispatchesServiceExactlyOnce() {
        assertEquals(
                ServiceStartHandoff.Decision.DISPATCH,
                ServiceStartHandoff.decide(
                        OnboardingState.Step.WIRELESS_DEBUGGING,
                        false,
                        SessionState.IDLE));
    }

    @Test
    public void dispatchedServiceWaitsWithoutAClockOrAutomaticReset() {
        assertEquals(
                ServiceStartHandoff.Decision.WAIT,
                ServiceStartHandoff.decide(
                        OnboardingState.Step.WIRELESS_DEBUGGING,
                        true,
                        SessionState.IDLE));
    }

    @Test
    public void serviceStateAcknowledgesBrokerHandoff() {
        assertEquals(
                ServiceStartHandoff.Decision.ACKNOWLEDGE,
                ServiceStartHandoff.decide(
                        OnboardingState.Step.WIRELESS_DEBUGGING,
                        true,
                        SessionState.PREPARING));
        assertEquals(
                ServiceStartHandoff.Decision.ACKNOWLEDGE,
                ServiceStartHandoff.decide(
                        OnboardingState.Step.WIRELESS_DEBUGGING,
                        false,
                        SessionState.CONNECTED));
    }

    @Test
    public void acknowledgedSessionFinishesOnlyAfterServiceReturnsIdle() {
        assertEquals(
                ServiceStartHandoff.Decision.NONE,
                ServiceStartHandoff.decide(
                        OnboardingState.Step.SESSION_ACTIVE,
                        true,
                        SessionState.PREPARING));
        assertEquals(
                ServiceStartHandoff.Decision.NONE,
                ServiceStartHandoff.decide(
                        OnboardingState.Step.SESSION_ACTIVE,
                        true,
                        SessionState.CONNECTED));
        assertEquals(
                ServiceStartHandoff.Decision.FINISH,
                ServiceStartHandoff.decide(
                        OnboardingState.Step.SESSION_ACTIVE,
                        true,
                        SessionState.IDLE));
    }
}
