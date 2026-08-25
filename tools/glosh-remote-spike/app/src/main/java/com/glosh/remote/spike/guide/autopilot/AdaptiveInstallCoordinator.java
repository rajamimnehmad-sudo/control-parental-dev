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
 * protected Android taps and switches. This coordinator never clicks or scrolls Settings.</p>
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
        default void showWaiting(String message) {
            showInstruction(message);
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
    private final WirelessDirectRoutePolicy wirelessRoutePolicy = new WirelessDirectRoutePolicy();

    private long probeStartedAtMs;
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
        if (stage == GuideStage.GUIDE_PERMISSION) {
            SupportSessionCoordinator.get(context).guideReady();
            LiveGuideRuntime.setStage(GuideStage.AUTOPILOT_PROBE);
            return;
        }
        if (stage == GuideStage.PAIR_CODE_TARGET || stage == GuideStage.PAIRING) {
            host.startSupportStackIfReady();
            return;
        }
        if (stage == GuideStage.AUTOPILOT_PROBE) {
            resetProbe();
            if (DeveloperOptionsProbe.isEnabled(context)) {
                completeDeveloperOptions();
                return;
            }
            host.showWaiting("Verificando si las opciones de desarrollador ya están activas…");
            host.openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
            scheduleAboutFallback();
            return;
        }
        if (stage == GuideStage.AUTOPILOT_CREDENTIAL) {
            host.showInstruction("Ingresá tu PIN, patrón o contraseña. Glosh nunca lo lee.");
            return;
        }
        if (stage == GuideStage.WIRELESS_DEBUGGING) {
            wirelessRoutePolicy.reset();
            host.startSupportStackIfReady();
            openWirelessDirect();
        }
    }

    public void onNoTrustedWindow(long generation) {
        GuideStage stage = LiveGuideRuntime.stage();
        if (stage == GuideStage.AUTOPILOT_PROBE) {
            handler.postDelayed(() -> {
                if (generationGuard.isGenerationCurrent(generation)
                        && LiveGuideRuntime.stage() == GuideStage.AUTOPILOT_PROBE) {
                    if (DeveloperOptionsProbe.isEnabled(context)) {
                        completeDeveloperOptions();
                    } else {
                        openSamsungDevelopmentFallback();
                    }
                }
            }, FALLBACK_DELAY_MS);
        } else if (stage == GuideStage.WIRELESS_DEBUGGING) {
            host.showRecovery(
                    "No encuentro la pantalla esperada. Volvé a Glosh para abrir Depuración inalámbrica nuevamente.");
        }
    }

    public void onStableSnapshot(
            ScanGenerationGuard.Token token,
            SettingsSnapshot snapshot) {
        GuideStage stage = LiveGuideRuntime.stage();
        AutopilotUiModel.ClassifiedScreen classified = classifier.classify(snapshot);
        debug("guided stage=" + stage
                + " screen=" + classified.screen()
                + " developerEnabled=" + DeveloperOptionsProbe.isEnabled(context)
                + " wireless=" + classified.wirelessEnabled()
                + " directAttempts=" + wirelessRoutePolicy.attempts());

        if (classified.screen() == AutopilotContract.Screen.CREDENTIAL_PROMPT) {
            LiveGuideRuntime.setStage(GuideStage.AUTOPILOT_CREDENTIAL);
            host.showInstruction("Ingresá tu PIN, patrón o contraseña. Glosh nunca lo lee.");
            return;
        }

        if (stage == GuideStage.AUTOPILOT_CREDENTIAL) {
            if (DeveloperOptionsProbe.isEnabled(context)) {
                completeDeveloperOptions();
            } else {
                host.showWaiting("Verificando el cambio…");
                LiveGuideRuntime.setStage(GuideStage.AUTOPILOT_PROBE);
            }
            return;
        }

        if (stage == GuideStage.AUTOPILOT_PROBE) {
            if (DeveloperOptionsProbe.isEnabled(context)) {
                completeDeveloperOptions();
                return;
            }
            handleDeveloperProbe(classified);
        } else if (stage == GuideStage.WIRELESS_DEBUGGING) {
            handleWirelessDebugging(snapshot, classified);
        }
    }

    private void handleDeveloperProbe(AutopilotUiModel.ClassifiedScreen classified) {
        if (classified.screen() == AutopilotContract.Screen.DEVELOPER_OPTIONS) {
            aboutFallbackScheduled = false;
            host.showInstruction(
                    "Activá el interruptor principal de Opciones de desarrollador. Glosh detectará cuando quede listo.");
            return;
        }
        if (classified.screen() == AutopilotContract.Screen.WIRELESS_DEBUGGING) {
            aboutFallbackScheduled = false;
            completeDeveloperOptions();
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
                if (!DeveloperOptionsProbe.isEnabled(context)) {
                    host.showInstruction(
                            "Activá el interruptor principal de Opciones de desarrollador. Glosh esperará acá.");
                } else {
                    handleDirectRouteReturnedToDeveloperOptions(snapshot);
                }
            }
            case WIRELESS_DEBUGGING -> {
                if (Boolean.TRUE.equals(classified.wirelessEnabled())) {
                    host.showWaiting("Depuración inalámbrica activada. Preparando el código…");
                    LiveGuideRuntime.setStage(GuideStage.PAIR_CODE_TARGET);
                    host.rescanAfter(180L);
                } else {
                    host.showInstruction(
                            "Activá Depuración inalámbrica. Glosh seguirá automáticamente.");
                }
            }
            case PAIRING_DIALOG -> {
                host.showWaiting("Leyendo el código de vinculación…");
                LiveGuideRuntime.setStage(GuideStage.PAIR_CODE_TARGET);
                host.rescanAfter(100L);
            }
            default -> host.showWaiting("Verificando la pantalla…");
        }
    }

    private void handleDirectRouteReturnedToDeveloperOptions(SettingsSnapshot snapshot) {
        WirelessDirectRoutePolicy.Decision decision =
                wirelessRoutePolicy.onDeveloperOptions(snapshot.fingerprint());
        debug("wireless direct fallback=" + decision
                + " fingerprint=" + snapshot.fingerprint());
        switch (decision) {
            case WAIT_FOR_USER -> host.showInstruction(
                    "Samsung no abrió Depuración inalámbrica directamente. Buscá y tocá “Depuración inalámbrica” en esta lista. Glosh seguirá solo.");
            case RETRY_DIRECT_ONCE -> {
                host.showWaiting(
                        "Detecté un cambio. Vuelvo a intentar abrir Depuración inalámbrica una sola vez…");
                openWirelessDirect();
            }
            case VISUAL_FALLBACK -> host.showInstruction(
                    "Buscá y tocá “Depuración inalámbrica” en esta lista. Glosh no volverá a relanzar esta pantalla ni hará scroll por vos.");
        }
    }

    private void openWirelessDirect() {
        wirelessRoutePolicy.markDirectAttempt();
        host.showWaiting("Abriendo Depuración inalámbrica…");
        host.openSettings(SettingsRoute.WIRELESS_DEBUGGING);
    }

    private void completeDeveloperOptions() {
        aboutFallbackScheduled = false;
        SupportSessionCoordinator support = SupportSessionCoordinator.get(context);
        host.showWaiting("Modo desarrollador detectado. Preparando Depuración inalámbrica…");
        if (support.step() == OnboardingState.Step.DEVELOPER_OPTIONS) {
            support.confirmDeveloperOptions();
        } else {
            LiveGuideRuntime.setStage(GuideStage.WIRELESS_DEBUGGING);
        }
    }

    private void scheduleAboutFallback() {
        if (aboutFallbackScheduled) {
            return;
        }
        aboutFallbackScheduled = true;
        handler.postDelayed(() -> {
            if (aboutFallbackScheduled
                    && LiveGuideRuntime.stage() == GuideStage.AUTOPILOT_PROBE) {
                if (DeveloperOptionsProbe.isEnabled(context)) {
                    completeDeveloperOptions();
                } else {
                    openSamsungDevelopmentFallback();
                }
            }
        }, FALLBACK_DELAY_MS);
    }

    private void openSamsungDevelopmentFallback() {
        if (LiveGuideRuntime.stage() != GuideStage.AUTOPILOT_PROBE) {
            return;
        }
        aboutFallbackScheduled = false;
        host.showWaiting("Las opciones de desarrollador todavía no están activas. Abriendo Acerca del teléfono…");
        LiveGuideRuntime.setStage(GuideStage.DEV_ABOUT_PHONE);
        host.openSettings(Settings.ACTION_DEVICE_INFO_SETTINGS);
    }

    private void resetProbe() {
        probeStartedAtMs = SystemClock.elapsedRealtime();
        aboutFallbackScheduled = false;
        wirelessRoutePolicy.reset();
    }

    private void debug(String message) {
        if (BuildConfig.DEBUG) {
            Log.d("GloshGuidedInstall", message);
        }
    }
}
