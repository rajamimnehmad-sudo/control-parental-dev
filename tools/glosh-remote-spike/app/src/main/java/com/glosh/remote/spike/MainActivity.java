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
import com.glosh.remote.spike.guide.accessibility.GuideOnlyDebugIntentHandler;
import com.glosh.remote.spike.guide.accessibility.GuideServiceStatus;
import com.glosh.remote.spike.guide.accessibility.SettingsPackageResolver;
import com.glosh.remote.spike.guide.state.GuideStage;
import com.glosh.remote.spike.guide.state.LiveGuideRuntime;
import com.glosh.remote.spike.session.PairingUiState;
import com.glosh.remote.spike.session.SessionState;
import com.glosh.remote.spike.wizard.GuideNotification;
import com.glosh.remote.spike.wizard.GuidePresentation;
import com.glosh.remote.spike.wizard.OnboardingState;
import com.glosh.remote.spike.wizard.RestrictedSettingsPreflight;
import com.glosh.remote.spike.wizard.SettingsNavigator;
import com.glosh.remote.spike.wizard.WizardLayout;

public final class MainActivity extends Activity implements SupportSessionCoordinator.Listener {
    private static final int REQUEST_NOTIFICATIONS = 9001;
    private static final int PERMISSION_NONE = 0;
    private static final int PERMISSION_OPEN_ACCESSIBILITY = 1;
    private static final int PERMISSION_OPEN_DEVELOPER = 2;
    private static final int PERMISSION_START_SESSION = 3;
    private static final int PERMISSION_OPEN_RESTRICTED_SETTINGS = 4;
    private static final long STATE_REFRESH_MS = 500L;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        coordinator = SupportSessionCoordinator.get(this);
        guideNotification = new GuideNotification(this);
        ui = new WizardLayout(this);
        setContentView(ui.view());
        if (savedInstanceState != null) {
            pendingPermissionAction = savedInstanceState.getInt(
                    "pending_permission",
                    PERMISSION_NONE);
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
        super.onSaveInstanceState(state);
    }

    @Override
    public void onStateChanged() {
        lastRenderKey = null;
        render();
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
        int action = pendingPermissionAction;
        pendingPermissionAction = PERMISSION_NONE;
        if (action == PERMISSION_OPEN_ACCESSIBILITY) {
            beginGuideAndOpenAccessibility();
        } else if (action == PERMISSION_OPEN_DEVELOPER) {
            performOpenDeveloperSettings();
        } else if (action == PERMISSION_START_SESSION) {
            startSupportSession();
        } else if (action == PERMISSION_OPEN_RESTRICTED_SETTINGS) {
            performOpenRestrictedSettingsInfo();
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
        boolean restrictedPreflight = RestrictedSettingsPreflight.required(this);
        String key = session + ":" + pairing + ":" + step + ":"
                + GuideServiceStatus.isEnabled(this) + ":" + LiveGuideRuntime.stage()
                + ":restricted=" + restrictedPreflight;
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
        GuidePresentation presentation = GuidePresentation.preparing(
                "Buscando soporte",
                "Estamos comprobando que haya una consola de soporte disponible.");
        showPresentation(presentation,
                "Todavía no tenés que cambiar ninguna opción del teléfono.");
        ui.showSecondary("CANCELAR", view -> coordinator.reset());
    }

    private void renderGuidePermission() {
        if (RestrictedSettingsPreflight.required(this)) {
            GuidePresentation presentation = GuidePresentation.restrictedSettings();
            showPresentation(presentation,
                    "Esto aparece en Android 13 o superior cuando la app fue instalada manualmente. Se hace una sola vez.");
            guideNotification.show(presentation);
            ui.showPrimary(
                    "1 · ABRIR INFORMACIÓN DE LA APP",
                    view -> openRestrictedSettingsInfo(),
                    true);
            ui.showSecondary(
                    "2 · YA LO PERMITÍ · CONTINUAR",
                    view -> confirmRestrictedSettingsAndContinue());
            ui.showTertiary("CANCELAR", view -> coordinator.reset());
            return;
        }

        GuidePresentation presentation = GuidePresentation.forStage(
                GuideStage.GUIDE_PERMISSION,
                "Activá el interruptor de Glosh Remote. Al hacerlo, seguimos automáticamente.");
        showPresentation(presentation,
                "Glosh observa únicamente las pantallas de Ajustes necesarias durante esta sesión.");
        guideNotification.show(presentation);
        ui.showPrimary("ABRIR ACCESIBILIDAD", view -> activateGuide(), true);
        ui.showTertiary("CANCELAR", view -> coordinator.reset());
    }

    private void renderDeveloperOptions() {
        GuideStage stage = normalizedDeveloperStage();
        GuidePresentation presentation = GuidePresentation.forStage(stage, null);
        showPresentation(presentation,
                "En Ajustes verás la misma indicación en una tarjeta pequeña. Vos tocás; Glosh detecta y avanza.");
        guideNotification.show(presentation);
        ui.showPrimary("VOLVER A AJUSTES", view -> returnToDeveloperStep(stage), false);
        ui.showTertiary("CANCELAR", view -> cancelConnection());
    }

    private void renderRequestingSupport() {
        GuidePresentation presentation = GuidePresentation.forStage(
                GuideStage.WIRELESS_DEBUGGING,
                "Podés activar Depuración inalámbrica mientras Glosh prepara la sesión segura.");
        showPresentation(presentation,
                "La solicitud de soporte se mantiene activa automáticamente.");
        guideNotification.show(presentation);
        ui.showPrimary(
                "ABRIR DEPURACIÓN INALÁMBRICA",
                view -> openWirelessDuringSession(),
                false);
        ui.showTertiary("CANCELAR", view -> cancelConnection());
    }

    private void renderWirelessDebugging() {
        GuidePresentation presentation = GuidePresentation.forStage(
                GuideStage.WIRELESS_DEBUGGING,
                "Activá el interruptor. Cuando quede listo, Glosh mostrará el paso del código.");
        showPresentation(presentation,
                "Glosh intenta la ruta directa una sola vez; si Samsung no la admite, te guía en la lista sin hacer scroll por vos.");
        guideNotification.show(presentation);
        ui.showPrimary(
                "ABRIR DEPURACIÓN INALÁMBRICA",
                view -> prepareAndOpenWirelessDebugging(),
                false);
        ui.showTertiary("CANCELAR", view -> cancelConnection());
    }

    private void renderPairing(PairingUiState pairing) {
        guideNotification.clear();
        if (pairing == PairingUiState.CONNECTING) {
            GuidePresentation presentation = GuidePresentation.forStage(
                    GuideStage.PAIRING,
                    "Glosh ya recibió el código y está terminando la conexión.");
            showPresentation(presentation,
                    "No cierres Glosh; normalmente tarda sólo unos segundos.");
            ui.showTertiary("CANCELAR", view -> cancelConnection());
            return;
        }

        boolean retry = pairing == PairingUiState.CODE_FAILED;
        String instruction = retry
                ? "El código anterior venció. Abrí uno nuevo y escribilo acá."
                : "Glosh intenta leer los seis dígitos automáticamente. También podés escribirlos acá.";
        GuidePresentation presentation = GuidePresentation.forStage(
                GuideStage.PAIR_CODE_TARGET,
                instruction);
        showPresentation(presentation,
                "La notificación de Glosh también permite ingresar el código sin volver a la app.");
        if (pairing == PairingUiState.WAITING_FOR_CODE || retry) {
            ui.showPairingInput(this::submitPairingCode, retry);
        }
        ui.showPrimary(
                "ABRIR PANTALLA DEL CÓDIGO",
                view -> openWirelessDuringSession(),
                false);
        ui.showTertiary("CANCELAR", view -> cancelConnection());
    }

    private void renderConnected() {
        guideNotification.clear();
        GuidePresentation presentation = GuidePresentation.forStage(
                GuideStage.CONNECTED,
                "La conexión segura y temporal ya está activa.");
        showPresentation(presentation,
                "Podés finalizarla en cualquier momento desde este botón.");
        ui.showSecondary("FINALIZAR CONEXIÓN", view -> cancelConnection());
    }

    private void renderUnavailable() {
        guideNotification.clear();
        ui.showScreen(
                "",
                "Soporte no disponible",
                "No encontramos una consola de soporte activa en este momento.",
                "Podés reintentar sin volver a configurar el teléfono.");
        ui.clearVisual();
        ui.showPrimary("REINTENTAR", view -> {
            coordinator.reset();
            handler.post(coordinator::requestSupport);
        }, false);
        ui.showSecondary("CANCELAR", view -> coordinator.reset());
    }

    private void showPresentation(GuidePresentation presentation, String information) {
        ui.showScreen(
                presentation.progressLabel(),
                presentation.title(),
                presentation.body(),
                information);
        ui.showPresentation(presentation);
    }

    private GuideStage normalizedDeveloperStage() {
        GuideStage stage = LiveGuideRuntime.stage();
        return switch (stage) {
            case DEV_ABOUT_PHONE, DEV_SOFTWARE_INFO, DEV_BUILD_NUMBER,
                    AUTOPILOT_CREDENTIAL, AUTOPILOT_FALLBACK -> stage;
            default -> GuideStage.AUTOPILOT_PROBE;
        };
    }

    private void returnToDeveloperStep(GuideStage stage) {
        if (stage == GuideStage.DEV_ABOUT_PHONE
                || stage == GuideStage.DEV_SOFTWARE_INFO
                || stage == GuideStage.DEV_BUILD_NUMBER) {
            settingsNavigator.openAboutPhone(this);
        } else {
            openDeveloperSettings();
        }
    }

    private void openDeveloperSettings() {
        coordinator.openedDeveloperSettings();
        if (needsNotificationPermission()) {
            pendingPermissionAction = PERMISSION_OPEN_DEVELOPER;
            requestNotificationPermission();
        } else {
            performOpenDeveloperSettings();
        }
    }

    private void performOpenDeveloperSettings() {
        GuidePresentation presentation = GuidePresentation.waiting(
                GuideStage.AUTOPILOT_PROBE,
                "Verificando el estado de las opciones de desarrollador…");
        guideNotification.show(presentation);
        if (GuideServiceStatus.isEnabled(this)) {
            LiveGuideRuntime.setStage(GuideStage.AUTOPILOT_PROBE);
            return;
        }
        settingsNavigator.openDeveloperOptions(this);
    }

    private void prepareAndOpenWirelessDebugging() {
        GuidePresentation presentation = GuidePresentation.waiting(
                GuideStage.WIRELESS_DEBUGGING,
                "Abriendo Depuración inalámbrica…");
        guideNotification.show(presentation);
        if (GuideServiceStatus.isEnabled(this)) {
            LiveGuideRuntime.setStage(GuideStage.WIRELESS_DEBUGGING);
            return;
        }
        if (coordinator.descriptor() != null
                && RemotePairingService.getSessionState() == SessionState.IDLE) {
            if (needsNotificationPermission()) {
                pendingPermissionAction = PERMISSION_START_SESSION;
                requestNotificationPermission();
            } else {
                startSupportSession();
            }
        } else {
            settingsNavigator.openWirelessDebugging(this);
        }
    }

    private void startSupportSession() {
        if (RemotePairingService.getSessionState() != SessionState.IDLE) {
            settingsNavigator.openWirelessDebugging(this);
            render();
            return;
        }
        String descriptor = coordinator.markSessionStarted();
        if (descriptor == null) {
            settingsNavigator.openWirelessDebugging(this);
            return;
        }
        guideNotification.clear();
        LiveGuideRuntime.setStage(GuideStage.WIRELESS_DEBUGGING);
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
        prepareAndOpenWirelessDebugging();
    }

    private void submitPairingCode(String code) {
        if (RemotePairingService.getSessionState() == SessionState.PREPARING) {
            startService(new Intent(this, RemotePairingService.class)
                    .setAction(RemotePairingService.ACTION_SUBMIT_CODE)
                    .putExtra(RemotePairingService.EXTRA_PAIRING_CODE, code));
        }
    }

    private void cancelConnection() {
        guideNotification.clear();
        LiveGuideRuntime.reset();
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
        handler.postDelayed(this::render, 250L);
    }

    private boolean needsNotificationPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED;
    }

    private void requestNotificationPermission() {
        requestPermissions(
                new String[] {Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_NOTIFICATIONS);
    }

    private void openRestrictedSettingsInfo() {
        if (needsNotificationPermission()) {
            pendingPermissionAction = PERMISSION_OPEN_RESTRICTED_SETTINGS;
            requestNotificationPermission();
        } else {
            performOpenRestrictedSettingsInfo();
        }
    }

    private void performOpenRestrictedSettingsInfo() {
        GuidePresentation presentation = GuidePresentation.restrictedSettings();
        guideNotification.show(presentation);
        settingsNavigator.openAppDetails(this);
    }

    private void confirmRestrictedSettingsAndContinue() {
        RestrictedSettingsPreflight.confirm(this);
        lastRenderKey = null;
        activateGuide();
    }

    private void activateGuide() {
        if (RestrictedSettingsPreflight.required(this)) {
            lastRenderKey = null;
            render();
            return;
        }
        if (needsNotificationPermission()) {
            pendingPermissionAction = PERMISSION_OPEN_ACCESSIBILITY;
            requestNotificationPermission();
        } else {
            beginGuideAndOpenAccessibility();
        }
    }

    private void beginGuideAndOpenAccessibility() {
        LiveGuideRuntime.beginPermission(
                coordinator.profile().family(),
                settingsPackageResolver.resolve(this));
        guideNotification.show(GuidePresentation.forStage(
                GuideStage.GUIDE_PERMISSION,
                "Activá el interruptor de Glosh Remote. Después seguimos automáticamente."));
        settingsNavigator.openAccessibility(this);
    }

    private void synchronizeGuidePermission() {
        if (coordinator.step() != OnboardingState.Step.GUIDE_PERMISSION
                || !GuideServiceStatus.isEnabled(this)) {
            return;
        }
        LiveGuideRuntime.beginPermission(
                coordinator.profile().family(),
                settingsPackageResolver.resolve(this));
        LiveGuideRuntime.guideEnabled();
        coordinator.guideReady();
        LiveGuideRuntime.setStage(GuideStage.AUTOPILOT_PROBE);
        guideNotification.show(GuidePresentation.waiting(
                GuideStage.AUTOPILOT_PROBE,
                "Verificando si el modo desarrollador ya está activado…"));
    }

    private void consumeDebugIntent(Intent intent) {
        GuideOnlyDebugIntentHandler.consume(
                this,
                intent,
                coordinator,
                settingsNavigator,
                settingsPackageResolver);
    }
}
