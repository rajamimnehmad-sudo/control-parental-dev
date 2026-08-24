package com.glosh.remote.spike.guide.state;

public enum GuideStage {
    OFF,
    GUIDE_PERMISSION,
    DEV_ABOUT_PHONE,
    DEV_SOFTWARE_INFO,
    DEV_BUILD_NUMBER,
    SUPPORT_PREPARING,
    WIRELESS_DEBUGGING,
    PAIR_CODE_TARGET,
    PAIRING,
    CONNECTED;

    public boolean observesSettings() {
        return this == DEV_ABOUT_PHONE
                || this == DEV_SOFTWARE_INFO
                || this == DEV_BUILD_NUMBER
                || this == WIRELESS_DEBUGGING
                || this == PAIR_CODE_TARGET;
    }
}
