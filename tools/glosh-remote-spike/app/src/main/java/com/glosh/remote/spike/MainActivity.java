package com.glosh.remote.spike;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;

import com.glosh.remote.spike.broker.SupportSessionCoordinator;
import com.glosh.remote.spike.session.PairingUiState;
import com.glosh.remote.spike.session.SessionState;
import com.glosh.remote.spike.wizard.OnboardingState;
import com.glosh.remote.spike.wizard.SettingsNavigator;
import com.glosh.remote.spike.wizard.WizardLayout;

/** One button, the official Wireless Debugging screen and notification RemoteInput. */
public final class MainActivity extends Activity implements SupportSessionCoordinator.Listener {
    private static final int REQUEST_NOTIFICATIONS = 9001;
    private static final long REFRESH_MS = 500L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SettingsNavigator settingsNavigator = new SettingsNavigator();
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            observeService();
            render();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    private SupportSessionCoordinator coordinator;
    private WizardLayout ui;
    private boolean connectAfterPermission;
    private boolean permissionDenied;
    private boolean serviceWasActive;
    private boolean dispatchFailed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        coordinator = SupportSessionCoordinator.get(this);
        ui = new WizardLayout(this);
        setContentView(ui.view());
        serviceWasActive = RemotePairingService.getSessionState() != SessionState.IDLE;
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        coordinator.attach(this);
        ui.onHostResume();
        handler.removeCallbacks(refresh);
        handler.post(refresh);
        dispatchSessionIfReady();
    }

    @Override
    protected void onPause() {
        ui.onHostPause();
        coordinator.detach(this);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(refresh);
        coordinator.detach(this);
        super.onDestroy();
    }

    @Override
    public void onStateChanged() {
        handler.post(() -> {
            dispatchSessionIfReady();
            render();
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQUEST_NOTIFICATIONS) {
            return;
        }
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        permissionDenied = !granted;
        if (granted && connectAfterPermission) {
            connectAfterPermission = false;
            beginSupport();
        } else {
            connectAfterPermission = false;
            render();
        }
    }

    private void connect() {
        dispatchFailed = false;
        permissionDenied = false;
        if (needsNotificationPermission()) {
            connectAfterPermission = true;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
            return;
        }
        beginSupport();
    }

    private void beginSupport() {
        coordinator.reset();
        coordinator.requestSupport();
        render();
    }

    private boolean needsNotificationPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED;
    }

    private void dispatchSessionIfReady() {
        if (coordinator.step() != OnboardingState.Step.WIRELESS_DEBUGGING) {
            return;
        }
        String descriptor = coordinator.takeDescriptor();
        if (descriptor == null) {
            return;
        }
        try {
            Intent service = new Intent(this, RemotePairingService.class)
                    .setAction(RemotePairingService.ACTION_START)
                    .putExtra(RemotePairingService.EXTRA_JOIN_URI, descriptor);
            startForegroundService(service);
            settingsNavigator.openWirelessDebugging(this);
        } catch (Throwable error) {
            dispatchFailed = true;
            coordinator.reset();
        }
    }

    private void observeService() {
        SessionState session = RemotePairingService.getSessionState();
        if (session != SessionState.IDLE) {
            serviceWasActive = true;
            return;
        }
        if (serviceWasActive && coordinator.step() == OnboardingState.Step.SESSION_ACTIVE) {
            serviceWasActive = false;
            coordinator.reset();
        }
    }

    private void cancel() {
        if (RemotePairingService.getSessionState() != SessionState.IDLE) {
            Intent stop = new Intent(this, RemotePairingService.class)
                    .setAction(RemotePairingService.ACTION_STOP);
            startService(stop);
        }
        serviceWasActive = false;
        coordinator.reset();
        render();
    }

    private void render() {
        SessionState session = RemotePairingService.getSessionState();
        PairingUiState pairing = RemotePairingService.getPairingUiState();
        if (session == SessionState.CONNECTED) {
            ui.showScreen(
                    "",
                    "Conectado con soporte",
                    "La conexión temporal y segura está activa.",
                    "Podés cancelarla en cualquier momento desde la notificación.");
            ui.clearVisual();
            ui.showSecondary("CANCELAR", view -> cancel());
            return;
        }
        if (session == SessionState.PREPARING) {
            boolean connecting = pairing == PairingUiState.CONNECTING;
            ui.showScreen(
                    "",
                    connecting ? "Conectando…" : "Ingresá los 6 dígitos",
                    connecting
                            ? "Código recibido. Glosh está terminando la conexión segura."
                            : "Generá el código en Android y escribilo en la notificación de Glosh.",
                    connecting ? "Esto tarda sólo unos segundos." : "No hace falta volver a esta pantalla.");
            ui.clearVisual();
            ui.showSecondary("CANCELAR", view -> cancel());
            return;
        }
        if (permissionDenied) {
            ui.showScreen(
                    "",
                    "Necesitamos las notificaciones",
                    "El código de 6 dígitos se ingresa únicamente desde la notificación de Glosh.",
                    "Android debe permitir notificaciones para continuar.");
            ui.clearVisual();
            ui.showPrimary("PERMITIR Y CONECTAR", view -> connect(), true);
            return;
        }
        if (dispatchFailed) {
            ui.showScreen(
                    "",
                    "No pudimos abrir la conexión",
                    "Android no permitió iniciar la sesión segura.",
                    "Intentá nuevamente.");
            ui.clearVisual();
            ui.showPrimary("REINTENTAR", view -> connect(), true);
            return;
        }

        switch (coordinator.step()) {
            case CHECKING_SUPPORT -> showWaiting(
                    "Buscando soporte…",
                    "Comprobando que la Mac esté lista.");
            case REQUESTING_SUPPORT -> showWaiting(
                    "Soporte encontrado",
                    "Preparando una conexión segura para este teléfono.");
            case WIRELESS_DEBUGGING, SESSION_ACTIVE -> showWaiting(
                    "Abriendo Depuración inalámbrica…",
                    "Cuando Android muestre el código, ingresalo en la notificación de Glosh.");
            case UNAVAILABLE -> {
                ui.showScreen(
                        "",
                        "Soporte todavía no está abierto",
                        "Avisale al técnico para que abra la sesión y volvé a intentar.",
                        "No se creó ninguna conexión.");
                ui.clearVisual();
                ui.showPrimary("REINTENTAR", view -> connect(), true);
            }
            default -> ui.showHome(view -> connect());
        }
    }

    private void showWaiting(String title, String body) {
        ui.showScreen("", title, body, "No cierres Glosh mientras se prepara.");
        ui.clearVisual();
        ui.showSecondary("CANCELAR", view -> cancel());
    }
}
