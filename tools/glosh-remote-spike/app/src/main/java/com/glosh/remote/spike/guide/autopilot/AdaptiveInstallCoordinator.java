package com.glosh.remote.spike.guide.autopilot;

import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import com.glosh.remote.spike.BuildConfig;
import com.glosh.remote.spike.broker.SupportSessionCoordinator;
import com.glosh.remote.spike.guide.accessibility.ScanGenerationGuard;
import com.glosh.remote.spike.guide.accessibility.SettingsSnapshot;
import com.glosh.remote.spike.guide.state.GuideStage;
import com.glosh.remote.spike.guide.state.LiveGuideRuntime;
import com.glosh.remote.spike.wizard.OnboardingState;
import com.glosh.remote.spike.wizard.SettingsRoute;

/**
 * Direct-route guide coordinator.
 *
 * <p>Glosh opens the narrowest Settings destination and observes the result. The customer performs
 * the protected taps and switches. Accessibility is retained only for state detection, highlighting
 * and the Samsung enable-development fallback; this class never clicks or scrolls Settings.</p>
 */
public final class AdaptiveInstallCoordinator {
    public interface Host {
        void openSettings(String action);
        void startSupportStackIfReady();
        void submitPairingCode(String code);
        void showManualPairingFallback();
        void showRecovery(String message);
        default void showInstruction(String message) {
            showRecovery(message);
        }
        void clearVisuals();
        void invalidateAndRescan();
        void rescanAfter(long delayMs);
    }

    private static final long FALLBACK_DELAY_MS = 1_400L;

    private final Context context;
    private final Handler handler;
    private final ScanGenerationGuard generationGuard;
    private final Host host;
    private final SamsungSettingsClassifier classifier = new SamsungSettingsClassifier();

    private long probeStartedAtMs;
    private String lastDeveloperFingerprint = "";
    private boolean aboutFallbackScheduled;

    public AdaptiveInstallCoordinator(
            Context context,
            Handler handler,
            ScanGenerationGuard generationGuard,
            Host host) {
        this.context = context.getApplicationContext();
        this.handler = handler;
        this.generationGuard = generationGuard;
        this.host = host;
    }

    public boolean handles(GuideStage stage) {
        return stage == GuideStage.AUTOPILOT_PROBE
                || stage == GuideStage.AUTOPILOT_CREDENTIAL
                || stage == GuideStage.WIRELESS_DEBUGGING;
    }

    public void onStageChanged(GuideStage stage, boolean active) {
        if (!active) {
            return;
        }
        if (stage == GuideStage.AUTOPILOT_PROBE) {
            resetProbe();
            host.showInstruction("Si están apagadas, activá las opciones de desarrollador.");
            host.openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
            scheduleAboutFallback();
            return;
        }
        if (stage == GuideStage.AUTOPILOT_CREDENTIAL) {
            host.showInstruction("Ingresá el PIN, patrón o contraseña que Android te pide.");
            return;
        }
        if (stage == GuideStage.WIRELESS_DEBUGGING) {
            lastDeveloperFingerprint = "";
            host.startSupportStackIfReady();
            host.showInstruction("Activá Depuración inalámbrica. Glosh detectará el cambio.");
            host.openSettings(SettingsRoute.WIRELESS_DEBUGGING);
        }
    }

    public void onNoTrustedWindow(long generation) {
        GuideStage stage = LiveGuideRuntime.stage();
        if (stage == GuideStage.AUTOPILOT_PROBE) {
            handler.postDelayed(() -> {
                if (generationGuard.isGenerationCurrent(generation)
                        && LiveGuideRuntime.stage() == GuideStage.AUTOPILOT_PROBE) {
                    openSamsungDevelopmentFallback();
                }
            }, FALLBACK_DELAY_MS);
        } else if (stage == GuideStage.WIRELESS_DEBUGGING) {
            host.showRecovery("No encuentro Depuración inalámbrica. Volvé a Glosh para abrirla otra vez.");
        }
    }

    public void onStableSnapshot(
            ScanGenerationGuard.Token token,
            SettingsSnapshot snapshot) {
        GuideStage stage = LiveGuideRuntime.stage();
        AutopilotUiModel.ClassifiedScreen classified = classifier.classify(snapshot);
        debug("guided stage=" + stage
                + " screen=" + classified.screen()
                + " wireless=" + classified.wirelessEnabled());

        if (classified.screen() == AutopilotContract.Screen.CREDENTIAL_PROMPT) {
            LiveGuideRuntime.setStage(GuideStage.AUTOPILOT_CREDENTIAL);
            host.showInstruction("Ingresá el PIN, patrón o contraseña que Android te pide.");
            return;
        }
        if (stage == GuideStage.AUTOPILOT_CREDENTIAL) {
            LiveGuideRuntime.setStage(GuideStage.AUTOPILOT_PROBE);
            return;
        }
        if (stage == GuideStage.AUTOPILOT_PROBE) {
            handleDeveloperProbe(snapshot, classified);
        } else if (stage == GuideStage.WIRELESS_DEBUGGING) {
            handleWirelessDebugging(snapshot, classified);
        }
    }

    private void handleDeveloperProbe(
            SettingsSnapshot snapshot,
            AutopilotUiModel.ClassifiedScreen classified) {
        SupportSessionCoordinator support = SupportSessionCoordinator.get(context);
        if (classified.screen() == AutopilotContract.Screen.WIRELESS_DEBUGGING) {
            aboutFallbackScheduled = false;
            if (support.step() == OnboardingState.Step.DEVELOPER_OPTIONS) {
                support.confirmDeveloperOptions();
            } else {
                LiveGuideRuntime.setStage(GuideStage.WIRELESS_DEBUGGING);
            }
            return;
        }
        if (classified.screen() == AutopilotContract.Screen.DEVELOPER_OPTIONS) {
            aboutFallbackScheduled = false;
            host.showInstruction("Si están apagadas, activá las opciones de desarrollador.");
            openWirelessWhenStateChanges(snapshot);
            return;
        }
        if (SystemClock.elapsedRealtime() - probeStartedAtMs >= FALLBACK_DELAY_MS) {
            openSamsungDevelopmentFallback();
        }
    }

    private void handleWirelessDebugging(
            SettingsSnapshot snapshot,
            AutopilotUiModel.ClassifiedScreen classified) {
        host.startSupportStackIfReady();
        switch (classified.screen()) {
            case NETWORK_CONFIRMATION ->
                    host.showInstruction("Tocá “Permitir” para usar esta red Wi‑Fi.");
            case DEVELOPER_OPTIONS -> {
                host.showInstruction("Activá las opciones de desarrollador si están apagadas.");
                openWirelessWhenStateChanges(snapshot);
            }
            case WIRELESS_DEBUGGING -> {
                if (Boolean.TRUE.equals(classified.wirelessEnabled())) {
                    host.clearVisuals();
                    LiveGuideRuntime.setStage(GuideStage.PAIR_CODE_TARGET);
                    host.rescanAfter(180L);
                } else {
                    host.showInstruction(
                            "Activá Depuración inalámbrica. Glosh seguirá automáticamente.");
                }
            }
            case PAIRING_DIALOG -> {
                LiveGuideRuntime.setStage(GuideStage.PAIR_CODE_TARGET);
                host.rescanAfter(100L);
            }
            default -> host.showInstruction(
                    "Glosh abrió la pantalla correcta. Seguí la indicación de la notificación.");
        }
    }

    private void openWirelessWhenStateChanges(SettingsSnapshot snapshot) {
        String fingerprint = snapshot.fingerprint() == null ? "" : snapshot.fingerprint();
        if (fingerprint.equals(lastDeveloperFingerprint)) {
            return;
        }
        lastDeveloperFingerprint = fingerprint;
        host.openSettings(SettingsRoute.WIRELESS_DEBUGGING);
    }

    private void scheduleAboutFallback() {
        if (aboutFallbackScheduled) {
            return;
        }
        aboutFallbackScheduled = true;
        handler.postDelayed(() -> {
            if (aboutFallbackScheduled
                    && LiveGuideRuntime.stage() == GuideStage.AUTOPILOT_PROBE) {
                openSamsungDevelopmentFallback();
            }
        }, FALLBACK_DELAY_MS);
    }

    private void openSamsungDevelopmentFallback() {
        if (LiveGuideRuntime.stage() != GuideStage.AUTOPILOT_PROBE) {
            return;
        }
        aboutFallbackScheduled = false;
        LiveGuideRuntime.setStage(GuideStage.DEV_ABOUT_PHONE);
        host.openSettings(Settings.ACTION_DEVICE_INFO_SETTINGS);
    }

    private void resetProbe() {
        probeStartedAtMs = SystemClock.elapsedRealtime();
        lastDeveloperFingerprint = "";
        aboutFallbackScheduled = false;
    }

    private void debug(String message) {
        if (BuildConfig.DEBUG) {
            Log.d("GloshGuidedInstall", message);
        }
    }
}
