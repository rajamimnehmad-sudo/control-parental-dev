package com.glosh.remote.spike.session;

import com.glosh.remote.spike.wizard.OnboardingState;

/** Clock-free contract between broker descriptor delivery and foreground-service ownership. */
public final class ServiceStartHandoff {
    public enum Decision {
        NONE,
        DISPATCH,
        WAIT,
        ACKNOWLEDGE,
        FINISH
    }

    private ServiceStartHandoff() {
    }

    public static Decision decide(
            OnboardingState.Step step,
            boolean dispatchIssued,
            SessionState serviceState) {
        if (step == OnboardingState.Step.WIRELESS_DEBUGGING) {
            if (serviceState != SessionState.IDLE) {
                return Decision.ACKNOWLEDGE;
            }
            return dispatchIssued ? Decision.WAIT : Decision.DISPATCH;
        }
        if (step == OnboardingState.Step.SESSION_ACTIVE
                && serviceState == SessionState.IDLE) {
            return Decision.FINISH;
        }
        return Decision.NONE;
    }
}
