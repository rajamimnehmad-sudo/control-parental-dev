package com.glosh.remote.spike.guide.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.glosh.remote.spike.BuildConfig;
import com.glosh.remote.spike.RemotePairingService;
import com.glosh.remote.spike.broker.SupportSessionCoordinator;
import com.glosh.remote.spike.guide.autopilot.AdaptiveInstallCoordinator;
import com.glosh.remote.spike.guide.autopilot.AutopilotContract;
import com.glosh.remote.spike.guide.autopilot.SamsungSettingsClassifier;
import com.glosh.remote.spike.guide.overlay.CoachBarController;
import com.glosh.remote.spike.guide.overlay.HighlightController;
import com.glosh.remote.spike.guide.overlay.OverlayGeometry;
import com.glosh.remote.spike.guide.pairing.PairingCodeDetector;
import com.glosh.remote.spike.guide.state.GuideStage;
import com.glosh.remote.spike.guide.state.LiveGuideRuntime;
import com.glosh.remote.spike.session.SessionState;
import com.glosh.remote.spike.wizard.GuideNotification;
import com.glosh.remote.spike.wizard.GuidePresentation;
import com.glosh.remote.spike.wizard.OemDetector;

import java.util.Set;

/**
 * Temporary Settings observer for the professional guided installer.
 *
 * <p>The service opens exact destinations, observes state, highlights safe labels and reads the
 * contextual pairing code. It deliberately performs no Settings click and no programmatic scroll.</p>
 */
public final class LiveGuideAccessibilityService extends AccessibilityService
        implements LiveGuideRuntime.Listener,
        GuideEventActor.Listener,
        AdaptiveInstallCoordinator.Host {
    private static final long POST_BUILD_REPROBE_MS = 1_000L;
    private static final long WAIT_FOR_PAIRING_SERVICE_MS = 280L;

    private final Handler actorHandler = new Handler(Looper.getMainLooper());
    private final TargetMatcher matcher = new TargetMatcher();
    private final GuideDebugSummary debugSummary = new GuideDebugSummary(matcher);
    private final AccessibilityEventTargetInspector eventTargetInspector =
            new AccessibilityEventTargetInspector(matcher);
    private final GuideTargetLocator locator = new GuideTargetLocator(matcher);
    private final PairingCodeDetector codeDetector = new PairingCodeDetector();
    private final SamsungSettingsClassifier samsungClassifier = new SamsungSettingsClassifier();
    private final ScanGenerationGuard generationGuard = new ScanGenerationGuard();

    private GuideEventActor actor;
    private SettingsWindowAuthority windowAuthority;
    private AdaptiveInstallCoordinator guided;
    private HighlightController highlight;
    private CoachBarController coach;
    private GuideNotification guideNotification;
    private ScanGenerationGuard.Token currentToken;
    private SettingsSnapshot currentSnapshot;
    private int buildTapCount;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        LiveGuideRuntime.initialize(
                this,
                OemDetector.detect(
                        Build.MANUFACTURER,
                        Build.BRAND,
                        Build.MODEL,
                        Build.VERSION.RELEASE,
                        Build.VERSION.SDK_INT).family());
        LiveGuideRuntime.updateSettingsPackages(new SettingsPackageResolver().resolve(this));
        windowAuthority = new SettingsWindowAuthority(this);
        guided = new AdaptiveInstallCoordinator(this, actorHandler, generationGuard, this);
        highlight = new HighlightController(this, generationGuard);
        coach = new CoachBarController(this, () -> { }, () -> { }, this::clearVisuals);
        guideNotification = new GuideNotification(this);
        actor = new GuideEventActor(actorHandler, this::captureSnapshot, this, generationGuard);
        LiveGuideRuntime.register(this);
        applyPackageScope();
        actor.relevantEvent();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || actor == null) {
            return;
        }
        if (!LiveGuideRuntime.isActive() || !LiveGuideRuntime.stage().observesSettings()) {
            clearVisuals();
            return;
        }
        String packageName = string(event.getPackageName());
        if (getPackageName().equals(packageName) || isAccessibilityOverlay(event.getWindowId())) {
            return;
        }
        if (!SettingsPackageResolver.isAllowed(packageName, LiveGuideRuntime.settingsPackages())) {
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED
                    || event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                actor.relevantEvent();
            }
            return;
        }
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED
                && (guided == null || !guided.handles(LiveGuideRuntime.stage()))) {
            trackExpectedClick(event);
        } else if (event.getEventType() == AccessibilityEvent.TYPE_ANNOUNCEMENT
                || event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            detectDeveloperConfirmation(event);
        }
        actor.relevantEvent();
    }

    @Override
    public void onInterrupt() {
        invalidateVisualAuthority();
    }

    @Override
    public void onDestroy() {
        if (actor != null) {
            actor.close();
        }
        LiveGuideRuntime.unregister(this);
        clearVisuals();
        if (guideNotification != null) {
            guideNotification.clear();
        }
        super.onDestroy();
    }

    @Override
    public void onGuideStateChanged(GuideStage stage, boolean active) {
        if (actor == null) {
            return;
        }
        actor.runSerialized(() -> {
            applyPackageScope();
            if (!active || stage == GuideStage.CONNECTED || stage == GuideStage.OFF) {
                clearVisuals();
                if (guideNotification != null) {
                    guideNotification.clear();
                }
                LiveGuideRuntime.unregister(this);
                disableSelf();
                return;
            }
            updateGuideNotification(stage, defaultInstruction(stage));
            if (guided != null) {
                guided.onStageChanged(stage, active);
            }
            actor.relevantEvent();
        });
    }

    @Override
    public void onGenerationInvalidated(long generation) {
        currentToken = null;
        currentSnapshot = null;
        if (highlight != null) {
            highlight.clear();
        }
    }

    @Override
    public void onStableSnapshot(
            ScanGenerationGuard.Token token,
            SettingsSnapshot snapshot) {
        GuideStage stage = LiveGuideRuntime.stage();
        if (!generationGuard.isCurrent(token, snapshot)
                || !LiveGuideRuntime.isActive()
                || !stage.observesSettings()) {
            return;
        }
        currentToken = token;
        currentSnapshot = snapshot;

        if (stage == GuideStage.DEV_BUILD_NUMBER
                && samsungClassifier.classify(snapshot).screen()
                == AutopilotContract.Screen.CREDENTIAL_PROMPT) {
            LiveGuideRuntime.setStage(GuideStage.AUTOPILOT_CREDENTIAL);
            return;
        }

        if (guided != null && guided.handles(stage)) {
            guided.onStableSnapshot(token, snapshot);
            return;
        }

        if (stage == GuideStage.PAIR_CODE_TARGET) {
            String code = codeDetector.detect(snapshot.visibleText(), true);
            if (code != null) {
                if (RemotePairingService.getSessionState() == SessionState.PREPARING) {
                    showInstruction("Código detectado. Completando la conexión…", null);
                    submitPairingCode(code);
                } else {
                    showInstruction(
                            "Código detectado. Esperando la sesión segura de soporte…",
                            null);
                    actorHandler.postDelayed(actor::relevantEvent, WAIT_FOR_PAIRING_SERVICE_MS);
                }
                return;
            }
        }

        LocatedTarget located = locate(snapshot);
        debug("stable stage=" + stage
                + " result=" + (located == null ? "none" : located.stage())
                + " expectedScreen=" + locator.isExpectedScreen(
                        LiveGuideRuntime.family(), stage, snapshot.screenTitle())
                + " nodes=" + snapshot.nodes().size()
                + " confidence=" + debugSummary.confidence(
                        snapshot,
                        GuideTargetCatalog.forStage(LiveGuideRuntime.family(), stage)));
        if (located == null) {
            showMissingTarget(snapshot);
            return;
        }
        if (located.stage() != stage) {
            LiveGuideRuntime.setStage(located.stage());
            return;
        }
        showLocatedTarget(token, snapshot, located);
    }

    @Override
    public void onNoTrustedWindow(long generation) {
        currentToken = null;
        currentSnapshot = null;
        if (highlight != null) {
            highlight.clear();
        }
        if (coach != null) {
            coach.clear();
        }
        if (guided != null && guided.handles(LiveGuideRuntime.stage())) {
            guided.onNoTrustedWindow(generation);
        }
    }

    private void applyPackageScope() {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) {
            return;
        }
        Set<String> packages = LiveGuideRuntime.isActive()
                ? LiveGuideRuntime.settingsPackages()
                : Set.of();
        info.packageNames = packages.toArray(new String[0]);
        info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        setServiceInfo(info);
    }

    private boolean isAccessibilityOverlay(int windowId) {
        for (android.view.accessibility.AccessibilityWindowInfo window : getWindows()) {
            if (window.getId() == windowId
                    && window.getType()
                    == android.view.accessibility.AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) {
                return true;
            }
        }
        return false;
    }

    private SettingsSnapshot captureSnapshot() {
        return windowAuthority.capture(LiveGuideRuntime.settingsPackages());
    }

    @Override
    public void startSupportStackIfReady() {
        if (RemotePairingService.getSessionState() != SessionState.IDLE) {
            return;
        }
        String descriptor = SupportSessionCoordinator.get(this).markSessionStarted();
        if (descriptor == null) {
            return;
        }
        if (guideNotification != null) {
            guideNotification.clear();
        }
        startForegroundService(new Intent(this, RemotePairingService.class)
                .setAction(RemotePairingService.ACTION_START)
                .putExtra(RemotePairingService.EXTRA_JOIN_URI, descriptor));
    }

    @Override
    public void showManualPairingFallback() {
        LiveGuideRuntime.setStage(GuideStage.PAIR_CODE_TARGET);
        Intent intent = new Intent(this, com.glosh.remote.spike.MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }

    @Override
    public void openSettings(String action) {
        Intent intent = new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.resolveActivity(getPackageManager()) == null) {
            intent = new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        try {
            startActivity(intent);
        } catch (Throwable error) {
            LiveGuideRuntime.setStage(GuideStage.AUTOPILOT_FALLBACK);
            showRecovery("No pude abrir esa pantalla. Volvé a Glosh para reintentar.");
        }
    }

    private LocatedTarget locate(SettingsSnapshot snapshot) {
        GuideTargetLocator.LocatedTarget located = locator.locate(
                snapshot,
                LiveGuideRuntime.family(),
                LiveGuideRuntime.stage(),
                false);
        return located == null ? null : new LocatedTarget(located.stage(), located.node());
    }

    private void showLocatedTarget(
            ScanGenerationGuard.Token token,
            SettingsSnapshot snapshot,
            LocatedTarget located) {
        String instruction = instruction(located.stage());
        Rect target = located.node().candidate().bounds();
        Rect content = contentBounds();
        boolean visible = !target.isEmpty() && Rect.intersects(content, target);
        debug("target stage=" + located.stage() + " visible=" + visible);
        if (visible) {
            Rect clamped = OverlayGeometry.clampHighlight(
                    target,
                    getSystemService(WindowManager.class).getCurrentWindowMetrics().getBounds(),
                    systemInsets());
            if (highlight.show(token, snapshot, clamped)) {
                coach.show(instruction, false, clamped);
            }
        } else {
            highlight.clear();
            coach.show(instruction + " Deslizá hasta encontrarlo.", false);
        }
        updateGuideNotification(located.stage(), instruction);
    }

    private void showMissingTarget(SettingsSnapshot snapshot) {
        if (highlight != null) {
            highlight.clear();
        }
        GuideStage stage = LiveGuideRuntime.stage();
        boolean expectedScreen = locator.isExpectedScreen(
                LiveGuideRuntime.family(), stage, snapshot.screenTitle());
        if (expectedScreen) {
            showInstruction(
                    instruction(stage) + " Si no lo ves, deslizá suavemente.",
                    null);
        } else {
            showRecovery("Esta no es la pantalla esperada. Volvé a Glosh para abrirla nuevamente.");
        }
    }

    private void trackExpectedClick(AccessibilityEvent event) {
        AccessibilityNodeInfo source = event.getSource();
        SettingsSnapshot snapshot = currentSnapshot;
        if (source == null || snapshot == null) {
            if (source != null) {
                source.recycle();
            }
            return;
        }
        try {
            GuideStage stage = LiveGuideRuntime.stage();
            TargetSpec spec = GuideTargetCatalog.forStage(LiveGuideRuntime.family(), stage);
            if (spec == null
                    || !eventTargetInspector.matches(source, spec, snapshot.screenTitle())) {
                return;
            }
            if (stage == GuideStage.DEV_ABOUT_PHONE) {
                LiveGuideRuntime.setStage(GuideStage.DEV_SOFTWARE_INFO);
            } else if (stage == GuideStage.DEV_SOFTWARE_INFO) {
                LiveGuideRuntime.setStage(GuideStage.DEV_BUILD_NUMBER);
            } else if (stage == GuideStage.DEV_BUILD_NUMBER) {
                buildTapCount = Math.min(7, buildTapCount + 1);
                LiveGuideRuntime.setStage(GuideStage.DEV_BUILD_NUMBER);
                if (buildTapCount >= 7) {
                    actorHandler.postDelayed(() -> {
                        if (LiveGuideRuntime.stage() == GuideStage.DEV_BUILD_NUMBER) {
                            LiveGuideRuntime.setStage(GuideStage.AUTOPILOT_PROBE);
                        }
                    }, POST_BUILD_REPROBE_MS);
                }
            }
        } finally {
            source.recycle();
        }
    }

    private void detectDeveloperConfirmation(AccessibilityEvent event) {
        String normalized = TargetMatcher.normalize(
                event.getText() == null ? "" : event.getText().toString());
        if (normalized.contains("ya sos desarrollador")
                || normalized.contains("you are now a developer")
                || normalized.contains("modo desarrollador activado")) {
            clearVisuals();
            SupportSessionCoordinator.get(this).confirmDeveloperOptions();
        }
    }

    @Override
    public void submitPairingCode(String code) {
        if (LiveGuideRuntime.stage() != GuideStage.PAIR_CODE_TARGET) {
            return;
        }
        if (RemotePairingService.getSessionState() != SessionState.PREPARING) {
            showInstruction("Código detectado. Esperando a soporte…", null);
            if (actor != null) {
                actorHandler.postDelayed(actor::relevantEvent, WAIT_FOR_PAIRING_SERVICE_MS);
            }
            return;
        }
        clearVisuals();
        Intent intent = new Intent(this, RemotePairingService.class)
                .setAction(RemotePairingService.ACTION_SUBMIT_CODE)
                .putExtra(RemotePairingService.EXTRA_PAIRING_CODE, code);
        startService(intent);
    }

    @Override
    public void showRecovery(String message) {
        if (highlight != null) {
            highlight.clear();
        }
        if (coach != null) {
            coach.showRecovery(message);
        }
        updateGuideNotification(
                LiveGuideRuntime.stage(),
                GuidePresentation.recovery(LiveGuideRuntime.stage(), message));
    }

    private void showInstruction(String message, Rect target) {
        if (coach != null) {
            coach.show(message, false, target);
        }
        updateGuideNotification(LiveGuideRuntime.stage(), message);
    }

    @Override
    public void clearVisuals() {
        if (highlight != null) {
            highlight.clear();
        }
        if (coach != null) {
            coach.clear();
        }
    }

    private void invalidateVisualAuthority() {
        generationGuard.invalidate();
        currentToken = null;
        currentSnapshot = null;
        clearVisuals();
    }

    @Override
    public void invalidateAndRescan() {
        generationGuard.invalidate();
        if (actor != null) {
            actor.relevantEvent();
        }
    }

    @Override
    public void rescanAfter(long delayMs) {
        if (actor != null) {
            actorHandler.postDelayed(actor::relevantEvent, delayMs);
        }
    }

    private Rect contentBounds() {
        Rect display = getSystemService(WindowManager.class).getCurrentWindowMetrics().getBounds();
        Insets insets = systemInsets();
        return new Rect(
                display.left + insets.left,
                display.top + insets.top,
                display.right - insets.right,
                display.bottom - insets.bottom);
    }

    private Insets systemInsets() {
        return getSystemService(WindowManager.class)
                .getCurrentWindowMetrics()
                .getWindowInsets()
                .getInsetsIgnoringVisibility(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
    }

    private String instruction(GuideStage stage) {
        if (stage == GuideStage.DEV_BUILD_NUMBER && buildTapCount > 0) {
            return "Seguí tocando “Número de compilación” · " + buildTapCount + " de 7.";
        }
        return GuideTargetCatalog.instruction(stage);
    }

    private String defaultInstruction(GuideStage stage) {
        return switch (stage) {
            case AUTOPILOT_PROBE -> "Si están apagadas, activá las opciones de desarrollador.";
            case AUTOPILOT_CREDENTIAL ->
                    "Ingresá el PIN, patrón o contraseña que Android te pide.";
            case WIRELESS_DEBUGGING ->
                    "Activá Depuración inalámbrica. Glosh detectará el cambio.";
            case PAIR_CODE_TARGET ->
                    "Tocá “Vincular dispositivo con código”. Glosh leerá los seis dígitos.";
            default -> instruction(stage);
        };
    }

    private void updateGuideNotification(GuideStage stage, String instruction) {
        updateGuideNotification(stage, GuidePresentation.forStage(stage, instruction));
    }

    private void updateGuideNotification(GuideStage stage, GuidePresentation presentation) {
        if (guideNotification == null) {
            return;
        }
        SessionState session = RemotePairingService.getSessionState();
        if (session == SessionState.PREPARING || session == SessionState.CONNECTED) {
            guideNotification.clear();
        } else {
            guideNotification.show(presentation);
        }
    }

    private String string(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    private void debug(String message) {
        if (BuildConfig.DEBUG) {
            Log.d("GloshGuidedInstall", message);
        }
    }

    private record LocatedTarget(GuideStage stage, NodeSnapshot node) {
    }
}
