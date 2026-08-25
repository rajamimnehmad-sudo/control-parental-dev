package com.glosh.remote.spike;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Rational;
import android.view.WindowManager;

import com.glosh.remote.spike.broker.SupportSessionCoordinator;
import com.glosh.remote.spike.session.PairingUiState;
import com.glosh.remote.spike.session.SessionState;
import com.glosh.remote.spike.wizard.GuideNotification;
import com.glosh.remote.spike.wizard.OemFamily;
import com.glosh.remote.spike.wizard.OnboardingState;
import com.glosh.remote.spike.wizard.SamsungGuideStep;
import com.glosh.remote.spike.wizard.SamsungGuideStore;
import com.glosh.remote.spike.wizard.SamsungPipCoachView;
import com.glosh.remote.spike.wizard.SettingsNavigator;
import com.glosh.remote.spike.wizard.WizardLayout;

import java.util.ArrayList;
import java.util.List;

/** Samsung-first guided entry point. Accessibility is intentionally not part of this flow. */
public final class MainActivity extends Activity implements SupportSessionCoordinator.Listener {
    public static final String ACTION_GUIDE_OPEN = "com.glosh.remote.spike.GUIDE_OPEN";
    public static final String ACTION_GUIDE_BACK = "com.glosh.remote.spike.GUIDE_BACK";
    public static final String ACTION_GUIDE_NEXT = "com.glosh.remote.spike.GUIDE_NEXT";

    private static final String ACTION_PIP_BACK = "com.glosh.remote.spike.PIP_BACK";
    private static final String ACTION_PIP_NEXT = "com.glosh.remote.spike.PIP_NEXT";
    private static final String ACTION_PIP_OPEN = "com.glosh.remote.spike.PIP_OPEN";
    private static final int REQUEST_NOTIFICATIONS = 9001;
    private static final long STATE_REFRESH_MS = 500L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SettingsNavigator settingsNavigator = new SettingsNavigator();
    private final Runnable refreshState = new Runnable() {
        @Override
        public void run() {
            synchronizeRuntimeProgress();
            render();
            handler.postDelayed(this, STATE_REFRESH_MS);
        }
    };

    private final BroadcastReceiver pipActions = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent == null ? null : intent.getAction();
            if (ACTION_PIP_BACK.equals(action)) {
                guideBack(true);
            } else if (ACTION_PIP_NEXT.equals(action)) {
                guideNext(true);
            } else if (ACTION_PIP_OPEN.equals(action)) {
                openGloshFromPip();
            }
        }
    };

    private SupportSessionCoordinator coordinator;
    private SamsungGuideStore guideStore;
    private GuideNotification guideNotification;
    private WizardLayout ui;
    private SamsungPipCoachView pipView;
    private String lastRenderKey;
    private boolean awaitingNotificationPermission;
    private boolean launchingSettings;
    private boolean pipReceiverRegistered;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        coordinator = SupportSessionCoordinator.get(this);
        guideStore = new SamsungGuideStore(this);
        guideNotification = new GuideNotification(this);
        ui = new WizardLayout(this);
        pipView = new SamsungPipCoachView(this);
        setContentView(ui.view());
        registerPipActions();
        consumeIntent(getIntent());

        // A previous Accessibility-based build may have left a stale wizard checkpoint.
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
        coordinator.attach(this);
        if (!isInPictureInPictureMode()) {
            ui.onHostResume();
        }
        handler.removeCallbacks(refreshState);
        handler.post(refreshState);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refreshState);
        if (!isInPictureInPictureMode()) {
            ui.onHostPause();
        }
        coordinator.detach(this);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (pipReceiverRegistered) {
            try {
                unregisterReceiver(pipActions);
            } catch (Throwable ignored) {
                // Activity is already being torn down.
            }
            pipReceiverRegistered = false;
        }
        pipView.stop();
        super.onDestroy();
    }

    @Override
    public void onPictureInPictureModeChanged(
            boolean isInPictureInPictureMode,
            Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            ui.onHostPause();
            pipView.setStep(guideStore.step());
            pipView.start();
            setContentView(pipView);
            updatePipParams(guideStore.step(), false);
        } else {
            pipView.stop();
            setContentView(ui.view());
            ui.onHostResume();
            lastRenderKey = null;
            render();
        }
    }

    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (!launchingSettings
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || isInPictureInPictureMode()) {
            return;
        }
        try {
            enterPictureInPictureMode(buildPipParams(guideStore.step(), false));
        } catch (Throwable ignored) {
            // Android 12+ also has auto-enter enabled; notification remains the final fallback.
        }
    }

    @Override
    public void onStateChanged() {
        handler.post(() -> {
            synchronizeRuntimeProgress();
            lastRenderKey = null;
            render();
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
        beginSupportDiscovery();
    }

    private void render() {
        SessionState session = RemotePairingService.getSessionState();
        PairingUiState pairing = RemotePairingService.getPairingUiState();
        OnboardingState.Step onboarding = coordinator.step();
        SamsungGuideStep guide = guideStore.step();
        String key = session + ":" + pairing + ":" + onboarding + ":" + guideStore.active() + ":" + guide;
        if (key.equals(lastRenderKey)) {
            return;
        }
        lastRenderKey = key;

        if (isInPictureInPictureMode()) {
            pipView.setStep(guide);
            updatePipParams(guide, false);
            return;
        }

        if (coordinator.profile().family() != OemFamily.SAMSUNG) {
            guideNotification.clear();
            ui.showUnsupported(coordinator.profile().manufacturer());
            return;
        }

        if (session == SessionState.CONNECTED) {
            renderConnected();
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
        guideNotification.clear();
        ui.showHome(view -> startSamsungGuide());
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
            beginSupportDiscovery();
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

        String info = "Cuando abras Ajustes, Glosh queda flotando arriba. Tocá la mini ventana para usar Atrás o “Ya está / Siguiente”.";
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

    private void guideNext(boolean fromPip) {
        SamsungGuideStep current = guideStore.step();
        if (current == SamsungGuideStep.ENTER_CODE) {
            openGloshFromPip();
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
            syncGuideSurfaces(fromPip);
            return;
        }

        if (current == SamsungGuideStep.WIRELESS_DEBUGGING) {
            if (RemotePairingService.getSessionState() == SessionState.IDLE) {
                if (coordinator.step() == OnboardingState.Step.REQUESTING_SUPPORT) {
                    guideNotification.showWaiting(
                            current,
                            "Esperá un momento: estamos preparando la sesión segura con soporte.");
                    if (!fromPip) {
                        lastRenderKey = null;
                        render();
                    }
                    return;
                }
                if (!startSupportSession()) {
                    guideNotification.showWaiting(
                            current,
                            "Todavía estamos preparando soporte. Probá nuevamente en unos segundos.");
                    return;
                }
            }
            guideStore.setStep(SamsungGuideStep.PAIR_DEVICE);
            syncGuideSurfaces(fromPip);
            return;
        }

        if (current == SamsungGuideStep.PAIR_DEVICE) {
            guideStore.setStep(SamsungGuideStep.ENTER_CODE);
            guideNotification.clear();
            syncGuideSurfaces(fromPip);
            return;
        }

        guideStore.setStep(current.next());
        syncGuideSurfaces(fromPip);
    }

    private void guideBack(boolean fromPip) {
        SamsungGuideStep current = guideStore.step();
        if (!current.canGoBack()) {
            if (fromPip) {
                openGloshFromPip();
            } else {
                cancelConnection();
            }
            return;
        }
        guideStore.setStep(current.previous());
        syncGuideSurfaces(fromPip);
    }

    private void syncGuideSurfaces(boolean fromPip) {
        SamsungGuideStep step = guideStore.step();
        if (RemotePairingService.getSessionState() == SessionState.IDLE) {
            guideNotification.showStep(step);
        }
        if (isInPictureInPictureMode() || fromPip) {
            pipView.setStep(step);
            updatePipParams(step, false);
        }
        lastRenderKey = null;
        render();
    }

    private void openSettingsForGuide() {
        SamsungGuideStep step = guideStore.step();
        guideNotification.showStep(step);
        updatePipParams(step, true);
        launchingSettings = true;
        settingsNavigator.openForStep(this, step);
        handler.postDelayed(() -> launchingSettings = false, 1_500L);
    }

    private void openWirelessSettings() {
        guideNotification.clear();
        updatePipParams(guideStore.step(), true);
        launchingSettings = true;
        settingsNavigator.openWirelessDebugging(this);
        handler.postDelayed(() -> launchingSettings = false, 1_500L);
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
        if (!guideStore.active() || session != SessionState.PREPARING) {
            return;
        }
        if ((pairing == PairingUiState.WAITING_FOR_CODE
                || pairing == PairingUiState.CODE_FAILED
                || pairing == PairingUiState.CONNECTING)
                && guideStore.step().ordinal() < SamsungGuideStep.ENTER_CODE.ordinal()) {
            guideStore.setStep(SamsungGuideStep.ENTER_CODE);
            guideNotification.clear();
            if (isInPictureInPictureMode()) {
                pipView.setStep(SamsungGuideStep.ENTER_CODE);
                updatePipParams(SamsungGuideStep.ENTER_CODE, false);
            }
            lastRenderKey = null;
        }
    }

    private void cancelConnection() {
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

    private void registerPipActions() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PIP_BACK);
        filter.addAction(ACTION_PIP_NEXT);
        filter.addAction(ACTION_PIP_OPEN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipActions, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(pipActions, filter);
        }
        pipReceiverRegistered = true;
    }

    private void openGloshFromPip() {
        Intent intent = new Intent(this, MainActivity.class)
                .setAction(ACTION_GUIDE_OPEN)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }

    private void updatePipParams(SamsungGuideStep step, boolean autoEnter) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        try {
            setPictureInPictureParams(buildPipParams(step, autoEnter));
        } catch (Throwable ignored) {
            // Some devices or user settings may disable PiP. Notification remains available.
        }
    }

    private PictureInPictureParams buildPipParams(SamsungGuideStep step, boolean autoEnter) {
        PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(16, 9))
                .setActions(pipActionsFor(step));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter);
        }
        return builder.build();
    }

    private List<RemoteAction> pipActionsFor(SamsungGuideStep step) {
        List<RemoteAction> actions = new ArrayList<>();
        actions.add(remoteAction(
                android.R.drawable.ic_media_previous,
                "Atrás",
                ACTION_PIP_BACK,
                9201));
        if (step == SamsungGuideStep.ENTER_CODE) {
            actions.add(remoteAction(
                    android.R.drawable.ic_menu_edit,
                    "Abrir Glosh",
                    ACTION_PIP_OPEN,
                    9203));
        } else {
            actions.add(remoteAction(
                    android.R.drawable.ic_media_next,
                    pipNextLabel(step),
                    ACTION_PIP_NEXT,
                    9202));
        }
        return actions;
    }

    private RemoteAction remoteAction(
            int iconResource,
            String label,
            String action,
            int requestCode) {
        Intent intent = new Intent(action).setPackage(getPackageName());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pending = PendingIntent.getBroadcast(this, requestCode, intent, flags);
        return new RemoteAction(
                Icon.createWithResource(this, iconResource),
                label,
                label,
                pending);
    }

    private static String pipNextLabel(SamsungGuideStep step) {
        return switch (step) {
            case ABOUT_PHONE, SOFTWARE_INFO -> "Ya lo abrí";
            case BUILD_NUMBER -> "Ya está activo";
            case DEVELOPER_OPTIONS -> "Ya estoy ahí";
            case WIRELESS_DEBUGGING -> "Ya la activé";
            case PAIR_DEVICE -> "Ya veo el código";
            case ENTER_CODE -> "Abrir Glosh";
        };
    }

    private static String openSettingsLabel(SamsungGuideStep step) {
        return switch (step) {
            case ABOUT_PHONE -> "ABRIR ACERCA DEL TELÉFONO";
            case SOFTWARE_INFO, BUILD_NUMBER -> "VOLVER A ACERCA DEL TELÉFONO";
            case DEVELOPER_OPTIONS -> "ABRIR OPCIONES DE DESARROLLADOR";
            case WIRELESS_DEBUGGING -> "ABRIR DEPURACIÓN INALÁMBRICA";
            case PAIR_DEVICE -> "VOLVER A DEPURACIÓN INALÁMBRICA";
            case ENTER_CODE -> "ABRIR GLOSH";
        };
    }
}
