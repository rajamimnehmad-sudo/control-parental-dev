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
import android.provider.Settings;
import android.view.WindowManager;

import com.glosh.remote.spike.broker.SupportSessionCoordinator;
import com.glosh.remote.spike.session.PairingUiState;
import com.glosh.remote.spike.session.SessionState;
import com.glosh.remote.spike.wizard.GuideNotification;
import com.glosh.remote.spike.wizard.GuideOverlayController;
import com.glosh.remote.spike.wizard.OemFamily;
import com.glosh.remote.spike.wizard.OnboardingState;
import com.glosh.remote.spike.wizard.SamsungGuideStep;
import com.glosh.remote.spike.wizard.SamsungGuideStore;
import com.glosh.remote.spike.wizard.SettingsNavigator;
import com.glosh.remote.spike.wizard.WizardLayout;

/** Samsung-first guided entry point. Accessibility is intentionally not part of this flow. */
public final class MainActivity extends Activity implements SupportSessionCoordinator.Listener {
    public static final String ACTION_GUIDE_OPEN = "com.glosh.remote.spike.GUIDE_OPEN";
    public static final String ACTION_GUIDE_BACK = "com.glosh.remote.spike.GUIDE_BACK";
    public static final String ACTION_GUIDE_NEXT = "com.glosh.remote.spike.GUIDE_NEXT";

    private static final int REQUEST_NOTIFICATIONS = 9001;
    private static final long STATE_REFRESH_MS = 500L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SettingsNavigator settingsNavigator = new SettingsNavigator();
    private final Runnable refreshState = new Runnable() {
        @Override
        public void run() {
            synchronizeRuntimeProgress();
            if (guideOverlay != null && guideOverlay.isVisible()) {
                refreshOverlaySurface();
            } else {
                render();
            }
            handler.postDelayed(this, STATE_REFRESH_MS);
        }
    };

    private SupportSessionCoordinator coordinator;
    private SamsungGuideStore guideStore;
    private GuideNotification guideNotification;
    private GuideOverlayController guideOverlay;
    private WizardLayout ui;
    private String lastRenderKey;
    private boolean awaitingNotificationPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Release builds protect sensitive support UI. DEV deliberately allows screenshots so
        // physical UX can be reviewed without weakening the eventual production configuration.
        if (!BuildConfig.DEBUG) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }

        coordinator = SupportSessionCoordinator.get(this);
        guideStore = new SamsungGuideStore(this);
        guideNotification = new GuideNotification(this);
        ui = new WizardLayout(this);
        guideOverlay = new GuideOverlayController(this, new GuideOverlayController.Listener() {
            @Override
            public void onBack() {
                guideBack(true);
            }

            @Override
            public void onNext() {
                guideNext(true);
            }
        });
        setContentView(ui.view());
        consumeIntent(getIntent());

        // A previous Accessibility/PiP build may have left a stale wizard checkpoint.
        if (RemotePairingService.getSessionState() == SessionState.IDLE
                && !guideStore.active()
                && coordinator.step() != OnboardingState.Step.HOME
                && coordinator.step() != OnboardingState.Step.UNAVAILABLE) {
            coordinator.reset();
        }
        render();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        consumeIntent(intent);
        lastRenderKey = null;
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        guideOverlay.hide();
        coordinator.attach(this);
        ui.onHostResume();
        handler.removeCallbacks(refreshState);
        handler.post(refreshState);

        if (guideStore.active()
                && coordinator.step() == OnboardingState.Step.HOME
                && !needsNotificationPermission()) {
            if (needsOverlayPermission()) {
                lastRenderKey = null;
                renderOverlayPermission();
            } else {
                beginSupportDiscovery();
            }
        }
    }

    @Override
    protected void onPause() {
        ui.onHostPause();
        if (!guideOverlay.isVisible()) {
            handler.removeCallbacks(refreshState);
            coordinator.detach(this);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(refreshState);
        coordinator.detach(this);
        guideOverlay.hide();
        super.onDestroy();
    }

    @Override
    public void onStateChanged() {
        handler.post(() -> {
            synchronizeRuntimeProgress();
            if (guideOverlay.isVisible()) {
                refreshOverlaySurface();
            } else {
                lastRenderKey = null;
                render();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_NOTIFICATIONS || !awaitingNotificationPermission) {
            return;
        }
        awaitingNotificationPermission = false;
        continueGuideSetup();
    }

    private void render() {
        SessionState session = RemotePairingService.getSessionState();
        PairingUiState pairing = RemotePairingService.getPairingUiState();
        OnboardingState.Step onboarding = coordinator.step();
        SamsungGuideStep guide = guideStore.step();
        String key = session + ":" + pairing + ":" + onboarding + ":" + guideStore.active()
                + ":" + guide + ":overlay=" + !needsOverlayPermission();
        if (key.equals(lastRenderKey)) {
            return;
        }
        lastRenderKey = key;

        if (coordinator.profile().family() != OemFamily.SAMSUNG) {
            guideNotification.clear();
            ui.showUnsupported(coordinator.profile().manufacturer());
            return;
        }

        if (session == SessionState.CONNECTED) {
            renderConnected();
            return;
        }

        if (guideStore.active() && needsOverlayPermission()) {
            renderOverlayPermission();
            return;
        }

        if (session == SessionState.IDLE
                && onboarding == OnboardingState.Step.SESSION_ACTIVE) {
            coordinator.reset();
            guideStore.clear();
            guideNotification.clear();
            renderHome();
            return;
        }

        if (onboarding == OnboardingState.Step.CHECKING_SUPPORT) {
            renderCheckingSupport();
            return;
        }
        if (onboarding == OnboardingState.Step.UNAVAILABLE) {
            renderUnavailable();
            return;
        }

        if (guideStore.active()) {
            renderSamsungGuide(guide, session, pairing, onboarding);
        } else {
            renderHome();
        }
    }

    private void renderHome() {
        guideOverlay.hide();
        guideNotification.clear();
        ui.showHome(view -> startSamsungGuide());
    }

    private void renderOverlayPermission() {
        guideOverlay.hide();
        guideNotification.clear();
        ui.showScreen(
                "Una sola vez",
                "Permití la guía flotante",
                "Glosh necesita “Mostrar sobre otras apps” para dejar una tarjeta pequeña encima de los Ajustes reales de Samsung.",
                "Este permiso no toca Ajustes por vos ni lee la pantalla. Activá “Permitir” para Glosh Remote y, al volver, continuamos automáticamente.");
        ui.clearVisual();
        ui.showPrimary("PERMITIR GUÍA FLOTANTE", view -> requestOverlayPermission(), true);
        ui.showTertiary("CANCELAR", view -> cancelConnection());
    }

    private void renderCheckingSupport() {
        ui.showScreen(
                "Preparando",
                "Buscando soporte",
                "Estamos comprobando que haya una consola de soporte disponible. Esto normalmente tarda unos segundos.",
                "Todavía no tenés que cambiar nada en el teléfono.");
        ui.clearVisual();
        ui.showTertiary("CANCELAR", view -> cancelConnection());
    }

    private void renderUnavailable() {
        guideNotification.clear();
        ui.showScreen(
                "",
                "Soporte no disponible",
                "No encontramos una consola de soporte activa en este momento.",
                "Podés reintentar sin configurar nada del teléfono.");
        ui.clearVisual();
        ui.showPrimary("REINTENTAR", view -> {
            coordinator.reset();
            guideStore.begin();
            continueGuideSetup();
        }, false);
        ui.showTertiary("CANCELAR", view -> cancelConnection());
    }

    private void renderSamsungGuide(
            SamsungGuideStep step,
            SessionState session,
            PairingUiState pairing,
            OnboardingState.Step onboarding) {
        if (session == SessionState.PREPARING && pairing == PairingUiState.CONNECTING) {
            guideNotification.clear();
            ui.showScreen(
                    "Paso 7 de 7",
                    "Conectando…",
                    "Código recibido. Glosh está emparejando ADB local y abriendo el canal seguro con soporte.",
                    "No cierres Glosh. Este paso continúa automáticamente.");
            ui.clearVisual();
            ui.showTertiary("CANCELAR", view -> cancelConnection());
            return;
        }

        if (step == SamsungGuideStep.ENTER_CODE) {
            renderCodeStep(pairing);
            return;
        }

        String info = "Cuando abras Ajustes, Glosh deja una tarjeta pequeña arriba con la instrucción, Atrás y Ya está. Podés arrastrarla desde el encabezado.";
        if (step == SamsungGuideStep.WIRELESS_DEBUGGING
                && onboarding == OnboardingState.Step.REQUESTING_SUPPORT) {
            info = "Podés ir dejando abierta Depuración inalámbrica. Glosh está preparando la sesión de soporte en segundo plano.";
        }
        ui.showSamsungStep(step, info);
        guideNotification.showStep(step);

        ui.showPrimary(openSettingsLabel(step), view -> openSettingsForGuide(), true);
        ui.showSecondary(step.confirmLabel(), view -> guideNext(false));
        ui.showTertiary("ATRÁS", view -> guideBack(false));
    }

    private void renderCodeStep(PairingUiState pairing) {
        guideNotification.clear();
        String info = "La notificación de Glosh permite responder los 6 números sin salir de Ajustes. Si no aparece, escribilos acá.";
        ui.showSamsungStep(SamsungGuideStep.ENTER_CODE, info);
        boolean retry = pairing == PairingUiState.CODE_FAILED;
        if (pairing == PairingUiState.WAITING_FOR_CODE || retry) {
            ui.showPairingInput(this::submitPairingCode, retry);
        } else if (pairing == PairingUiState.DISCOVERING_ENDPOINT) {
            ui.showScreen(
                    "Paso 7 de 7",
                    "Esperando el código",
                    "En Depuración inalámbrica tocá “Vincular dispositivo con código”. Cuando Android muestre los seis números, Glosh habilita el ingreso automáticamente.",
                    info);
            ui.showGuide(SamsungGuideStep.PAIR_DEVICE.visual());
        }
        ui.showPrimary("VOLVER A DEPURACIÓN INALÁMBRICA", view -> openWirelessSettings(), false);
        ui.showTertiary("ATRÁS", view -> guideBack(false));
    }

    private void renderConnected() {
        guideOverlay.hide();
        guideNotification.clear();
        guideStore.clear();
        ui.showScreen(
                "Completado",
                "Conectado con soporte",
                "La conexión segura y temporal ya está activa.",
                "Podés finalizarla en cualquier momento. No queda ADB público ni acceso permanente.");
        ui.clearVisual();
        ui.showSecondary("FINALIZAR CONEXIÓN", view -> cancelConnection());
    }

    private void startSamsungGuide() {
        guideStore.begin();
        lastRenderKey = null;
        if (needsNotificationPermission()) {
            awaitingNotificationPermission = true;
            requestPermissions(
                    new String[] {Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS);
            return;
        }
        continueGuideSetup();
    }

    private void continueGuideSetup() {
        if (needsOverlayPermission()) {
            lastRenderKey = null;
            renderOverlayPermission();
            return;
        }
        beginSupportDiscovery();
    }

    private void beginSupportDiscovery() {
        if (coordinator.step() == OnboardingState.Step.HOME
                || coordinator.step() == OnboardingState.Step.UNAVAILABLE) {
            coordinator.requestSupport();
        } else {
            lastRenderKey = null;
            render();
        }
    }

    private void guideNext(boolean fromOverlay) {
        SamsungGuideStep current = guideStore.step();
        if (current == SamsungGuideStep.ENTER_CODE) {
            openGloshFromOverlay();
            return;
        }

        if (current == SamsungGuideStep.BUILD_NUMBER) {
            if (coordinator.step() == OnboardingState.Step.GUIDE_PERMISSION) {
                coordinator.guideReady();
            }
            if (coordinator.step() == OnboardingState.Step.DEVELOPER_OPTIONS) {
                coordinator.confirmDeveloperOptions();
            }
            guideStore.setStep(SamsungGuideStep.DEVELOPER_OPTIONS);
            syncGuideSurfaces(fromOverlay);
            return;
        }

        if (current == SamsungGuideStep.WIRELESS_DEBUGGING) {
            if (RemotePairingService.getSessionState() == SessionState.IDLE) {
                if (coordinator.step() == OnboardingState.Step.REQUESTING_SUPPORT) {
                    String waiting = "Esperá un momento: estamos preparando la sesión segura con soporte.";
                    guideNotification.showWaiting(current, waiting);
                    if (fromOverlay) {
                        guideOverlay.showWaiting(current, waiting);
                    } else {
                        lastRenderKey = null;
                        render();
                    }
                    return;
                }
                if (!startSupportSession()) {
                    String waiting = "Todavía estamos preparando soporte. Probá nuevamente en unos segundos.";
                    guideNotification.showWaiting(current, waiting);
                    if (fromOverlay) {
                        guideOverlay.showWaiting(current, waiting);
                    }
                    return;
                }
            }
            guideStore.setStep(SamsungGuideStep.PAIR_DEVICE);
            syncGuideSurfaces(fromOverlay);
            return;
        }

        if (current == SamsungGuideStep.PAIR_DEVICE) {
            guideStore.setStep(SamsungGuideStep.ENTER_CODE);
            guideNotification.clear();
            syncGuideSurfaces(fromOverlay);
            return;
        }

        guideStore.setStep(current.next());
        syncGuideSurfaces(fromOverlay);
    }

    private void guideBack(boolean fromOverlay) {
        SamsungGuideStep current = guideStore.step();
        if (!current.canGoBack()) {
            if (fromOverlay) {
                openGloshFromOverlay();
            } else {
                cancelConnection();
            }
            return;
        }
        guideStore.setStep(current.previous());
        syncGuideSurfaces(fromOverlay);
    }

    private void syncGuideSurfaces(boolean fromOverlay) {
        SamsungGuideStep step = guideStore.step();
        if (RemotePairingService.getSessionState() == SessionState.IDLE) {
            guideNotification.showStep(step);
        }
        if (guideOverlay.isVisible() || fromOverlay) {
            guideOverlay.updateStep(step);
        }
        lastRenderKey = null;
        if (!guideOverlay.isVisible()) {
            render();
        }
    }

    private void openSettingsForGuide() {
        SamsungGuideStep step = guideStore.step();
        if (!guideOverlay.show(step)) {
            lastRenderKey = null;
            renderOverlayPermission();
            return;
        }
        guideNotification.showStep(step);
        settingsNavigator.openForStep(this, step);
    }

    private void openWirelessSettings() {
        if (!guideOverlay.show(guideStore.step())) {
            lastRenderKey = null;
            renderOverlayPermission();
            return;
        }
        guideNotification.clear();
        settingsNavigator.openWirelessDebugging(this);
    }

    private boolean startSupportSession() {
        if (RemotePairingService.getSessionState() != SessionState.IDLE) {
            return true;
        }
        String descriptor = coordinator.markSessionStarted();
        if (descriptor == null) {
            return false;
        }
        guideNotification.clear();
        startForegroundService(new Intent(this, RemotePairingService.class)
                .setAction(RemotePairingService.ACTION_START)
                .putExtra(RemotePairingService.EXTRA_JOIN_URI, descriptor));
        if (getIntent() != null) {
            getIntent().setData(null);
        }
        return true;
    }

    private void submitPairingCode(String code) {
        if (RemotePairingService.getSessionState() == SessionState.PREPARING) {
            startService(new Intent(this, RemotePairingService.class)
                    .setAction(RemotePairingService.ACTION_SUBMIT_CODE)
                    .putExtra(RemotePairingService.EXTRA_PAIRING_CODE, code));
        }
    }

    private void synchronizeRuntimeProgress() {
        SessionState session = RemotePairingService.getSessionState();
        PairingUiState pairing = RemotePairingService.getPairingUiState();
        if (session == SessionState.CONNECTED) {
            guideOverlay.hide();
            return;
        }
        if (!guideStore.active() || session != SessionState.PREPARING) {
            return;
        }
        if ((pairing == PairingUiState.WAITING_FOR_CODE
                || pairing == PairingUiState.CODE_FAILED
                || pairing == PairingUiState.CONNECTING)
                && guideStore.step().ordinal() < SamsungGuideStep.ENTER_CODE.ordinal()) {
            guideStore.setStep(SamsungGuideStep.ENTER_CODE);
            guideNotification.clear();
            if (guideOverlay.isVisible()) {
                guideOverlay.updateStep(SamsungGuideStep.ENTER_CODE);
            }
            lastRenderKey = null;
        }
    }

    private void refreshOverlaySurface() {
        SessionState session = RemotePairingService.getSessionState();
        PairingUiState pairing = RemotePairingService.getPairingUiState();
        if (session == SessionState.CONNECTED || !guideStore.active()) {
            guideOverlay.hide();
            return;
        }
        SamsungGuideStep step = guideStore.step();
        if (session == SessionState.PREPARING && pairing == PairingUiState.CONNECTING) {
            guideOverlay.showWaiting(step, "Código recibido. Glosh está completando la conexión segura…");
        } else {
            guideOverlay.updateStep(step);
        }
    }

    private void cancelConnection() {
        guideOverlay.hide();
        guideNotification.clear();
        guideStore.clear();
        coordinator.reset();
        if (RemotePairingService.getSessionState() != SessionState.IDLE) {
            try {
                startService(new Intent(this, RemotePairingService.class)
                        .setAction(RemotePairingService.ACTION_STOP));
            } catch (Throwable ignored) {
                // Session may already be closing.
            }
        }
        lastRenderKey = null;
        handler.postDelayed(this::render, 200L);
    }

    private boolean needsNotificationPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED;
    }

    private boolean needsOverlayPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this);
    }

    private void requestOverlayPermission() {
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        try {
            startActivity(intent);
        } catch (Throwable firstFailure) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            } catch (Throwable ignored) {
                // The permission screen is a platform surface; render() will remain on the safe gate.
            }
        }
    }

    private void consumeIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (ACTION_GUIDE_BACK.equals(action)) {
            intent.setAction(null);
            guideBack(false);
            return;
        }
        if (ACTION_GUIDE_NEXT.equals(action)) {
            intent.setAction(null);
            guideNext(false);
            return;
        }
        if (ACTION_GUIDE_OPEN.equals(action)) {
            intent.setAction(null);
            return;
        }
        if (Intent.ACTION_VIEW.equals(action)
                && intent.getData() != null
                && "gloshremote".equals(intent.getData().getScheme())
                && RemotePairingService.getSessionState() == SessionState.IDLE
                && coordinator.step() == OnboardingState.Step.HOME) {
            try {
                coordinator.seedDebugDescriptor(intent.getData().toString());
                guideStore.begin();
                guideStore.setStep(SamsungGuideStep.WIRELESS_DEBUGGING);
            } catch (Throwable ignored) {
                // Invalid debug descriptor is ignored; normal broker flow remains available.
            }
        }
    }

    private void openGloshFromOverlay() {
        Intent intent = new Intent(this, MainActivity.class)
                .setAction(ACTION_GUIDE_OPEN)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }

    private static String openSettingsLabel(SamsungGuideStep step) {
        return switch (step) {
            case ABOUT_PHONE -> "ABRIR AJUSTES";
            case SOFTWARE_INFO, BUILD_NUMBER -> "VOLVER A ACERCA DEL TELÉFONO";
            case DEVELOPER_OPTIONS -> "ABRIR OPCIONES DE DESARROLLADOR";
            case WIRELESS_DEBUGGING -> "ABRIR DEPURACIÓN INALÁMBRICA";
            case PAIR_DEVICE -> "VOLVER A DEPURACIÓN INALÁMBRICA";
            case ENTER_CODE -> "ABRIR GLOSH";
        };
    }
}
