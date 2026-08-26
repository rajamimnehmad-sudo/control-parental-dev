package com.glosh.remote.spike;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.WindowManager;

import com.glosh.remote.spike.broker.SupportSessionCoordinator;
import com.glosh.remote.spike.session.PairingUiState;
import com.glosh.remote.spike.session.ServiceStartHandoff;
import com.glosh.remote.spike.session.SessionState;
import com.glosh.remote.spike.wizard.OemFamily;
import com.glosh.remote.spike.wizard.OnboardingState;
import com.glosh.remote.spike.wizard.WizardLayout;

/** Notification-PIN Samsung entry point: one connect action, then RemoteInput. */
public final class MainActivity extends Activity implements SupportSessionCoordinator.Listener {
    // Compile-only compatibility for dormant Bubble classes retained in source history. The v19
    // manifest does not expose the Bubble activity and this entry point never sends these actions.
    public static final String ACTION_GUIDE_OPEN = "com.glosh.remote.spike.GUIDE_OPEN";
    public static final String ACTION_GUIDE_BACK = "com.glosh.remote.spike.GUIDE_BACK";
    public static final String ACTION_GUIDE_NEXT = "com.glosh.remote.spike.GUIDE_NEXT";

    private static final long STATE_REFRESH_MS = 250L;
    private static final long BROKER_RETRY_MS = 2_000L;
    private static final int REQUEST_NOTIFICATIONS = 9001;

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
    private boolean connectRequested;
    private boolean notificationPermissionRequestInFlight;
    private boolean notificationPermissionDenied;
    private boolean serviceStartIssued;
    private boolean directDescriptorSeeded;
    private long nextBrokerRetryAtMs;
    private String lastRenderKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

        coordinator = SupportSessionCoordinator.get(this);
        ui = new WizardLayout(this);
        setContentView(ui.view());

        consumeIntent(getIntent());
        // v19 intentionally supersedes every persisted Bubble/guide checkpoint. A descriptor
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
        connectRequested = true;
        serviceStartIssued = false;
        intent.setData(null);
    }

    private void driveConnection() {
        if (!connectRequested || needsNotificationPermission()) {
            return;
        }
        SessionState session = RemotePairingService.getSessionState();
        OnboardingState.Step step = coordinator.step();
        ServiceStartHandoff.Decision handoff = ServiceStartHandoff.decide(
                step,
                serviceStartIssued,
                session);
        if (handoff == ServiceStartHandoff.Decision.ACKNOWLEDGE) {
            coordinator.markSessionStarted();
            serviceStartIssued = true;
        } else if (handoff == ServiceStartHandoff.Decision.FINISH) {
            coordinator.reset();
            connectRequested = false;
            serviceStartIssued = false;
            directDescriptorSeeded = false;
            nextBrokerRetryAtMs = 0L;
            lastRenderKey = null;
            return;
        } else if (handoff == ServiceStartHandoff.Decision.DISPATCH) {
            serviceStartIssued = startSupportSession();
            return;
        } else if (handoff == ServiceStartHandoff.Decision.WAIT) {
            return;
        }

        if (session == SessionState.CONNECTED) {
            return;
        }

        long now = SystemClock.elapsedRealtime();
        step = coordinator.step();
        if (session == SessionState.IDLE) {
            if (step == OnboardingState.Step.HOME || step == OnboardingState.Step.UNAVAILABLE) {
                directDescriptorSeeded = false;
                if (now >= nextBrokerRetryAtMs) {
                    nextBrokerRetryAtMs = now + BROKER_RETRY_MS;
                    coordinator.requestDirectSession();
                }
                return;
            }

            // WIRELESS_DEBUGGING and SESSION_ACTIVE are handled by ServiceStartHandoff above.
        }
    }

    private boolean startSupportSession() {
        if (RemotePairingService.getSessionState() != SessionState.IDLE) {
            return true;
        }
        String descriptor = coordinator.descriptor();
        if (descriptor == null) {
            return false;
        }
        try {
            startForegroundService(new Intent(this, RemotePairingService.class)
                    .setAction(RemotePairingService.ACTION_START)
                    .putExtra(RemotePairingService.EXTRA_JOIN_URI, descriptor));
            return true;
        } catch (Throwable ignored) {
            coordinator.reset();
            connectRequested = false;
            lastRenderKey = null;
            return false;
        }
    }

    private void render() {
        if (coordinator.profile().family() != OemFamily.SAMSUNG) {
            renderMode("unsupported", () -> ui.showUnsupported(coordinator.profile().manufacturer()));
            return;
        }

        if (!connectRequested) {
            renderMode(
                    notificationPermissionDenied ? "home-permission-denied" : "home",
                    this::renderHome);
            return;
        }

        if (needsNotificationPermission()) {
            renderMode("notification-permission", this::renderNotificationPermission);
            return;
        }

        SessionState session = RemotePairingService.getSessionState();
        PairingUiState pairing = RemotePairingService.getPairingUiState();

        if (session == SessionState.CONNECTED) {
            renderMode("connected", this::renderConnected);
            return;
        }

        if (pairing == PairingUiState.CODE_FAILED) {
            renderMode("notification-retry", () -> renderNotificationCode(true));
            return;
        }

        if (pairing == PairingUiState.CONNECTING) {
            renderMode("connecting", this::renderConnecting);
            return;
        }

        if (pairing == PairingUiState.WAITING_FOR_CODE) {
            renderMode("notification-code", () -> renderNotificationCode(false));
            return;
        }

        if (session == SessionState.PREPARING) {
            renderMode("waiting-endpoint", this::renderWaitingForEndpoint);
            return;
        }

        renderMode("preparing", this::renderPreparing);
    }

    private void renderMode(String key, Runnable renderer) {
        if (key.equals(lastRenderKey)) {
            return;
        }
        lastRenderKey = key;
        renderer.run();
    }

    private void renderHome() {
        ui.showScreen(
                "",
                "Conectá tu Samsung con soporte",
                notificationPermissionDenied
                        ? "Para ingresar el código desde la notificación, permití las notificaciones de Glosh Remote."
                        : "Un toque prepara la sesión. Después ingresás los 6 números directamente desde la notificación y Glosh se conecta solo.",
                "No se muestran enlaces, puertos ni claves de sesión.");
        ui.clearVisual();
        ui.showPrimary("CONECTAR CON SOPORTE", view -> startConnection(), true);
    }

    private void renderNotificationPermission() {
        ui.showScreen(
                "",
                "Permití la notificación segura",
                "Glosh usa la respuesta de la notificación para recibir los 6 números sólo cuando Android confirma el endpoint de pairing.",
                "El código no se guarda ni se muestra en registros.");
        ui.clearVisual();
        if (!notificationPermissionRequestInFlight) {
            ui.showPrimary("PERMITIR NOTIFICACIONES", view -> requestNotificationPermission(), true);
        }
        ui.showTertiary("CANCELAR", view -> cancelConnection());
    }

    private void renderPreparing() {
        ui.showScreen(
                "",
                "Preparando soporte…",
                "Glosh está abriendo una sesión temporal y segura con la Mac.",
                "Cuando esté lista, continuá en Depuración inalámbrica.");
        ui.clearVisual();
        ui.showTertiary("CANCELAR", view -> cancelConnection());
    }

    private void renderWaitingForEndpoint() {
        ui.showScreen(
                "",
                "Abrí el código de Android",
                "En Depuración inalámbrica, tocá “Vincular dispositivo con código”. Glosh habilitará la respuesta en la notificación cuando detecte ese diálogo.",
                "No ingreses un código hasta ver la acción “Ingresar código” en la notificación.");
        ui.clearVisual();
        ui.showTertiary("CANCELAR", view -> cancelConnection());
    }

    private void renderNotificationCode(boolean retry) {
        ui.showScreen(
                "",
                retry ? "Ingresá un código nuevo" : "Ingresá el código desde la notificación",
                retry
                        ? "Generá otro código en Android y usá “Ingresar código” en la notificación de Glosh Remote."
                        : "Desplegá la notificación de Glosh Remote, tocá “Ingresar código” y escribí los 6 números. Al enviarlos, Glosh se conecta solo.",
                "La notificación sólo habilita el ingreso después de detectar el endpoint local correcto.");
        ui.clearVisual();
        ui.showTertiary("CANCELAR", view -> cancelConnection());
    }

    private void renderConnecting() {
        ui.showScreen(
                "",
                "Conectando…",
                "Glosh está completando ADB y abriendo la conexión segura con la Mac.",
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

    private void startConnection() {
        connectRequested = true;
        notificationPermissionDenied = false;
        lastRenderKey = null;
        if (needsNotificationPermission()) {
            requestNotificationPermission();
            render();
            return;
        }
        driveConnection();
        render();
    }

    private void requestNotificationPermission() {
        if (!needsNotificationPermission()) {
            notificationPermissionRequestInFlight = false;
            driveConnection();
            render();
            return;
        }
        notificationPermissionRequestInFlight = true;
        requestPermissions(
                new String[] {Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_NOTIFICATIONS);
    }

    private boolean needsNotificationPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_NOTIFICATIONS) {
            return;
        }
        notificationPermissionRequestInFlight = false;
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        notificationPermissionDenied = !granted;
        if (!granted) {
            connectRequested = false;
        }
        lastRenderKey = null;
        driveConnection();
        render();
    }

    private void cancelConnection() {
        if (RemotePairingService.getSessionState() != SessionState.IDLE) {
            startService(new Intent(this, RemotePairingService.class)
                    .setAction(RemotePairingService.ACTION_STOP));
        }
        coordinator.reset();
        connectRequested = false;
        notificationPermissionRequestInFlight = false;
        serviceStartIssued = false;
        directDescriptorSeeded = false;
        nextBrokerRetryAtMs = 0L;
        lastRenderKey = null;
        driveConnection();
        render();
    }
}
