package com.glosh.remote.spike.guide.autopilot;

import com.glosh.remote.spike.guide.autopilot.AutopilotContract.Action;
import com.glosh.remote.spike.guide.autopilot.AutopilotContract.Decision;
import com.glosh.remote.spike.guide.autopilot.AutopilotContract.Observation;
import com.glosh.remote.spike.guide.autopilot.AutopilotContract.Screen;

public final class AdaptiveAutopilotPlanner {
    public static final int MIN_WIRELESS_ADB_API = 30;

    public Decision decide(Observation observation) {
        if (observation.supportConnected()) {
            return decision(Action.DONE, "support already connected");
        }
        if (observation.adbConnected()) {
            return decision(Action.CONNECT_SUPPORT, "ADB already valid; skip Settings/pairing");
        }
        if (observation.androidApi() < MIN_WIRELESS_ADB_API) {
            return decision(Action.UNSUPPORTED_ANDROID, "Android <11 standard wireless path unsupported");
        }
        if (!observation.accessibilityEnabled() && observation.restrictedSettingsRequired()) {
            return decision(Action.ASK_ALLOW_RESTRICTED_SETTINGS, "sideloaded Accessibility is restricted by OS");
        }
        if (!observation.accessibilityEnabled()) {
            return decision(Action.ASK_ENABLE_ACCESSIBILITY, "manual bootstrap prerequisite");
        }
        if (!observation.wifiReady()) {
            return decision(Action.ASK_CONNECT_WIFI, "Wireless Debugging needs a usable Wi-Fi network");
        }
        if (observation.wirelessPolicyBlocked()) {
            return decision(Action.POLICY_BLOCKED, "Wireless Debugging disabled by device/admin policy");
        }
        if (observation.previousPairingKnown() && !observation.reconnectAttempted()) {
            return decision(Action.TRY_ADB_RECONNECT, "reuse previous pairing before opening Settings");
        }
        if (observation.screen() == Screen.CREDENTIAL_PROMPT) {
            return decision(Action.WAIT_USER_CREDENTIAL, "never automate device credential");
        }
        if (observation.screen() == Screen.PAIRING_DIALOG) {
            if (!safeObservation(observation)) {
                return decision(Action.WAIT_STABLE, "pairing dialog not stable/trusted");
            }
            var codes = observation.pairCodeCandidates().stream()
                    .filter(code -> code.length() == 6 && code.chars().allMatch(Character::isDigit))
                    .toList();
            if (observation.requestActive() && observation.pairingContextHigh() && codes.size() == 1) {
                return new Decision(Action.AUTO_PAIR_WITH_CODE, codes.get(0), "unique contextual code");
            }
            return decision(Action.SHOW_MANUAL_PAIR_CODE, "PIN not unambiguous");
        }
        if (observation.screen() == Screen.NETWORK_CONFIRMATION) {
            return safeClick(observation, "network_confirm_positive")
                    ? new Decision(Action.ACCEPT_NETWORK_CONFIRMATION,
                            "network_confirm_positive", "exact trusted confirmation")
                    : decision(Action.FALLBACK_GUIDE, "unsafe confirmation");
        }
        if (observation.screen() == Screen.WIRELESS_DEBUGGING) {
            if (Boolean.FALSE.equals(observation.wirelessEnabled())) {
                return safeClick(observation, "wireless_debugging_toggle")
                        ? new Decision(Action.ENABLE_WIRELESS_DEBUGGING,
                                "wireless_debugging_toggle", "wireless is off")
                        : decision(Action.FALLBACK_GUIDE, "unsafe toggle");
            }
            if (Boolean.TRUE.equals(observation.wirelessEnabled())) {
                return safeClick(observation, "pair_with_code")
                        ? new Decision(Action.CLICK_PAIR_WITH_CODE,
                                "pair_with_code", "wireless already enabled")
                        : decision(Action.FALLBACK_GUIDE, "unsafe pair target");
            }
            return decision(Action.WAIT_STABLE, "wireless state unknown");
        }
        if (observation.screen() == Screen.DEVELOPER_OPTIONS) {
            return safeClick(observation, "wireless_debugging")
                    ? new Decision(Action.CLICK_WIRELESS_DEBUGGING,
                            "wireless_debugging", "developer options available")
                    : decision(Action.FALLBACK_GUIDE, "wireless row unsafe");
        }
        if (observation.screen() == Screen.SOFTWARE_INFO) {
            if (observation.buildTapsDone() < 7) {
                return safeClick(observation, "build_number")
                        ? new Decision(Action.CLICK_BUILD_NUMBER, "build_number",
                                "tap " + (observation.buildTapsDone() + 1) + "/7")
                        : decision(Action.FALLBACK_GUIDE, "build number unsafe");
            }
            return decision(Action.OPEN_DEVELOPER_SETTINGS, "re-probe after seven taps");
        }
        if (observation.screen() == Screen.ABOUT_PHONE) {
            return safeClick(observation, "software_info")
                    ? new Decision(Action.CLICK_SOFTWARE_INFO, "software_info", "Samsung recipe")
                    : decision(Action.FALLBACK_GUIDE, "software info unsafe");
        }
        if (!observation.directDevProbeAttempted()) {
            return decision(Action.OPEN_DEVELOPER_SETTINGS, "fast path");
        }
        if (!observation.directDevScreenRecognized()) {
            return observation.oem().equalsIgnoreCase("Samsung")
                    ? decision(Action.OPEN_DEVICE_INFO_SETTINGS, "Samsung enable-development fallback")
                    : decision(Action.FALLBACK_GUIDE, "OEM recipe unavailable");
        }
        return decision(Action.FALLBACK_GUIDE, "unrecognized state");
    }

    private boolean safeObservation(Observation observation) {
        return observation.authority() != null && observation.authority().safe();
    }

    private boolean safeClick(Observation observation, String key) {
        return safeObservation(observation)
                && observation.candidate() != null
                && key.equals(observation.candidate().key())
                && observation.candidate().safeToClick();
    }

    private Decision decision(Action action, String reason) {
        return new Decision(action, null, reason);
    }
}
