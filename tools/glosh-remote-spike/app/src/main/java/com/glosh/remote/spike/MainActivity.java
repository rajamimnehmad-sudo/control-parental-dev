package com.glosh.remote.spike;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.WindowManager;

import com.glosh.remote.spike.broker.SupportSessionCoordinator;
import com.glosh.remote.spike.protocol.PairingPin;
import com.glosh.remote.spike.session.PairingFailureKind;
import com.glosh.remote.spike.session.PairingUiState;
import com.glosh.remote.spike.session.PinOnlyBootstrapPolicy;
import com.glosh.remote.spike.session.SessionState;
import com.glosh.remote.spike.wizard.OemFamily;
import com.glosh.remote.spike.wizard.OnboardingState;
import com.glosh.remote.spike.wizard.SettingsNavigator;
import com.glosh.remote.spike.wizard.WizardLayout;

/** PIN-only Samsung entry point: six digits in, recoverable local ADB + secure relay out. */
public final class MainActivity extends Activity implements SupportSessionCoordinator.Listener {
    public static final String ACTION_GUIDE_OPEN = "com.glosh.remote.spike.GUIDE_OPEN";
    public static final String ACTION_GUIDE_BACK = "com.glosh.remote.spike.GUIDE_BACK";
    public static final String ACTION_GUIDE_NEXT = "com.glosh.remote.spike.GUIDE_NEXT";

    private static final long STATE_REFRESH_MS = 250L;
    private static final long SERVICE_START_GRACE_MS = 3_000L;
    private static final long BROKER_RETRY_MS = 2_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SettingsNavigator settingsNavigator = new SettingsNavigator();
    private final Runnable refreshState = new Runnable() {
        @Override
        public void run() {
            driveConnection();
            render();
            handler.postDelayed(this, STATE_REFRESH_MS);
        }
    };

    private SupportSessionCoordinator coordinator;
    private WizardLayout ui;
    private String pendingPairingCode;
    private boolean pairingCodeDispatched;
    private boolean bootstrapStartIssued;
    private boolean descriptorAttached;
    private boolean directDescriptorSeeded;
    private boolean wirelessSettingsOpened;
    private long bootstrapStartAtMs;
    private long nextBrokerRetryAtMs;
    private String lastRenderKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!BuildConfig.DEBUG) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
        coordinator = SupportSessionCoordinator.get(this);
        ui = new WizardLayout(this);
        setContentView(ui.view());
        consumeIntent(getIntent());
        if (RemotePairingService.getSessionState() == SessionState.IDLE
                && !directDescriptorSeeded
                && coordinator.step() != OnboardingState.Step.HOME
                && coordinator.step() != OnboardingState.Step.UNAVAILABLE) {
            coordinator.reset();
        }
        driveConnection();
        render();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        consumeIntent(intent);
        driveConnection();
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        coordinator.attach(this);
        ui.onHostResume();
        handler.removeCallbacks(refreshState);
        handler.post(refreshState);
    }

    @Override
    protected void onPause() {
        ui.onHostPause();
        handler.removeCallbacks(refreshState);
        coordinator.detach(this);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(refreshState);
        coordinator.detach(this);
        super.onDestroy();
    }

    @Override
    public void onStateChanged() {
        handler.post(() -> {
            driveConnection();
            render();
        });
    }

    private void consumeIntent(Intent intent) {
        Uri data = intent == null ? null : intent.getData();
        if (data == null || !"gloshremote".equalsIgnoreCase(data.getScheme())) {
            return;
        }
        coordinator.reset();
        coordinator.seedDebugDescriptor(data.toString());
        directDescriptorSeeded = true;
        descriptorAttached = false;
        pairingCodeDispatched = false;
        intent.setData(null);
    }

    private void driveConnection() {
        SessionState session = RemotePairingService.getSessionState();
        PairingUiState pairing = RemotePairingService.getPairingUiState();
        long now = SystemClock.elapsedRealtime();

        if (session == SessionState.CONNECTED
                || session == SessionState.RECONNECTING
                || session == SessionState.ADB_READY) {
            pendingPairingCode = null;
            pairingCodeDispatched = false;
        }
        if (pairing == PairingUiState.CODE_FAILED && !pairingCodeDispatched) {
            pendingPairingCode = null;
        }

        if (session == SessionState.IDLE && !bootstrapStartIssued) {
            startAdbBootstrap();
            bootstrapStartIssued = true;
            bootstrapStartAtMs = now;
        } else if (session != SessionState.IDLE) {
            bootstrapStartIssued = true;
        }

        OnboardingState.Step step = coordinator.step();
        if (step == OnboardingState.Step.HOME || step == OnboardingState.Step.UNAVAILABLE) {
            directDescriptorSeeded = false;
            descriptorAttached = false;
            if (now >= nextBrokerRetryAtMs) {
                nextBrokerRetryAtMs = now + BROKER_RETRY_MS;
                coordinator.requestDirectSession();
            }
        }

        session = RemotePairingService.getSessionState();
        pairing = RemotePairingService.getPairingUiState();
        step = coordinator.step();

        if (step == OnboardingState.Step.WIRELESS_DEBUGGING
                && PinOnlyBootstrapPolicy.canAttachDescriptor(session, descriptorAttached)) {
            attachSupportDescriptor();
        }

        if (session == SessionState.PREPARING
                && PairingPin.isValid(pendingPairingCode)
                && !pairingCodeDispatched) {
            dispatchPairingCode(pendingPairingCode);
        }

        if (PinOnlyBootstrapPolicy.shouldLaunchWirelessSettings(
                session,
                pairing,
                wirelessSettingsOpened,
                step == OnboardingState.Step.WIRELESS_DEBUGGING)) {
            wirelessSettingsOpened = true;
            settingsNavigator.openWirelessDebugging(this);
            return;
        }

        if (session == SessionState.IDLE
                && bootstrapStartIssued
                && now - bootstrapStartAtMs >= SERVICE_START_GRACE_MS
                && coordinator.step() == OnboardingState.Step.SESSION_ACTIVE) {
            bootstrapStartIssued = false;
            descriptorAttached = false;
            pairingCodeDispatched = false;
            directDescriptorSeeded = false;
            wirelessSettingsOpened = false;
            coordinator.reset();
            nextBrokerRetryAtMs = 0L;
        }
    }

    private void startAdbBootstrap() {
        startForegroundService(new Intent(this, RemotePairingService.class)
                .setAction(RemotePairingService.ACTION_START));
    }

    private void attachSupportDescriptor() {
        String descriptor = coordinator.markSessionStarted();
        if (descriptor == null) {
            return;
        }
        descriptorAttached = true;
        startService(new Intent(this, RemotePairingService.class)
                .setAction(RemotePairingService.ACTION_ATTACH_DESCRIPTOR)
                .putExtra(RemotePairingService.EXTRA_JOIN_URI, descriptor));
    }

    private void submitPairingCode(String code) {
        if (!PairingPin.isValid(code)) {
            return;
        }
        pendingPairingCode = code;
        pairingCodeDispatched = false;
        if (RemotePairingService.getSessionState() == SessionState.PREPARING) {
            dispatchPairingCode(code);
        }
        driveConnection();
        render();
    }

    private void dispatchPairingCode(String code) {
        pairingCodeDispatched = true;
        startService(new Intent(this, RemotePairingService.class)
                .setAction(RemotePairingService.ACTION_SUBMIT_CODE)
                .putExtra(RemotePairingService.EXTRA_PAIRING_CODE, code));
    }

    private void render() {
        if (coordinator.profile().family() != OemFamily.SAMSUNG) {
            renderMode("unsupported", () -> ui.showUnsupported(coordinator.profile().manufacturer()));
            return;
        }
        SessionState session = RemotePairingService.getSessionState();
        PairingUiState pairing = RemotePairingService.getPairingUiState();
        if (session == SessionState.CONNECTED) {
            renderMode("connected", this::renderConnected);
            return;
        }
        if (session == SessionState.RECONNECTING) {
            renderMode("reconnecting", this::renderReconnecting);
            return;
        }
        if (session == SessionState.ADB_READY) {
            renderMode("adb-ready", this::renderAdbReady);
            return;
        }
        if (pairing == PairingUiState.CONNECTING) {
            renderMode("connecting", () -> renderConnecting(true));
            return;
        }
        if (PairingPin.isValid(pendingPairingCode) || pairingCodeDispatched) {
            renderMode("received", () -> renderConnecting(false));
            return;
        }
        if (pairing == PairingUiState.CODE_FAILED) {
            PairingFailureKind failure = RemotePairingService.getPairingFailureKind();
            renderMode("retry-" + failure.name(), () -> renderCodeInput(failure));
            return;
        }
        if (PinOnlyBootstrapPolicy.shouldShowCodeInput(pairing)) {
            renderMode("input", () -> renderCodeInput(PairingFailureKind.NONE));
            return;
        }
        if (pairing == PairingUiState.CHECKING_SAVED_IDENTITY) {
            renderMode(
                    "checking-saved",
                    () -> renderPreparing(
                            "Preparando ADB",
                            "Glosh está comprobando si este teléfono ya quedó vinculado. No hagas nada."));
            return;
        }
        if (pairing == PairingUiState.DISCOVERING_ENDPOINT) {
            renderMode(
                    "discovering",
                    () -> renderPreparing(
                            "Abrí la vinculación",
                            "Glosh abrió Depuración inalámbrica. Tocá “Vincular dispositivo con código” y luego escribí sólo los 6 números."));
            return;
        }
        renderMode(
                "preparing",
                () -> renderPreparing(
                        "Preparando conexión",
                        "Glosh está preparando ADB y la sesión segura automáticamente."));
    }

    private void renderMode(String key, Runnable renderer) {
        if (key.equals(lastRenderKey)) {
            return;
        }
        lastRenderKey = key;
        renderer.run();
    }

    private void renderCodeInput(PairingFailureKind failure) {
        boolean retry = failure != PairingFailureKind.NONE;
        String title;
        String text;
        switch (failure) {
            case PIN_REJECTED:
                title = "Código rechazado";
                text = "Android rechazó el código. Generá uno nuevo en “Vincular dispositivo con código” y escribilo acá.";
                break;
            case ENDPOINT_CHANGED:
                title = "La vinculación cambió";
                text = "Android cambió la vinculación mientras conectábamos. Generá un código nuevo y escribilo acá.";
                break;
            case ENDPOINT_UNAVAILABLE:
                title = "La vinculación venció";
                text = "La vinculación anterior dejó de estar disponible. Generá un código nuevo y escribilo acá.";
                break;
            case ADB_ERROR:
                title = "No pudimos completar ADB";
                text = "Abrí nuevamente “Vincular dispositivo con código”, generá un código nuevo y escribilo acá.";
                break;
            case NONE:
            default:
                title = "Ingresá el código";
                text = "Escribí los 6 números que muestra “Vincular dispositivo con código”. Al completar el sexto número, Glosh vincula ADB y continúa solo.";
                break;
        }
        ui.showScreen("", title, text, "");
        ui.clearVisual();
        ui.showPairingInput(this::submitPairingCode, retry);
        ui.focusPairingInput();
    }

    private void renderPreparing(String title, String text) {
        ui.showScreen("", title, text, "");
        ui.clearVisual();
        ui.showTertiary("CANCELAR", view -> cancelConnection());
    }

    private void renderConnecting(boolean activePairing) {
        ui.showScreen(
                "",
                activePairing ? "Conectando…" : "Código recibido",
                activePairing
                        ? "Glosh está completando ADB. La sesión remota se engancha automáticamente cuando esté disponible."
                        : "No hagas nada. Glosh está usando el código y preparando ADB automáticamente.",
                "");
        ui.clearVisual();
        ui.showTertiary("CANCELAR", view -> cancelConnection());
    }

    private void renderAdbReady() {
        ui.showScreen(
                "",
                "Vinculación lista",
                "ADB ya quedó vinculado. Glosh está esperando o abriendo la conexión segura automáticamente. No generes otro código.",
                "");
        ui.clearVisual();
        ui.showTertiary("CANCELAR", view -> cancelConnection());
    }

    private void renderReconnecting() {
        ui.showScreen(
                "",
                "Reconectando…",
                "Glosh está recuperando la sesión automáticamente. No hace falta generar otro código.",
                "");
        ui.clearVisual();
        ui.showTertiary("CANCELAR", view -> cancelConnection());
    }

    private void renderConnected() {
        ui.showScreen("", "Conectado", "La sesión temporal y segura está activa.", "");
        ui.clearVisual();
        ui.showSecondary("FINALIZAR CONEXIÓN", view -> cancelConnection());
    }

    private void cancelConnection() {
        if (RemotePairingService.getSessionState() != SessionState.IDLE) {
            startService(new Intent(this, RemotePairingService.class)
                    .setAction(RemotePairingService.ACTION_STOP));
        }
        coordinator.reset();
        pendingPairingCode = null;
        pairingCodeDispatched = false;
        bootstrapStartIssued = false;
        descriptorAttached = false;
        directDescriptorSeeded = false;
        wirelessSettingsOpened = false;
        bootstrapStartAtMs = 0L;
        nextBrokerRetryAtMs = 0L;
        lastRenderKey = null;
        driveConnection();
        render();
    }
}
