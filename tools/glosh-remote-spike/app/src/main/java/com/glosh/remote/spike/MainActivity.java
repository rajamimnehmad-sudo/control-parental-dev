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
import com.glosh.remote.spike.guide.accessibility.GuideServiceStatus;
import com.glosh.remote.spike.guide.accessibility.GuideOnlyDebugIntentHandler;
import com.glosh.remote.spike.guide.accessibility.SettingsPackageResolver;
import com.glosh.remote.spike.guide.state.GuideStage;
import com.glosh.remote.spike.guide.state.LiveGuideRuntime;
import com.glosh.remote.spike.session.PairingUiState;
import com.glosh.remote.spike.session.SessionState;
import com.glosh.remote.spike.wizard.DeveloperGuidePhase;
import com.glosh.remote.spike.wizard.GuideNotification;
import com.glosh.remote.spike.wizard.OemGuideRecipe;
import com.glosh.remote.spike.wizard.OnboardingState;
import com.glosh.remote.spike.wizard.SettingsNavigator;
import com.glosh.remote.spike.wizard.WizardLayout;

public final class MainActivity extends Activity implements SupportSessionCoordinator.Listener {
    private static final int REQUEST_NOTIFICATIONS = 9001;
    private static final int PERMISSION_NONE = 0;
    private static final int PERMISSION_DEVELOPER_SETTINGS = 1;
    private static final int PERMISSION_START_SESSION = 2;
    private static final long STATE_REFRESH_MS = 500;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SettingsNavigator settingsNavigator = new SettingsNavigator();
    private final SettingsPackageResolver settingsPackageResolver = new SettingsPackageResolver();
    private final Runnable refreshState = new Runnable() {
        @Override
        public void run() {
            synchronizeGuidePermission();
            render();
            handler.postDelayed(this, STATE_REFRESH_MS);
        }
    };

    private SupportSessionCoordinator coordinator;
    private GuideNotification guideNotification;
    private WizardLayout ui;
    private String lastRenderKey;
    private int pendingPermissionAction;
    private boolean pairingHelp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        coordinator = SupportSessionCoordinator.get(this);
        guideNotification = new GuideNotification(this);
        ui = new WizardLayout(this);
        setContentView(ui.view());
        if (savedInstanceState != null) {
            pendingPermissionAction = savedInstanceState.getInt("pending_permission", PERMISSION_NONE);
            pairingHelp = savedInstanceState.getBoolean("pairing_help", false);
        }
        consumeDebugIntent(getIntent());
        render();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (RemotePairingService.getSessionState() == SessionState.IDLE) {
            consumeDebugIntent(intent);
        }
        lastRenderKey = null;
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        coordinator.attach(this);
        ui.onHostResume();
        synchronizeGuidePermission();
        handler.removeCallbacks(refreshState);
        handler.post(refreshState);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refreshState);
        ui.onHostPause();
        coordinator.detach(this);
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putInt("pending_permission", pendingPermissionAction);
        state.putBoolean("pairing_help", pairingHelp);
        super.onSaveInstanceState(state);
    }

    @Override
    public void onStateChanged() {
        lastRenderKey = null;
        render();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_NOTIFICATIONS) {
            return;
        }
        int action = pendingPermissionAction;
        pendingPermissionAction = PERMISSION_NONE;
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (action == PERMISSION_DEVELOPER_SETTINGS) {
            performOpenDeveloperSettings(granted);
        } else if (action == PERMISSION_START_SESSION) {
            startSupportSession();
        }
    }

    private void render() {
        SessionState session = RemotePairingService.getSessionState();
        PairingUiState pairing = RemotePairingService.getPairingUiState();
        OnboardingState.Step step = coordinator.step();
        if (session == SessionState.IDLE && step == OnboardingState.Step.SESSION_ACTIVE) {
            coordinator.reset();
            return;
        }
        String key = session + ":" + pairing + ":" + step + ":"
                + coordinator.developerPhase() + ":" + coordinator.wirelessHelp() + ":"
                + pairingHelp + ":" + GuideServiceStatus.isEnabled(this) + ":"
                + LiveGuideRuntime.stage();
        if (key.equals(lastRenderKey)) {
            return;
        }
        lastRenderKey = key;
        if (session == SessionState.CONNECTED) {
            renderConnected();
        } else if (session == SessionState.PREPARING) {
            renderPairing(pairing);
        } else {
            switch (step) {
                case CHECKING_SUPPORT -> renderCheckingSupport();
                case GUIDE_PERMISSION -> renderGuidePermission();
                case DEVELOPER_OPTIONS -> renderDeveloperOptions();
                case REQUESTING_SUPPORT -> renderRequestingSupport();
                case WIRELESS_DEBUGGING -> renderWirelessDebugging();
                case UNAVAILABLE -> renderUnavailable();
                default -> renderHome();
            }
        }
    }

    private void renderHome() {
        guideNotification.clear();
        ui.showHome(view -> coordinator.requestSupport());
    }

    private void renderCheckingSupport() {
        ui.showScreen(
                "Preparando la guía",
                "Estamos buscando soporte",
                "Primero comprobamos que haya alguien disponible para ayudarte.",
                "Todavía no comenzó ninguna sesión temporal.");
        ui.clearVisual();
        ui.showSecondary("CANCELAR", view -> coordinator.reset());
    }

    private void renderGuidePermission() {
        ui.showScreen(
                "1 de 3 · Preparar el teléfono",
                "PERMITÍ QUE GLOSH TE GUÍE",
                "Durante esta configuración Glosh te va a indicar exactamente dónde tocar dentro de Ajustes.",
                "La guía mira sólo Ajustes y se desactiva automáticamente cuando terminamos.");
        ui.clearVisual();
        ui.showPrimary("ACTIVAR GUÍA", view -> activateGuide(), true);
        ui.showSecondary("CONTINUAR SIN GUÍA", view -> continueWithoutGuide());
        ui.showTertiary("CANCELAR", view -> coordinator.reset());
    }

    private void renderDeveloperOptions() {
        GuideStage liveStage = LiveGuideRuntime.stage();
        if (liveStage == GuideStage.AUTOPILOT_PROBE
                || liveStage == GuideStage.SUPPORT_PREPARING
                || liveStage == GuideStage.AUTOPILOT_CREDENTIAL) {
            boolean credential = liveStage == GuideStage.AUTOPILOT_CREDENTIAL;
            ui.showScreen(
                    "Preparando teléfono…",
                    credential ? "Confirmá tu bloqueo de pantalla" : "Glosh está preparando tu teléfono",
                    credential
                            ? "Android necesita que ingreses el PIN, patrón o contraseña del teléfono."
                            : "Detectamos el estado real y elegimos el camino más corto y seguro.",
                    credential
                            ? "Glosh nunca lee ni guarda esa credencial."
                            : "No toques otras opciones mientras Glosh continúa.");
            ui.clearVisual();
            ui.showSecondary("CANCELAR", view -> coordinator.reset());
            return;
        }
        OemGuideRecipe recipe = coordinator.recipe();
        DeveloperGuidePhase phase = coordinator.developerPhase();
        if (phase == DeveloperGuidePhase.HELP) {
            ui.showScreen(
                    "1 de 3 · Preparar el teléfono",
                    "Vamos de nuevo",
                    recipe.developerOptions().help().copy(),
                    "Seguí sólo la fila resaltada en verde.");
            ui.showGuide(recipe.developerOptions());
            ui.showPrimary("MOSTRARME DE NUEVO", view -> coordinator.showDeveloperGuide(), true);
            ui.showSecondary("ABRIR AJUSTES", view -> openDeveloperSettings());
            return;
        }
        if (phase == DeveloperGuidePhase.CONFIRMATION) {
            ui.showScreen(
                    "1 de 3 · Preparar el teléfono",
                    "¿Viste el mensaje “Ya sos desarrollador”?",
                    "Si apareció, ya podemos seguir.",
                    "La solicitud de soporte recién se creará cuando confirmes.");
            ui.showGuide(recipe.developerOptions());
            ui.showPrimary("SÍ, SEGUIR", view -> coordinator.confirmDeveloperOptions(), true);
            ui.showSecondary("NO ME APARECIÓ", view -> coordinator.showDeveloperGuide());
            ui.showTertiary("ME PERDÍ", view -> coordinator.showDeveloperHelp());
            return;
        }
        ui.showScreen(
                "1 de 3 · Preparar el teléfono",
                recipe.developerOptions().title(),
                recipe.developerOptions().body(),
                "Detectamos: " + recipe.familyLabel() + " · " + coordinator.profile().model()
                        + guideFallbackCopy());
        ui.showGuide(recipe.developerOptions());
        ui.showPrimary("ABRIR AJUSTES", view -> openDeveloperSettings(), true);
        ui.showSecondary("YA LO TENGO ACTIVADO", view -> coordinator.openedDeveloperSettings());
        ui.showTertiary("ME PERDÍ", view -> coordinator.showDeveloperHelp());
    }

    private void renderRequestingSupport() {
        ui.showScreen(
                "Preparando la conexión",
                "Esperando a soporte",
                "Un operador va a aceptar tu solicitud.",
                "Podés quedarte en esta pantalla. Glosh seguirá intentando durante unos minutos.");
        ui.clearVisual();
        ui.showSecondary("CANCELAR", view -> coordinator.reset());
    }

    private void renderWirelessDebugging() {
        OemGuideRecipe recipe = coordinator.recipe();
        if (coordinator.wirelessHelp()) {
            ui.showScreen(
                    "2 de 3 · Abrir conexión",
                    "Te muestro dónde está",
                    recipe.wirelessDebugging().help().copy(),
                    "No cambies ninguna otra opción.");
            ui.showGuide(recipe.wirelessDebugging());
            ui.showPrimary("MOSTRARME DE NUEVO", view -> coordinator.showWirelessGuide(), true);
            ui.showSecondary("ABRIR AJUSTES", view -> prepareAndOpenWirelessDebugging());
            ui.showTertiary("CANCELAR CONEXIÓN", view -> cancelConnection());
            return;
        }
        ui.showScreen(
                "2 de 3 · Abrir conexión",
                recipe.wirelessDebugging().title(),
                recipe.wirelessDebugging().body(),
                "Activá Depuración inalámbrica y tocá Emparejar dispositivo con código.");
        ui.showGuide(recipe.wirelessDebugging());
        ui.showPrimary("ABRIR DEPURACIÓN INALÁMBRICA", view -> prepareAndOpenWirelessDebugging(), true);
        ui.showSecondary("ME PERDÍ", view -> coordinator.showWirelessHelp());
        ui.showTertiary("CANCELAR CONEXIÓN", view -> cancelConnection());
    }

    private void renderPairing(PairingUiState pairing) {
        if (pairingHelp) {
            ui.showScreen(
                    "3 de 3 · Ingresar código",
                    "Busquemos el código",
                    coordinator.recipe().wirelessDebugging().help().copy(),
                    "Tocá Emparejar dispositivo con código. Android va a mostrar 6 números.");
            ui.showGuide(coordinator.recipe().wirelessDebugging());
            ui.showPrimary("ABRIR DEPURACIÓN INALÁMBRICA", view -> openWirelessDuringSession(), true);
            ui.showSecondary("VOLVER AL CÓDIGO", view -> {
                pairingHelp = false;
                lastRenderKey = null;
                render();
            });
            ui.showTertiary("CANCELAR CONEXIÓN", view -> cancelConnection());
            return;
        }
        if (pairing == PairingUiState.WAITING_FOR_CODE || pairing == PairingUiState.CODE_FAILED) {
            boolean failed = pairing == PairingUiState.CODE_FAILED;
            ui.showScreen(
                    "3 de 3 · Ingresar código",
                    failed ? "Ese código ya no sirve" : "INGRESÁ LOS 6 NÚMEROS",
                    failed ? "Generá uno nuevo en Android y escribilo acá." : "Escribí los números que muestra Android.",
                    "Al ingresar el sexto número, Glosh continúa automáticamente.");
            ui.showPairingInput(this::submitPairingCode, failed);
            if (failed) {
                ui.showPrimary("ABRIR DEPURACIÓN INALÁMBRICA", view -> openWirelessDuringSession(), true);
            }
            ui.showSecondary("NO VEO EL CÓDIGO", view -> showPairingHelp());
            ui.showTertiary("CANCELAR CONEXIÓN", view -> cancelConnection());
            return;
        }
        ui.showScreen(
                "3 de 3 · Ingresar código",
                pairing == PairingUiState.CONNECTING ? "Conectando…" : "Esperando el código",
                pairing == PairingUiState.CONNECTING
                        ? "No cierres Glosh. Esto tarda sólo unos segundos."
                        : "En Android, tocá Emparejar dispositivo con código.",
                "Podés ingresar los 6 números en Glosh o desde la notificación.");
        ui.clearVisual();
        ui.showSecondary("ME PERDÍ", view -> showPairingHelp());
        ui.showTertiary("CANCELAR CONEXIÓN", view -> cancelConnection());
    }

    private void renderConnected() {
        guideNotification.clear();
        ui.showScreen(
                "3 de 3 · Completado",
                "¡Listo, Glosher!",
                "Soporte ya está conectado de forma segura.",
                "Guía terminada ✓\n\nLa conexión es temporal y podés terminarla cuando quieras.");
        ui.clearVisual();
        ui.showSecondary("CANCELAR CONEXIÓN", view -> cancelConnection());
    }

    private void renderUnavailable() {
        ui.showScreen(
                "Conexión no disponible",
                "Soporte remoto no está disponible en este momento.",
                "Intentá nuevamente más tarde.",
                "No necesitás ingresar ningún dato técnico.");
        ui.clearVisual();
        ui.showPrimary("VOLVER", view -> coordinator.reset(), false);
    }

    private void openDeveloperSettings() {
        coordinator.openedDeveloperSettings();
        if (needsNotificationPermission()) {
            pendingPermissionAction = PERMISSION_DEVELOPER_SETTINGS;
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        } else {
            performOpenDeveloperSettings(true);
        }
    }

    private void performOpenDeveloperSettings(boolean notificationsAllowed) {
        if (notificationsAllowed) {
            guideNotification.show("Paso 1 de 3", coordinator.recipe().developerOptions().help().notificationCopy());
        }
        if (GuideServiceStatus.isEnabled(this)) {
            LiveGuideRuntime.setStage(GuideStage.DEV_SOFTWARE_INFO);
        }
        settingsNavigator.openAboutPhone(this);
    }

    private void prepareAndOpenWirelessDebugging() {
        if (coordinator.descriptor() == null) {
            coordinator.reset();
            return;
        }
        if (needsNotificationPermission()) {
            pendingPermissionAction = PERMISSION_START_SESSION;
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        } else {
            startSupportSession();
        }
    }

    private void startSupportSession() {
        if (RemotePairingService.getSessionState() != SessionState.IDLE) {
            render();
            return;
        }
        String descriptor = coordinator.markSessionStarted();
        if (descriptor == null) {
            coordinator.reset();
            return;
        }
        guideNotification.clear();
        if (GuideServiceStatus.isEnabled(this)) {
            LiveGuideRuntime.setStage(GuideStage.WIRELESS_DEBUGGING);
        }
        startForegroundService(new Intent(this, RemotePairingService.class)
                .setAction(RemotePairingService.ACTION_START)
                .putExtra(RemotePairingService.EXTRA_JOIN_URI, descriptor));
        if (getIntent() != null) {
            getIntent().setData(null);
        }
        lastRenderKey = null;
        render();
        settingsNavigator.openWirelessDebugging(this);
    }

    private void openWirelessDuringSession() {
        if (GuideServiceStatus.isEnabled(this)) {
            LiveGuideRuntime.setStage(GuideStage.WIRELESS_DEBUGGING);
        }
        guideNotification.show("Paso 2 de 3", coordinator.recipe().wirelessDebugging().help().notificationCopy());
        settingsNavigator.openWirelessDebugging(this);
    }

    private void submitPairingCode(String code) {
        if (RemotePairingService.getSessionState() == SessionState.PREPARING) {
            startService(new Intent(this, RemotePairingService.class)
                    .setAction(RemotePairingService.ACTION_SUBMIT_CODE)
                    .putExtra(RemotePairingService.EXTRA_PAIRING_CODE, code));
        }
    }

    private void showPairingHelp() {
        pairingHelp = true;
        lastRenderKey = null;
        render();
    }

    private void cancelConnection() {
        guideNotification.clear();
        LiveGuideRuntime.reset();
        pairingHelp = false;
        coordinator.reset();
        if (RemotePairingService.getSessionState() != SessionState.IDLE) {
            try {
                startService(new Intent(this, RemotePairingService.class)
                        .setAction(RemotePairingService.ACTION_STOP));
            } catch (Throwable ignored) {
                // The session may already have closed itself.
            }
        }
        lastRenderKey = null;
        handler.postDelayed(this::render, 250);
    }

    private boolean needsNotificationPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED;
    }

    private void activateGuide() {
        LiveGuideRuntime.beginPermission(
                coordinator.profile().family(),
                settingsPackageResolver.resolve(this));
        settingsNavigator.openAccessibility(this);
    }

    private void continueWithoutGuide() {
        LiveGuideRuntime.reset();
        coordinator.guideReady();
    }

    private void synchronizeGuidePermission() {
        if (coordinator.step() != OnboardingState.Step.GUIDE_PERMISSION) {
            return;
        }
        if (GuideServiceStatus.isEnabled(this)) {
            LiveGuideRuntime.beginPermission(
                    coordinator.profile().family(),
                    settingsPackageResolver.resolve(this));
            LiveGuideRuntime.guideEnabled();
            coordinator.guideReady();
            LiveGuideRuntime.setStage(GuideStage.AUTOPILOT_PROBE);
        }
    }

    private String guideFallbackCopy() {
        if (LiveGuideRuntime.isActive() && !GuideServiceStatus.isEnabled(this)) {
            return "\n\nLa guía en pantalla se desactivó. Podés continuar igual.";
        }
        return "";
    }

    private void consumeDebugIntent(Intent intent) {
        GuideOnlyDebugIntentHandler.consume(
                this, intent, coordinator, settingsNavigator, settingsPackageResolver);
    }
}
