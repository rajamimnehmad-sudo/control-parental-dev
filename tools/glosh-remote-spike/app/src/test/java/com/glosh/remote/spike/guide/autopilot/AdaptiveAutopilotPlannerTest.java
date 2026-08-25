package com.glosh.remote.spike.guide.autopilot;

import static org.junit.Assert.assertEquals;

import com.glosh.remote.spike.guide.autopilot.AutopilotContract.Action;
import com.glosh.remote.spike.guide.autopilot.AutopilotContract.Candidate;
import com.glosh.remote.spike.guide.autopilot.AutopilotContract.Confidence;
import com.glosh.remote.spike.guide.autopilot.AutopilotContract.Observation;
import com.glosh.remote.spike.guide.autopilot.AutopilotContract.Screen;
import com.glosh.remote.spike.guide.autopilot.AutopilotContract.SnapshotAuthority;

import org.junit.Test;

import java.util.List;

public class AdaptiveAutopilotPlannerTest {
    private static final SnapshotAuthority SAFE = new SnapshotAuthority(
            true, 2, true, true, true, false);
    private final AdaptiveAutopilotPlanner planner = new AdaptiveAutopilotPlanner();

    @Test
    public void referencePlannerCoverage() {
        int checks = 0;
        checks += expect(Action.DONE, base().supportConnected(true).build());
        checks += expect(Action.CONNECT_SUPPORT, base().adbConnected(true).build());
        checks += expect(Action.UNSUPPORTED_ANDROID, base().androidApi(29).build());
        checks += expect(Action.ASK_ENABLE_ACCESSIBILITY, base().accessibilityEnabled(false).build());
        checks += expect(Action.ASK_ALLOW_RESTRICTED_SETTINGS,
                base().accessibilityEnabled(false).restrictedSettingsRequired(true).build());
        checks += expect(Action.ASK_CONNECT_WIFI, base().wifiReady(false).build());
        checks += expect(Action.POLICY_BLOCKED, base().wirelessPolicyBlocked(true).build());
        checks += expect(Action.TRY_ADB_RECONNECT,
                base().previousPairingKnown(true).reconnectAttempted(false).build());
        checks += expect(Action.OPEN_DEVELOPER_SETTINGS,
                base().previousPairingKnown(true).reconnectAttempted(true).screen(Screen.APP).build());
        checks += expect(Action.OPEN_DEVELOPER_SETTINGS, base().screen(Screen.APP).build());
        checks += expect(Action.OPEN_DEVICE_INFO_SETTINGS,
                base().screen(Screen.SETTINGS_HOME).directDevProbeAttempted(true).build());
        checks += expect(Action.FALLBACK_GUIDE,
                base().oem("Xiaomi").screen(Screen.SETTINGS_HOME).directDevProbeAttempted(true).build());
        checks += expect(Action.CLICK_SOFTWARE_INFO,
                base().screen(Screen.ABOUT_PHONE).candidate(candidate("software_info")).build());
        for (int tap = 0; tap < 7; tap++) {
            checks += expect(Action.CLICK_BUILD_NUMBER,
                    base().screen(Screen.SOFTWARE_INFO).candidate(candidate("build_number"))
                            .buildTapsDone(tap).build());
        }
        checks += expect(Action.OPEN_DEVELOPER_SETTINGS,
                base().screen(Screen.SOFTWARE_INFO).candidate(candidate("build_number"))
                        .buildTapsDone(7).build());
        checks += expect(Action.WAIT_USER_CREDENTIAL, base().screen(Screen.CREDENTIAL_PROMPT).build());
        checks += expect(Action.CLICK_WIRELESS_DEBUGGING,
                base().screen(Screen.DEVELOPER_OPTIONS).candidate(candidate("wireless_debugging")).build());
        checks += expect(Action.ENABLE_WIRELESS_DEBUGGING,
                base().screen(Screen.WIRELESS_DEBUGGING)
                        .candidate(candidate("wireless_debugging_toggle")).wirelessEnabled(false).build());
        checks += expect(Action.CLICK_PAIR_WITH_CODE,
                base().screen(Screen.WIRELESS_DEBUGGING)
                        .candidate(candidate("pair_with_code")).wirelessEnabled(true).build());
        checks += expect(Action.WAIT_STABLE,
                base().screen(Screen.WIRELESS_DEBUGGING).candidate(candidate("pair_with_code")).build());
        checks += expect(Action.ACCEPT_NETWORK_CONFIRMATION,
                base().screen(Screen.NETWORK_CONFIRMATION)
                        .candidate(candidate("network_confirm_positive")).build());
        checks += expect(Action.AUTO_PAIR_WITH_CODE,
                base().screen(Screen.PAIRING_DIALOG).pairCodeCandidates(List.of("123456"))
                        .pairingContextHigh(true).build());
        checks += expect(Action.SHOW_MANUAL_PAIR_CODE,
                base().screen(Screen.PAIRING_DIALOG).pairCodeCandidates(List.of("123456", "654321"))
                        .pairingContextHigh(true).build());
        checks += expect(Action.SHOW_MANUAL_PAIR_CODE,
                base().screen(Screen.PAIRING_DIALOG).pairCodeCandidates(List.of("123456"))
                        .pairingContextHigh(false).build());
        checks += expect(Action.SHOW_MANUAL_PAIR_CODE,
                base().screen(Screen.PAIRING_DIALOG).pairCodeCandidates(List.of("123456"))
                        .pairingContextHigh(true).requestActive(false).build());
        checks += expect(Action.WAIT_STABLE,
                base().screen(Screen.PAIRING_DIALOG)
                        .authority(new SnapshotAuthority(true, 1, true, true, true, false))
                        .pairCodeCandidates(List.of("123456")).pairingContextHigh(true).build());
        checks += unsafe(new SnapshotAuthority(true, 1, true, true, true, false), candidate("software_info"));
        checks += unsafe(new SnapshotAuthority(false, 2, true, true, true, false), candidate("software_info"));
        checks += unsafe(new SnapshotAuthority(true, 2, true, true, true, true), candidate("software_info"));
        checks += unsafe(new SnapshotAuthority(true, 2, false, true, true, false), candidate("software_info"));
        checks += unsafe(new SnapshotAuthority(true, 2, true, false, true, false), candidate("software_info"));
        checks += unsafe(new SnapshotAuthority(true, 2, true, true, false, false), candidate("software_info"));
        checks += unsafe(SAFE, candidate("software_info", Confidence.MEDIUM, true, true, true, true));
        checks += unsafe(SAFE, candidate("software_info", Confidence.HIGH, true, false, true, true));
        checks += unsafe(SAFE, candidate("software_info", Confidence.HIGH, true, true, false, true));
        checks += unsafe(SAFE, candidate("software_info", Confidence.HIGH, true, true, true, false));
        checks += unsafe(SAFE, candidate("software_info", Confidence.HIGH, false, true, true, true));
        assertEquals(43, checks);
    }

    private int unsafe(SnapshotAuthority authority, Candidate candidate) {
        return expect(Action.FALLBACK_GUIDE,
                base().screen(Screen.ABOUT_PHONE).authority(authority).candidate(candidate).build());
    }

    private int expect(Action expected, Observation observation) {
        assertEquals(expected, planner.decide(observation).action());
        return 1;
    }

    private AutopilotContract.Builder base() {
        return Observation.builder().authority(SAFE);
    }

    private Candidate candidate(String key) {
        return candidate(key, Confidence.HIGH, true, true, true, true);
    }

    private Candidate candidate(
            String key, Confidence confidence, boolean clickable,
            boolean unique, boolean margin, boolean fresh) {
        return new Candidate(key, confidence, clickable, unique, margin, fresh);
    }
}
