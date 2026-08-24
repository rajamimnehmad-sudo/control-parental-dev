package com.glosh.remote.spike.wizard;

public final class OnboardingState {
    public enum Step {
        HOME,
        CHECKING_SUPPORT,
        REQUESTING_SUPPORT,
        DEVELOPER_OPTIONS,
        WIRELESS_DEBUGGING,
        SESSION_ACTIVE,
        UNAVAILABLE
    }

    private Step step = Step.HOME;

    public synchronized Step step() {
        return step;
    }

    public synchronized BrokerAction requestSupport() {
        require(Step.HOME, Step.UNAVAILABLE);
        step = Step.CHECKING_SUPPORT;
        return BrokerAction.DISCOVER;
    }

    public synchronized void supportAvailable() {
        require(Step.CHECKING_SUPPORT);
        step = Step.DEVELOPER_OPTIONS;
    }

    public synchronized void sessionReady() {
        require(Step.REQUESTING_SUPPORT, Step.HOME);
        step = Step.WIRELESS_DEBUGGING;
    }

    public synchronized BrokerAction developerOptionsReady() {
        require(Step.DEVELOPER_OPTIONS);
        step = Step.REQUESTING_SUPPORT;
        return BrokerAction.REQUEST;
    }

    public synchronized void sessionStarted() {
        require(Step.WIRELESS_DEBUGGING);
        step = Step.SESSION_ACTIVE;
    }

    public synchronized void unavailable() {
        require(Step.CHECKING_SUPPORT, Step.REQUESTING_SUPPORT);
        step = Step.UNAVAILABLE;
    }

    public synchronized void restore(Step restored) {
        step = restored;
    }

    public synchronized void reset() {
        step = Step.HOME;
    }

    private void require(Step... allowed) {
        for (Step candidate : allowed) {
            if (step == candidate) {
                return;
            }
        }
        throw new IllegalStateException("Invalid onboarding transition from " + step);
    }

    public enum BrokerAction {
        DISCOVER,
        REQUEST
    }
}
