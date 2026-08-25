package com.glosh.remote.spike.guide.autopilot;

import java.util.List;

public final class AutopilotContract {
    public enum Confidence { LOW, MEDIUM, HIGH }

    public enum Screen {
        UNKNOWN,
        APP,
        SETTINGS_HOME,
        ABOUT_PHONE,
        SOFTWARE_INFO,
        DEVELOPER_OPTIONS,
        WIRELESS_DEBUGGING,
        NETWORK_CONFIRMATION,
        PAIRING_DIALOG,
        CREDENTIAL_PROMPT
    }

    public enum Action {
        WAIT_STABLE,
        ASK_ALLOW_RESTRICTED_SETTINGS,
        ASK_ENABLE_ACCESSIBILITY,
        ASK_CONNECT_WIFI,
        TRY_ADB_RECONNECT,
        POLICY_BLOCKED,
        UNSUPPORTED_ANDROID,
        OPEN_DEVELOPER_SETTINGS,
        OPEN_DEVICE_INFO_SETTINGS,
        CLICK_SOFTWARE_INFO,
        CLICK_BUILD_NUMBER,
        WAIT_USER_CREDENTIAL,
        CLICK_WIRELESS_DEBUGGING,
        ENABLE_WIRELESS_DEBUGGING,
        ACCEPT_NETWORK_CONFIRMATION,
        CLICK_PAIR_WITH_CODE,
        AUTO_PAIR_WITH_CODE,
        SHOW_MANUAL_PAIR_CODE,
        CONNECT_SUPPORT,
        FALLBACK_GUIDE,
        DONE
    }

    public record SnapshotAuthority(
            boolean trustedSettingsWindow,
            int stableSnapshots,
            boolean generationCurrent,
            boolean windowIdCurrent,
            boolean fingerprintCurrent,
            boolean ambiguousWindow) {
        public boolean safe() {
            return trustedSettingsWindow
                    && stableSnapshots >= 2
                    && generationCurrent
                    && windowIdCurrent
                    && fingerprintCurrent
                    && !ambiguousWindow;
        }
    }

    public record Candidate(
            String key,
            Confidence confidence,
            boolean clickable,
            boolean unique,
            boolean marginOk,
            boolean freshReacquired) {
        public boolean safeToClick() {
            return confidence == Confidence.HIGH
                    && clickable
                    && unique
                    && marginOk
                    && freshReacquired;
        }
    }

    public record Observation(
            int androidApi,
            String oem,
            boolean accessibilityEnabled,
            boolean adbConnected,
            boolean supportConnected,
            boolean restrictedSettingsRequired,
            boolean wifiReady,
            boolean wirelessPolicyBlocked,
            boolean previousPairingKnown,
            boolean reconnectAttempted,
            Screen screen,
            SnapshotAuthority authority,
            Candidate candidate,
            Boolean wirelessEnabled,
            int buildTapsDone,
            List<String> pairCodeCandidates,
            boolean pairingContextHigh,
            boolean requestActive,
            boolean directDevProbeAttempted,
            boolean directDevScreenRecognized) {
        public Observation {
            oem = oem == null ? "" : oem;
            screen = screen == null ? Screen.UNKNOWN : screen;
            pairCodeCandidates = pairCodeCandidates == null
                    ? List.of()
                    : List.copyOf(pairCodeCandidates);
        }

        public static Builder builder() {
            return new Builder();
        }
    }

    public record Decision(Action action, String target, String reason) {
        public Decision(Action action, String reason) {
            this(action, null, reason);
        }
    }

    public static final class Builder {
        private int androidApi = 34;
        private String oem = "Samsung";
        private boolean accessibilityEnabled = true;
        private boolean adbConnected;
        private boolean supportConnected;
        private boolean restrictedSettingsRequired;
        private boolean wifiReady = true;
        private boolean wirelessPolicyBlocked;
        private boolean previousPairingKnown;
        private boolean reconnectAttempted;
        private Screen screen = Screen.UNKNOWN;
        private SnapshotAuthority authority;
        private Candidate candidate;
        private Boolean wirelessEnabled;
        private int buildTapsDone;
        private List<String> pairCodeCandidates = List.of();
        private boolean pairingContextHigh;
        private boolean requestActive = true;
        private boolean directDevProbeAttempted;
        private boolean directDevScreenRecognized;

        public Builder androidApi(int value) { androidApi = value; return this; }
        public Builder oem(String value) { oem = value; return this; }
        public Builder accessibilityEnabled(boolean value) { accessibilityEnabled = value; return this; }
        public Builder adbConnected(boolean value) { adbConnected = value; return this; }
        public Builder supportConnected(boolean value) { supportConnected = value; return this; }
        public Builder restrictedSettingsRequired(boolean value) { restrictedSettingsRequired = value; return this; }
        public Builder wifiReady(boolean value) { wifiReady = value; return this; }
        public Builder wirelessPolicyBlocked(boolean value) { wirelessPolicyBlocked = value; return this; }
        public Builder previousPairingKnown(boolean value) { previousPairingKnown = value; return this; }
        public Builder reconnectAttempted(boolean value) { reconnectAttempted = value; return this; }
        public Builder screen(Screen value) { screen = value; return this; }
        public Builder authority(SnapshotAuthority value) { authority = value; return this; }
        public Builder candidate(Candidate value) { candidate = value; return this; }
        public Builder wirelessEnabled(Boolean value) { wirelessEnabled = value; return this; }
        public Builder buildTapsDone(int value) { buildTapsDone = value; return this; }
        public Builder pairCodeCandidates(List<String> value) { pairCodeCandidates = value; return this; }
        public Builder pairingContextHigh(boolean value) { pairingContextHigh = value; return this; }
        public Builder requestActive(boolean value) { requestActive = value; return this; }
        public Builder directDevProbeAttempted(boolean value) { directDevProbeAttempted = value; return this; }
        public Builder directDevScreenRecognized(boolean value) { directDevScreenRecognized = value; return this; }

        public Observation build() {
            return new Observation(
                    androidApi, oem, accessibilityEnabled, adbConnected, supportConnected,
                    restrictedSettingsRequired, wifiReady, wirelessPolicyBlocked,
                    previousPairingKnown, reconnectAttempted, screen, authority, candidate,
                    wirelessEnabled, buildTapsDone, pairCodeCandidates, pairingContextHigh,
                    requestActive, directDevProbeAttempted, directDevScreenRecognized);
        }
    }

    private AutopilotContract() {
    }
}
