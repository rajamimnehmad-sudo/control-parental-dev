package com.glosh.remote.spike.guide.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.glosh.remote.spike.RemotePairingService;
import com.glosh.remote.spike.BuildConfig;
import com.glosh.remote.spike.broker.SupportSessionCoordinator;
import com.glosh.remote.spike.guide.autopilot.AdaptiveInstallCoordinator;
import com.glosh.remote.spike.guide.autopilot.AutopilotActionGate;
import com.glosh.remote.spike.guide.autopilot.FreshNodeClickExecutor;
import com.glosh.remote.spike.guide.autopilot.FreshSettingsScrollExecutor;
import com.glosh.remote.spike.guide.autopilot.SamsungSettingsClassifier;
import com.glosh.remote.spike.guide.overlay.CoachBarController;
import com.glosh.remote.spike.guide.overlay.HighlightController;
import com.glosh.remote.spike.guide.overlay.OverlayGeometry;
import com.glosh.remote.spike.guide.pairing.PairingCodeDetector;
import com.glosh.remote.spike.guide.scroll.RevealActionExecutor;
import com.glosh.remote.spike.guide.scroll.RevealScrollController;
import com.glosh.remote.spike.guide.state.GuideStage;
import com.glosh.remote.spike.guide.state.LiveGuideRuntime;
import com.glosh.remote.spike.wizard.OemDetector;
import com.glosh.remote.spike.session.SessionState;

import java.util.Set;

public final class LiveGuideAccessibilityService extends AccessibilityService
        implements LiveGuideRuntime.Listener,
        GuideEventActor.Listener,
        AdaptiveInstallCoordinator.Host {
    private final Handler actorHandler = new Handler(Looper.getMainLooper());
    private final TargetMatcher matcher = new TargetMatcher();
    private final GuideDebugSummary debugSummary = new GuideDebugSummary(matcher);
    private final AccessibilityEventTargetInspector eventTargetInspector =
            new AccessibilityEventTargetInspector(matcher);
    private final GuideTargetLocator locator = new GuideTargetLocator(matcher);
    private final PairingCodeDetector codeDetector = new PairingCodeDetector();
    private final SamsungSettingsClassifier samsungClassifier = new SamsungSettingsClassifier();
    private final ScanGenerationGuard generationGuard = new ScanGenerationGuard();
    private final RevealScrollController reveal = new RevealScrollController();

    private GuideEventActor actor;
    private SettingsWindowAuthority windowAuthority;
    private RevealActionExecutor revealExecutor;
    private AdaptiveInstallCoordinator autopilot;
    private HighlightController highlight;
    private CoachBarController coach;
    private ScanGenerationGuard.Token currentToken;
    private SettingsSnapshot currentSnapshot;
    private boolean rescueRequested;
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
        revealExecutor = new RevealActionExecutor(windowAuthority, reveal, generationGuard);
        FreshNodeClickExecutor clickExecutor = new FreshNodeClickExecutor(
                windowAuthority,
                generationGuard,
                samsungClassifier,
                new AutopilotActionGate());
        autopilot = new AdaptiveInstallCoordinator(
                this,
                actorHandler,
                generationGuard,
                clickExecutor,
                new FreshSettingsScrollExecutor(
                        windowAuthority, generationGuard, samsungClassifier),
                this);
        highlight = new HighlightController(this, generationGuard);
        coach = new CoachBarController(
                this,
                this::requestReveal,
                this::requestRescue,
                this::closeGuide);
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
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            reveal.onScrolled(SystemClock.elapsedRealtime());
        } else if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            if (autopilot == null || !autopilot.handles(LiveGuideRuntime.stage())) {
                trackExpectedClick(event);
            }
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
                reveal.reset();
                LiveGuideRuntime.unregister(this);
                disableSelf();
                return;
            }
            reveal.cancel();
            rescueRequested = false;
            if (autopilot != null) {
                autopilot.onStageChanged(stage, active);
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
        if (!generationGuard.isCurrent(token, snapshot)
                || !LiveGuideRuntime.isActive()
                || !LiveGuideRuntime.stage().observesSettings()) {
            return;
        }
        currentToken = token;
        currentSnapshot = snapshot;

        if (autopilot != null && autopilot.handles(LiveGuideRuntime.stage())) {
            autopilot.onStableSnapshot(token, snapshot);
            return;
        }

        if (LiveGuideRuntime.stage() == GuideStage.PAIR_CODE_TARGET) {
            String code = codeDetector.detect(snapshot.visibleText(), true);
            if (code != null) {
                submitPairingCode(code);
                return;
            }
        }

        LocatedTarget located = locate(snapshot, rescueRequested);
        debug("stable stage=" + LiveGuideRuntime.stage()
                + " result=" + (located == null ? "none" : located.stage())
                + " expectedScreen=" + locator.isExpectedScreen(
                        LiveGuideRuntime.family(), LiveGuideRuntime.stage(), snapshot.screenTitle())
                + " nodes=" + snapshot.nodes().size()
                + " confidence=" + debugSummary.confidence(
                        snapshot,
                        GuideTargetCatalog.forStage(
                                LiveGuideRuntime.family(), LiveGuideRuntime.stage())));
        if (rescueRequested) {
            rescueRequested = false;
            if (located == null) {
                showRecovery("Volvamos al punto correcto");
                return;
            }
        }
        if (located == null) {
            showMissingTarget(snapshot);
            return;
        }
        if (located.stage() != LiveGuideRuntime.stage()) {
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
        if (rescueRequested) {
            rescueRequested = false;
            showRecovery("Abramos nuevamente los Ajustes correctos");
        }
        if (autopilot != null) {
            autopilot.onNoTrustedWindow(generation);
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
            showRecovery("No pude abrir los Ajustes correctos.");
        }
    }

    private LocatedTarget locate(SettingsSnapshot snapshot, boolean rescue) {
        GuideTargetLocator.LocatedTarget located = locator.locate(
                snapshot, LiveGuideRuntime.family(), LiveGuideRuntime.stage(), rescue);
        return located == null ? null : new LocatedTarget(located.stage(), located.node());
    }

    private void showLocatedTarget(
            ScanGenerationGuard.Token token,
            SettingsSnapshot snapshot,
            LocatedTarget located) {
        Rect target = located.node().candidate().bounds();
        Rect content = contentBounds();
        boolean visible = !target.isEmpty() && Rect.intersects(content, target);
        debug("target stage=" + located.stage() + " visible=" + visible);
        if (visible) {
            reveal.cancel();
            Rect clamped = OverlayGeometry.clampHighlight(
                    target,
                    getSystemService(WindowManager.class).getCurrentWindowMetrics().getBounds(),
                    systemInsets());
            if (highlight.show(token, snapshot, clamped)) {
                coach.show(instruction(located.stage()), false, clamped);
            }
            return;
        }
        highlight.clear();
        coach.show(instruction(located.stage()), true);
        if (reveal.movementAllowed(SystemClock.elapsedRealtime())) {
            performReveal(token, snapshot, located.node());
        }
    }

    private void showMissingTarget(SettingsSnapshot snapshot) {
        highlight.clear();
        boolean expectedScreen = locator.isExpectedScreen(
                LiveGuideRuntime.family(), LiveGuideRuntime.stage(), snapshot.screenTitle());
        if (expectedScreen) {
            coach.show(instruction(LiveGuideRuntime.stage()), hasScrollable(snapshot));
            if (reveal.movementAllowed(SystemClock.elapsedRealtime())) {
                performReveal(currentToken, snapshot, null);
            }
        } else {
            reveal.cancel();
            coach.showRecovery("No encuentro este paso en esta pantalla");
        }
    }

    private void requestReveal() {
        if (actor == null) {
            return;
        }
        actor.runSerialized(() -> {
            long now = SystemClock.elapsedRealtime();
            if (!reveal.arm(now)) {
                coach.showRecovery("Esperá un momento y volvé a tocar MOSTRARME");
                return;
            }
            ScanGenerationGuard.Token token = currentToken;
            SettingsSnapshot snapshot = currentSnapshot;
            if (token == null || snapshot == null || !generationGuard.isCurrent(token, snapshot)) {
                actor.relevantEvent();
                return;
            }
            LocatedTarget located = locate(snapshot, false);
            performReveal(token, snapshot, located == null ? null : located.node());
        });
    }

    private void performReveal(
            ScanGenerationGuard.Token token,
            SettingsSnapshot snapshot,
            NodeSnapshot targetSnapshot) {
        RevealActionExecutor.Result result = revealExecutor.perform(
                token,
                snapshot,
                targetSnapshot,
                contentBounds(),
                LiveGuideRuntime.settingsPackages());
        // PERFORMED waits for the next real Accessibility event before continuing.
        if (result == RevealActionExecutor.Result.UNSUPPORTED) {
            coach.showRecovery("Deslizá suavemente y te sigo guiando.");
        }
    }

    private void requestRescue() {
        if (actor == null) {
            return;
        }
        actor.runSerialized(() -> {
            reveal.cancel();
            rescueRequested = true;
            actor.relevantEvent();
        });
    }

    private void trackExpectedClick(AccessibilityEvent event) {
        AccessibilityNodeInfo source = event.getSource();
        SettingsSnapshot snapshot = currentSnapshot;
        if (source == null || snapshot == null) {
            return;
        }
        GuideStage stage = LiveGuideRuntime.stage();
        TargetSpec spec = GuideTargetCatalog.forStage(LiveGuideRuntime.family(), stage);
        if (spec == null) {
            return;
        }
        if (!eventTargetInspector.matches(source, spec, snapshot.screenTitle())) {
            return;
        }
        if (stage == GuideStage.DEV_ABOUT_PHONE) {
            LiveGuideRuntime.setStage(GuideStage.DEV_SOFTWARE_INFO);
        } else if (stage == GuideStage.DEV_SOFTWARE_INFO) {
            LiveGuideRuntime.setStage(GuideStage.DEV_BUILD_NUMBER);
        } else if (stage == GuideStage.DEV_BUILD_NUMBER) {
            buildTapCount = Math.min(7, buildTapCount + 1);
        } else if (stage == GuideStage.WIRELESS_DEBUGGING) {
            LiveGuideRuntime.setStage(GuideStage.PAIR_CODE_TARGET);
        }
    }

    private void detectDeveloperConfirmation(AccessibilityEvent event) {
        String normalized = TargetMatcher.normalize(
                event.getText() == null ? "" : event.getText().toString());
        if (normalized.contains("ya sos desarrollador")
                || normalized.contains("you are now a developer")
                || normalized.contains("modo desarrollador activado")) {
            LiveGuideRuntime.setStage(GuideStage.SUPPORT_PREPARING);
            clearVisuals();
            SupportSessionCoordinator.get(this).confirmDeveloperOptions();
        }
    }

    @Override
    public void submitPairingCode(String code) {
        if (LiveGuideRuntime.stage() != GuideStage.PAIR_CODE_TARGET) {
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
    }

    private void closeGuide() {
        clearVisuals();
        reveal.reset();
        LiveGuideRuntime.reset();
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

    private boolean hasScrollable(SettingsSnapshot snapshot) {
        return snapshot.nodes().stream().anyMatch(NodeSnapshot::scrollable);
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
            return "Seguí tocando Número de compilación · " + buildTapCount + " de 7";
        }
        return GuideTargetCatalog.instruction(stage);
    }

    private String string(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    private void debug(String message) {
        if (BuildConfig.DEBUG) {
            Log.d("GloshGuideV2", message);
        }
    }

    private record LocatedTarget(GuideStage stage, NodeSnapshot node) {
    }
}
