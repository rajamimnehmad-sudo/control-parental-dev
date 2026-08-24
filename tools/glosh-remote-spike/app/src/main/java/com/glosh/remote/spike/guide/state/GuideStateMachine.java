package com.glosh.remote.spike.guide.state;

public final class GuideStateMachine {
    private GuideStage stage = GuideStage.OFF;

    public synchronized GuideStage stage() {
        return stage;
    }

    public synchronized void restore(GuideStage restored) {
        stage = restored == null ? GuideStage.OFF : restored;
    }

    public synchronized void beginPermission() {
        stage = GuideStage.GUIDE_PERMISSION;
    }

    public synchronized void guideEnabled() {
        require(GuideStage.GUIDE_PERMISSION, GuideStage.DEV_ABOUT_PHONE);
        stage = GuideStage.DEV_ABOUT_PHONE;
    }

    public synchronized void targetReached(GuideStage target) {
        if (target == null || !target.observesSettings()) {
            return;
        }
        stage = target;
    }

    public synchronized void developerReady() {
        stage = GuideStage.SUPPORT_PREPARING;
    }

    public synchronized void wirelessDebugging() {
        stage = GuideStage.WIRELESS_DEBUGGING;
    }

    public synchronized void pairingCodeExpected() {
        stage = GuideStage.PAIR_CODE_TARGET;
    }

    public synchronized void pairing() {
        stage = GuideStage.PAIRING;
    }

    public synchronized void connected() {
        stage = GuideStage.CONNECTED;
    }

    public synchronized void reset() {
        stage = GuideStage.OFF;
    }

    private void require(GuideStage... allowed) {
        for (GuideStage value : allowed) {
            if (stage == value) {
                return;
            }
        }
        throw new IllegalStateException("Invalid live-guide transition from " + stage);
    }
}
