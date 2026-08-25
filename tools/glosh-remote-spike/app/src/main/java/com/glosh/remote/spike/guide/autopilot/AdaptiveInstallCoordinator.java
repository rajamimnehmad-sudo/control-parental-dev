package com.glosh.remote.spike.guide.autopilot;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import com.glosh.remote.spike.BuildConfig;
import com.glosh.remote.spike.RemotePairingService;
import com.glosh.remote.spike.broker.SupportSessionCoordinator;
import com.glosh.remote.spike.guide.accessibility.GuideActionPolicy;
import com.glosh.remote.spike.guide.accessibility.ScanGenerationGuard;
import com.glosh.remote.spike.guide.accessibility.SettingsSnapshot;
import com.glosh.remote.spike.guide.state.GuideStage;
import com.glosh.remote.spike.guide.state.LiveGuideRuntime;
import com.glosh.remote.spike.session.PairingUiState;
import com.glosh.remote.spike.session.SessionState;
import com.glosh.remote.spike.wizard.OnboardingState;

import java.util.EnumSet;
import java.util.List;

/**
 * Serial decision owner for the Samsung adaptive installer.
 *
 * <p>The accessibility service owns Android lifecycle and stable snapshots. This class owns only
 * the pure planner binding and the one-click transaction sequence. It never chains clicks: every
 * dispatched action invalidates the scan authority and waits for a new stable snapshot.</p>
 */
public final class AdaptiveInstallCoordinator {
    public interface Host {
        void openSettings(String action);
        void startSupportStackIfReady();
        void submitPairingCode(String code);
        void showManualPairingFallback();
        void showRecovery(String message);
        void clearVisuals();
        void invalidateAndRescan();
        void rescanAfter(long delayMs);
    }

    private static final long SCREEN_TRANSITION_TIMEOUT_MS = 2_500L;

    private final Context context;
    private final Handler handler;
    private final ScanGenerationGuard generationGuard;
    private final Host host;
    private final AdaptiveAutopilotPlanner planner = new AdaptiveAutopilotPlanner();
    private final SamsungSettingsClassifier classifier = new SamsungSettingsClassifier();
    private final ContextualPairingCodeDetector codeDetector =
            new ContextualPairingCodeDetector();
    private final AutopilotCapabilityProbe capabilityProbe = new AutopilotCapabilityProbe();
    private final FreshNodeClickExecutor clickExecutor;
    private final FreshSettingsScrollExecutor scrollExecutor;

    private boolean stopped;
    private boolean terminalWaiting;
    private int buildTapCount;
    private AutopilotContract.Screen scrollScreen = AutopilotContract.Screen.UNKNOWN;
    private int scrollAttempts;
    private final AutopilotTransitionGuard transitionGuard =
            new AutopilotTransitionGuard(SCREEN_TRANSITION_TIMEOUT_MS);

    public AdaptiveInstallCoordinator(
            Context context,
            Handler handler,
            ScanGenerationGuard generationGuard,
            FreshNodeClickExecutor clickExecutor,
            FreshSettingsScrollExecutor scrollExecutor,
            Host host) {
        this.context = context.getApplicationContext();
        this.handler = handler;
        this.generationGuard = generationGuard;
        this.clickExecutor = clickExecutor;
        this.scrollExecutor = scrollExecutor;
        this.host = host;
    }

    public boolean handles(GuideStage stage) {
        return stage == GuideStage.AUTOPILOT_PROBE
                || stage == GuideStage.AUTOPILOT_CREDENTIAL
                || stage == GuideStage.AUTOPILOT_FALLBACK
                || stage == GuideStage.DEV_ABOUT_PHONE
                || stage == GuideStage.DEV_SOFTWARE_INFO
                || stage == GuideStage.DEV_BUILD_NUMBER
                || stage == GuideStage.WIRELESS_DEBUGGING
                || stage == GuideStage.PAIR_CODE_TARGET;
    }

    public void onStageChanged(GuideStage stage, boolean active) {
        if (!active) {
            return;
        }
        if (stage == GuideStage.AUTOPILOT_PROBE) {
            stopped = false;
            terminalWaiting = false;
            transitionGuard.clear();
            scrollScreen = AutopilotContract.Screen.UNKNOWN;
            scrollAttempts = 0;
            host.openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
        } else if (stage == GuideStage.WIRELESS_DEBUGGING) {
            host.startSupportStackIfReady();
            host.openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
        }
    }

    public void onNoTrustedWindow(long generation) {
        if (LiveGuideRuntime.stage() != GuideStage.AUTOPILOT_PROBE) {
            return;
        }
        handler.postDelayed(() -> {
            if (generationGuard.isGenerationCurrent(generation)
                    && LiveGuideRuntime.stage() == GuideStage.AUTOPILOT_PROBE) {
                LiveGuideRuntime.setStage(GuideStage.DEV_ABOUT_PHONE);
                host.openSettings(Settings.ACTION_DEVICE_INFO_SETTINGS);
            }
        }, 1_200L);
    }

    public void onStableSnapshot(
            ScanGenerationGuard.Token token,
            SettingsSnapshot snapshot) {
        if (terminalWaiting) {
            return;
        }
        if (stopped) {
            host.showRecovery("No pude reconocer esta pantalla con seguridad. Continuá desde Glosh.");
            return;
        }
        AutopilotUiModel.ClassifiedScreen classified = classifier.classify(snapshot);
        AutopilotContract.Screen screen = classified.screen();
        if (!acceptExpectedScreen(classified)) {
            return;
        }

        if (screen == AutopilotContract.Screen.CREDENTIAL_PROMPT) {
            LiveGuideRuntime.setStage(GuideStage.AUTOPILOT_CREDENTIAL);
            host.showRecovery("Confirmá el PIN, patrón o contraseña del teléfono para continuar.");
            return;
        }
        if (LiveGuideRuntime.stage() == GuideStage.AUTOPILOT_CREDENTIAL) {
            LiveGuideRuntime.setStage(GuideStage.AUTOPILOT_PROBE);
            return;
        }

        SupportSessionCoordinator support = SupportSessionCoordinator.get(context);
        SessionState session = RemotePairingService.getSessionState();
        if (screen == AutopilotContract.Screen.DEVELOPER_OPTIONS
                && session == SessionState.IDLE
                && support.step() == OnboardingState.Step.DEVELOPER_OPTIONS) {
            host.clearVisuals();
            support.confirmDeveloperOptions();
            return;
        }
        if (support.step() == OnboardingState.Step.REQUESTING_SUPPORT) {
            return;
        }
        if (support.step() == OnboardingState.Step.WIRELESS_DEBUGGING
                && session == SessionState.IDLE) {
            host.startSupportStackIfReady();
            return;
        }

        AutopilotContract.Decision decision = planner.decide(observation(
                classified, snapshot, screen, session));
        debug("decision screen=" + screen + " action=" + decision.action());
        if (decision.action() == AutopilotContract.Action.AUTO_PAIR_WITH_CODE
                && !pairingEndpointReady()) {
            host.rescanAfter(250L);
            return;
        }
        execute(token, snapshot, decision);
    }

    private boolean acceptExpectedScreen(AutopilotUiModel.ClassifiedScreen classified) {
        AutopilotTransitionGuard.Result result = transitionGuard.evaluate(
                classified.screen(), classified.wirelessEnabled(), SystemClock.elapsedRealtime());
        if (result == AutopilotTransitionGuard.Result.REJECT) {
            stop("La pantalla cambió de una forma inesperada.");
        }
        return result == AutopilotTransitionGuard.Result.ACCEPT;
    }

    private AutopilotContract.Observation observation(
            AutopilotUiModel.ClassifiedScreen classified,
            SettingsSnapshot snapshot,
            AutopilotContract.Screen screen,
            SessionState session) {
        AutopilotUiModel.TargetKey targetKey = targetFor(classified);
        AutopilotUiModel.MatchedTarget matched = targetKey == null
                ? null
                : classified.target(targetKey);
        AutopilotContract.Candidate candidate = matched == null
                ? null
                : new AutopilotContract.Candidate(
                        matched.key().plannerKey(),
                        matched.confidence(),
                        matched.clickable(),
                        matched.unique(),
                        matched.marginOk(),
                        true);

        ContextualPairingCodeDetector.Result detected = codeDetector.detect(snapshot, screen);
        List<String> codes = detected instanceof ContextualPairingCodeDetector.Unique unique
                ? List.of(unique.code())
                : List.of();
        boolean contextualCode = detected instanceof ContextualPairingCodeDetector.Unique;

        return AutopilotContract.Observation.builder()
                .androidApi(Build.VERSION.SDK_INT)
                .oem(Build.MANUFACTURER)
                .accessibilityEnabled(true)
                .supportConnected(capabilityProbe.supportConnected())
                .wifiReady(capabilityProbe.wifiReady(context))
                .wirelessPolicyBlocked(classified.policyBlocked())
                .screen(screen)
                .authority(new AutopilotContract.SnapshotAuthority(
                        true, 2, true, true, true, false))
                .candidate(candidate)
                .wirelessEnabled(classified.wirelessEnabled())
                .buildTapsDone(buildTapCount)
                .pairCodeCandidates(codes)
                .pairingContextHigh(contextualCode)
                .requestActive(session == SessionState.PREPARING)
                .directDevProbeAttempted(true)
                .directDevScreenRecognized(
                        screen == AutopilotContract.Screen.DEVELOPER_OPTIONS
                                || screen == AutopilotContract.Screen.WIRELESS_DEBUGGING
                                || screen == AutopilotContract.Screen.PAIRING_DIALOG)
                .build();
    }

    private void execute(
            ScanGenerationGuard.Token token,
            SettingsSnapshot snapshot,
            AutopilotContract.Decision decision) {
        switch (decision.action()) {
            case DONE, CONNECT_SUPPORT, WAIT_STABLE -> {
                // Existing connection components own terminal and wait states.
            }
            case OPEN_DEVELOPER_SETTINGS -> {
                LiveGuideRuntime.setStage(GuideStage.AUTOPILOT_PROBE);
            }
            case OPEN_DEVICE_INFO_SETTINGS -> {
                LiveGuideRuntime.setStage(GuideStage.DEV_ABOUT_PHONE);
                host.openSettings(Settings.ACTION_DEVICE_INFO_SETTINGS);
            }
            case CLICK_SOFTWARE_INFO -> click(
                    token, snapshot, AutopilotUiModel.TargetKey.SOFTWARE_INFO,
                    EnumSet.of(AutopilotContract.Screen.SOFTWARE_INFO), false);
            case CLICK_BUILD_NUMBER -> click(
                    token, snapshot, AutopilotUiModel.TargetKey.BUILD_NUMBER,
                    EnumSet.of(
                            AutopilotContract.Screen.SOFTWARE_INFO,
                            AutopilotContract.Screen.CREDENTIAL_PROMPT,
                            AutopilotContract.Screen.DEVELOPER_OPTIONS), true);
            case CLICK_WIRELESS_DEBUGGING -> click(
                    token, snapshot, AutopilotUiModel.TargetKey.WIRELESS_DEBUGGING,
                    EnumSet.of(AutopilotContract.Screen.WIRELESS_DEBUGGING), false);
            case ENABLE_WIRELESS_DEBUGGING -> click(
                    token, snapshot, AutopilotUiModel.TargetKey.WIRELESS_DEBUGGING_TOGGLE,
                    EnumSet.of(
                            AutopilotContract.Screen.WIRELESS_DEBUGGING,
                            AutopilotContract.Screen.NETWORK_CONFIRMATION), false, true);
            case ACCEPT_NETWORK_CONFIRMATION -> click(
                    token, snapshot, AutopilotUiModel.TargetKey.NETWORK_CONFIRM_POSITIVE,
                    EnumSet.of(AutopilotContract.Screen.WIRELESS_DEBUGGING), false);
            case CLICK_PAIR_WITH_CODE -> click(
                    token, snapshot, AutopilotUiModel.TargetKey.PAIR_WITH_CODE,
                    EnumSet.of(AutopilotContract.Screen.PAIRING_DIALOG), false);
            case AUTO_PAIR_WITH_CODE -> {
                terminalWaiting = true;
                LiveGuideRuntime.setStage(GuideStage.PAIR_CODE_TARGET);
                host.submitPairingCode(decision.target());
            }
            case SHOW_MANUAL_PAIR_CODE -> {
                terminalWaiting = true;
                LiveGuideRuntime.setStage(GuideStage.AUTOPILOT_FALLBACK);
                host.showManualPairingFallback();
            }
            case WAIT_USER_CREDENTIAL -> {
                LiveGuideRuntime.setStage(GuideStage.AUTOPILOT_CREDENTIAL);
                host.showRecovery("Confirmá el PIN, patrón o contraseña del teléfono para continuar.");
            }
            case ASK_CONNECT_WIFI -> stop("Conectate a una red Wi-Fi para continuar.");
            case POLICY_BLOCKED -> stop(
                    "La depuración inalámbrica está bloqueada por una política del teléfono.");
            case UNSUPPORTED_ANDROID -> stop("Este teléfono necesita Android 11 o superior.");
            case FALLBACK_GUIDE -> {
                if (!tryRevealKnownTarget(token, snapshot)) {
                    stop("No pude continuar automáticamente desde esta pantalla.");
                }
            }
            case ASK_ALLOW_RESTRICTED_SETTINGS, ASK_ENABLE_ACCESSIBILITY, TRY_ADB_RECONNECT -> stop(
                            "No pude continuar automáticamente desde esta pantalla.");
        }
    }

    private void click(
            ScanGenerationGuard.Token token,
            SettingsSnapshot snapshot,
            AutopilotUiModel.TargetKey target,
            EnumSet<AutopilotContract.Screen> expected,
            boolean buildTap) {
        click(token, snapshot, target, expected, buildTap, null);
    }

    private void click(
            ScanGenerationGuard.Token token,
            SettingsSnapshot snapshot,
            AutopilotUiModel.TargetKey target,
            EnumSet<AutopilotContract.Screen> expected,
            boolean buildTap,
            Boolean expectedWirelessState) {
        if (!GuideActionPolicy.isAllowed(GuideActionPolicy.Operation.CLICK)) {
            stop("La acción automática no está autorizada.");
            return;
        }
        FreshNodeClickExecutor.Result result = clickExecutor.click(
                token, snapshot, target, LiveGuideRuntime.settingsPackages());
        debug("click target=" + target + " result=" + result);
        if (result != FreshNodeClickExecutor.Result.ACTION_DISPATCHED) {
            stop("La pantalla cambió antes de poder continuar.");
            return;
        }
        if (buildTap) {
            buildTapCount = Math.min(7, buildTapCount + 1);
        }
        scrollScreen = AutopilotContract.Screen.UNKNOWN;
        scrollAttempts = 0;
        transitionGuard.expect(expected, expectedWirelessState, SystemClock.elapsedRealtime());
        host.invalidateAndRescan();
    }

    private boolean pairingEndpointReady() {
        PairingUiState state = RemotePairingService.getPairingUiState();
        return state == PairingUiState.WAITING_FOR_CODE || state == PairingUiState.CODE_FAILED;
    }

    private boolean tryRevealKnownTarget(
            ScanGenerationGuard.Token token,
            SettingsSnapshot snapshot) {
        AutopilotUiModel.ClassifiedScreen classified = classifier.classify(snapshot);
        AutopilotUiModel.TargetKey expectedTarget = targetFor(classified);
        AutopilotContract.Screen screen = classified.screen();
        if (expectedTarget == null
                || classified.target(expectedTarget) != null
                || (screen != AutopilotContract.Screen.ABOUT_PHONE
                && screen != AutopilotContract.Screen.SOFTWARE_INFO
                && screen != AutopilotContract.Screen.DEVELOPER_OPTIONS
                && screen != AutopilotContract.Screen.WIRELESS_DEBUGGING)) {
            return false;
        }
        if (screen == AutopilotContract.Screen.DEVELOPER_OPTIONS
                && classifier.hasVisibleWirelessDebuggingLabel(snapshot)) {
            debug("wireless label visible but no safe navigable row; refusing scroll");
            return false;
        }
        if (scrollScreen != screen) {
            scrollScreen = screen;
            scrollAttempts = 0;
        }
        if (scrollAttempts >= 3) {
            return false;
        }
        FreshSettingsScrollExecutor.Result result = scrollExecutor.scrollForward(
                token, snapshot, screen, LiveGuideRuntime.settingsPackages());
        debug("scroll screen=" + screen + " attempt=" + (scrollAttempts + 1)
                + " result=" + result);
        if (result != FreshSettingsScrollExecutor.Result.SCROLLED) {
            return false;
        }
        scrollAttempts++;
        host.invalidateAndRescan();
        return true;
    }

    private AutopilotUiModel.TargetKey targetFor(
            AutopilotUiModel.ClassifiedScreen classified) {
        return switch (classified.screen()) {
            case ABOUT_PHONE -> AutopilotUiModel.TargetKey.SOFTWARE_INFO;
            case SOFTWARE_INFO -> AutopilotUiModel.TargetKey.BUILD_NUMBER;
            case DEVELOPER_OPTIONS -> AutopilotUiModel.TargetKey.WIRELESS_DEBUGGING;
            case WIRELESS_DEBUGGING -> Boolean.FALSE.equals(classified.wirelessEnabled())
                    ? AutopilotUiModel.TargetKey.WIRELESS_DEBUGGING_TOGGLE
                    : AutopilotUiModel.TargetKey.PAIR_WITH_CODE;
            case NETWORK_CONFIRMATION -> AutopilotUiModel.TargetKey.NETWORK_CONFIRM_POSITIVE;
            default -> null;
        };
    }

    private void stop(String message) {
        stopped = true;
        transitionGuard.clear();
        LiveGuideRuntime.setStage(GuideStage.AUTOPILOT_FALLBACK);
        host.showRecovery(message);
    }

    private void debug(String message) {
        if (BuildConfig.DEBUG) {
            Log.d("GloshAutopilot", message);
        }
    }
}
