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
import com.glosh.remote.spike.session.PairingUiState;
import com.glosh.remote.spike.session.SessionState;
import com.glosh.remote.spike.wizard.OemFamily;
import com.glosh.remote.spike.wizard.OnboardingState;
import com.glosh.remote.spike.wizard.WizardLayout;

/** PIN-only Samsung entry point: six digits in, secure ADB/relay connection out. */
public final class MainActivity extends Activity implements SupportSessionCoordinator.Listener {
    // Compile-only compatibility for dormant Bubble classes retained in source history. The v18
    // manifest does not expose the Bubble activity and this entry point never sends these actions.
    public static final String ACTION_GUIDE_OPEN = "com.glosh.remote.spike.GUIDE_OPEN";
    public static final String ACTION_GUIDE_BACK = "com.glosh.remote.spike.GUIDE_BACK";
    public static final String ACTION_GUIDE_NEXT = "com.glosh.remote.spike.GUIDE_NEXT";

    private static final long STATE_REFRESH_MS = 250L;
    private static final long SERVICE_START_GRACE_MS = 3_000L;
    private static final long BROKER_RETRY_MS = 2_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
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
    private boolean serviceStartIssued;
    private boolean directDescriptorSeeded;
    private long serviceStartAtMs;
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
        // v18 intentionally supersedes every persisted Bubble/guide checkpoint. A descriptor
        // supplied explicitly through the DEV deep link is the only state that may start in
        // WIRELESS_DEBUGGING without a fresh broker rendezvous.
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
        serviceStartIssued = false;
        pairingCodeDispatched = false;
        intent.setData(null);
    }

    private void driveConnection() {
        SessionState session = RemotePairingService.getSessionState();
        PairingUiState pairing = RemotePairingService.getPairingUiState();
        long now = SystemClock.elapsedRealtime();

        if (session == SessionState.CONNECTED) {
            pendingPairingCode = null;
            pairingCodeDispatched = false;
            serviceStartIssued = false;
            return;
        }

        if (pairing == PairingUiState.CODE_FAILED) {
            pendingPairingCode = null;
            pairingCodeDispatched = false;
        }

        OnboardingState.Step step = coordinator.step();
        if (session == SessionState.IDLE) {
            if (step == OnboardingState.Step.HOME || step == OnboardingState.Step.UNAVAILABLE) {
                directDescriptorSeeded = false;
                if (now >= nextBrokerRetryAtMs) {
                    nextBrokerRetryAtMs = now + BROKER_RETRY_MS;
                    coordinator.requestDirectSession();
                }
                return;
            }

            if (step == OnboardingState.Step.WIRELESS_DEBUGGING && !serviceStartIssued) {
                if (startSupportSession()) {
                    serviceStartIssued = true;
                    serviceStartAtMs = now;
                }
                return;
            }

            if (step == OnboardingState.Step.SESSION_ACTIVE
                    && serviceStartIssued
                    && now - serviceStartAtMs >= SERVICE_START_GRACE_MS) {
                serviceStartIssued = false;
                pairingCodeDispatched = false;
                directDescriptorSeeded = false;
                coordinator.reset();
                nextBrokerRetryAtMs = 0L;
            }
            return;
        }

        if (session == SessionState.PREPARING) {
            serviceStartIssued = true;
            if (PairingPin.isValid(pendingPairingCode) && !pairingCodeDispatched) {
                pairingCodeDispatched = true;
                startService(new Intent(this, RemotePairingService.class)
                        .setAction(RemotePairingService.ACTION_SUBMIT_CODE)
                        .putExtra(RemotePairingService.EXTRA_PAIRING_CODE, pendingPairingCode));
            }
        }
    }

    private boolean startSupportSession() {
        if (RemotePairingService.getSessionState() != SessionState.IDLE) {
            return true;
        }
        String descriptor = coordinator.markSessionStarted();
        if (descriptor == null) {
            return false;
        }
        startForegroundService(new Intent(this, RemotePairingService.class)
                .setAction(RemotePairingService.ACTION_START)
                .putExtra(RemotePairingService.EXTRA_JOIN_URI, descriptor));
        return true;
    }

    private void submitPairingCode(String code) {
        if (!PairingPin.isValid(code)) {
            return;
        }
        pendingPairingCode = code;
        pairingCodeDispatched = false;
        driveConnection();
        render();
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

        if (pairing == PairingUiState.CODE_FAILED) {
            renderMode("retry", () -> renderCodeInput(true));
            return;
        }

        if (PairingPin.isValid(pendingPairingCode) || pairingCodeDispatched) {
            String mode = pairing == PairingUiState.CONNECTING ? "connecting" : "received";
            renderMode(mode, () -> renderConnecting(pairing == PairingUiState.CONNECTING));
            return;
        }

        renderMode("input", () -> renderCodeInput(false));
    }

    private void renderMode(String key, Runnable renderer) {
        if (key.equals(lastRenderKey)) {
            return;
        }
        lastRenderKey = key;
        renderer.run();
    }

    private void renderCodeInput(boolean retry) {
        ui.showScreen(
                "",
                retry ? "Ingresá un código nuevo" : "Ingresá el código",
                retry
                        ? "El código anterior venció. Generá otro código de 6 dígitos en Depuración inalámbrica y escribilo acá."
                        : "Escribí los 6 números que muestra “Vincular dispositivo con código”. Al completar el sexto número, Glosh se conecta solo.",
                "");
        ui.clearVisual();
        ui.showPairingInput(this::submitPairingCode, retry);
        ui.focusPairingInput();
    }

    private void renderConnecting(boolean activePairing) {
        ui.showScreen(
                "",
                activePairing ? "Conectando…" : "Código recibido",
                activePairing
                        ? "Glosh está completando ADB y abriendo la conexión segura con la Mac."
                        : "No hagas nada. Glosh está preparando la conexión con la Mac y usará el código automáticamente.",
                "");
        ui.clearVisual();
        ui.showTertiary("CANCELAR", view -> cancelConnection());
    }

    private void renderConnected() {
        ui.showScreen(
                "",
                "Conectado",
                "La Mac ya tiene la sesión temporal y segura del teléfono.",
                "");
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
        serviceStartIssued = false;
        directDescriptorSeeded = false;
        serviceStartAtMs = 0L;
        nextBrokerRetryAtMs = 0L;
        lastRenderKey = null;
        driveConnection();
        render();
    }
}
