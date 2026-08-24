package com.glosh.remote.spike.wizard;

public record WizardSnapshot(
        OemFamily family,
        OnboardingState.Step step,
        DeveloperGuidePhase developerPhase,
        boolean developerConfirmed,
        boolean wirelessHelp) {
    public WizardSnapshot safeAfterProcessDeath() {
        return switch (step) {
            case CHECKING_SUPPORT, GUIDE_PERMISSION -> new WizardSnapshot(
                    family, OnboardingState.Step.HOME, DeveloperGuidePhase.GUIDE, false, false);
            case REQUESTING_SUPPORT, WIRELESS_DEBUGGING, SESSION_ACTIVE -> new WizardSnapshot(
                    family,
                    OnboardingState.Step.DEVELOPER_OPTIONS,
                    DeveloperGuidePhase.CONFIRMATION,
                    true,
                    false);
            default -> this;
        };
    }
}
