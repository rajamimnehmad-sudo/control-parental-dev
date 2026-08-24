package com.glosh.remote.spike.guide.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.glosh.remote.spike.RemotePairingService;
import com.glosh.remote.spike.broker.SupportSessionCoordinator;
import com.glosh.remote.spike.guide.overlay.GuideBubbleController;
import com.glosh.remote.spike.guide.overlay.HighlightOverlayController;
import com.glosh.remote.spike.guide.pairing.PairingCodeDetector;
import com.glosh.remote.spike.guide.scroll.AutoScrollController;
import com.glosh.remote.spike.guide.state.GuideStage;
import com.glosh.remote.spike.guide.state.LiveGuideRuntime;
import com.glosh.remote.spike.wizard.OemDetector;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class LiveGuideAccessibilityService extends AccessibilityService
        implements LiveGuideRuntime.Listener {
    private static final long RESCAN_DEBOUNCE_MS = 220;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SettingsTreeScanner scanner = new SettingsTreeScanner();
    private final TargetMatcher matcher = new TargetMatcher();
    private final PairingCodeDetector codeDetector = new PairingCodeDetector();
    private final AutoScrollController scroll = new AutoScrollController();
    private final Runnable rescan = this::scanCurrentWindow;

    private HighlightOverlayController highlight;
    private GuideBubbleController bubble;
    private String currentPackage;
    private String currentScreen;
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
        highlight = new HighlightOverlayController(this);
        bubble = new GuideBubbleController(
                this,
                this::rescue,
                this::openCorrectSettings,
                this::closeGuide);
        LiveGuideRuntime.register(this);
        applyPackageScope();
        scheduleScan();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || !LiveGuideRuntime.isActive()) {
            clearVisuals();
            return;
        }
        GuideStage stage = LiveGuideRuntime.stage();
        String packageName = event.getPackageName() == null ? "" : event.getPackageName().toString();
        Set<String> allowed = LiveGuideRuntime.settingsPackages();
        if (!stage.observesSettings() || !SettingsPackageResolver.isAllowed(packageName, allowed)) {
            clearVisuals();
            return;
        }
        currentPackage = packageName;
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            trackExpectedClick(event);
        } else if (event.getEventType() == AccessibilityEvent.TYPE_ANNOUNCEMENT
                || event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            detectDeveloperConfirmation(event);
        }
        scheduleScan();
    }

    @Override
    public void onInterrupt() {
        clearVisuals();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(rescan);
        LiveGuideRuntime.unregister(this);
        clearVisuals();
        super.onDestroy();
    }

    @Override
    public void onGuideStateChanged(GuideStage stage, boolean active) {
        handler.post(() -> {
            applyPackageScope();
            if (!active || stage == GuideStage.CONNECTED || stage == GuideStage.OFF) {
                clearVisuals();
                LiveGuideRuntime.unregister(this);
                disableSelf();
                return;
            }
            scroll.reset();
            scheduleScan();
        });
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
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);
    }

    private void scheduleScan() {
        handler.removeCallbacks(rescan);
        handler.postDelayed(rescan, RESCAN_DEBOUNCE_MS);
    }

    private void scanCurrentWindow() {
        if (!LiveGuideRuntime.isActive() || !LiveGuideRuntime.stage().observesSettings()) {
            clearVisuals();
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            showRecovery();
            return;
        }
        String packageName = root.getPackageName() == null ? "" : root.getPackageName().toString();
        if (!SettingsPackageResolver.isAllowed(packageName, LiveGuideRuntime.settingsPackages())) {
            clearVisuals();
            return;
        }
        SettingsTreeScanner.ScanResult result = scanner.scan(root);
        boolean screenChanged = currentScreen != null && !currentScreen.equals(result.screenTitle());
        currentScreen = result.screenTitle();

        if (LiveGuideRuntime.stage() == GuideStage.PAIR_CODE_TARGET) {
            String code = codeDetector.detect(result.visibleText(), true);
            if (code != null) {
                submitDetectedCode(code);
                return;
            }
        }

        LocatedTarget located = locate(result);
        if (located == null) {
            clearVisuals();
            attemptScroll(root, null, result, screenChanged);
            return;
        }
        if (located.stage != LiveGuideRuntime.stage()) {
            LiveGuideRuntime.setStage(located.stage);
            scroll.reset();
        }
        android.view.WindowManager windowManager = getSystemService(android.view.WindowManager.class);
        android.view.WindowMetrics metrics = windowManager.getCurrentWindowMetrics();
        Rect display = metrics.getBounds();
        android.graphics.Insets insets = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                android.view.WindowInsets.Type.systemBars()
                        | android.view.WindowInsets.Type.displayCutout());
        Rect bounds = com.glosh.remote.spike.guide.overlay.OverlayGeometry.clampHighlight(
                located.record.candidate().bounds(), display, insets);
        boolean visible = Rect.intersects(display, bounds) && !bounds.isEmpty();
        if (visible) {
            scroll.reset();
            highlight.show(bounds);
            bubble.show(instruction(located.stage), bounds);
        } else {
            clearVisuals();
            attemptScroll(root, located.record.node(), result, screenChanged);
        }
    }

    private LocatedTarget locate(SettingsTreeScanner.ScanResult result) {
        List<GuideStage> stages = new ArrayList<>();
        stages.add(LiveGuideRuntime.stage());
        for (GuideStage candidate : new GuideStage[] {
                GuideStage.DEV_ABOUT_PHONE,
                GuideStage.DEV_SOFTWARE_INFO,
                GuideStage.DEV_BUILD_NUMBER,
                GuideStage.WIRELESS_DEBUGGING,
                GuideStage.PAIR_CODE_TARGET}) {
            if (!stages.contains(candidate)) {
                stages.add(candidate);
            }
        }
        List<TargetCandidate> candidates = result.nodes().stream()
                .map(SettingsTreeScanner.NodeRecord::candidate)
                .toList();
        for (GuideStage stage : stages) {
            TargetSpec spec = GuideTargetCatalog.forStage(LiveGuideRuntime.family(), stage);
            if (spec == null) {
                continue;
            }
            TargetMatcher.Match match = matcher.best(spec, candidates);
            if (!match.actionable()) {
                continue;
            }
            for (SettingsTreeScanner.NodeRecord record : result.nodes()) {
                if (record.candidate() == match.candidate()) {
                    return new LocatedTarget(stage, record);
                }
            }
        }
        return null;
    }

    private void attemptScroll(
            AccessibilityNodeInfo root,
            AccessibilityNodeInfo target,
            SettingsTreeScanner.ScanResult result,
            boolean screenChanged) {
        AccessibilityNodeInfo scrollable = target == null
                ? findScrollableDescendant(root)
                : findScrollable(target);
        int showAction = AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId();
        int downAction = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.getId();
        int forwardAction = AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;
        boolean show = target != null && supports(target, showAction);
        boolean down = scrollable != null && supports(scrollable, downAction);
        boolean forward = scrollable != null && supports(scrollable, forwardAction);
        AutoScrollController.Action action = scroll.next(
                target != null,
                false,
                show,
                down,
                forward,
                fingerprint(result),
                screenChanged,
                true);
        AccessibilityNodeInfo receiver = action == AutoScrollController.Action.SHOW_ON_SCREEN
                ? target
                : scrollable;
        if (receiver == null) {
            showRecovery();
            return;
        }
        GuideActionPolicy.Operation operation = switch (action) {
            case SHOW_ON_SCREEN -> GuideActionPolicy.Operation.SHOW_ON_SCREEN;
            case SCROLL_DOWN -> GuideActionPolicy.Operation.SCROLL_DOWN;
            case SCROLL_FORWARD -> GuideActionPolicy.Operation.SCROLL_FORWARD;
            case STOP -> null;
        };
        int nodeAction = switch (action) {
            case SHOW_ON_SCREEN -> showAction;
            case SCROLL_DOWN -> downAction;
            case SCROLL_FORWARD -> forwardAction;
            case STOP -> 0;
        };
        if (nodeAction == 0 || !GuideActionPolicy.isAllowed(operation)
                || !receiver.performAction(nodeAction)) {
            showRecovery();
        }
    }

    private AccessibilityNodeInfo findScrollable(AccessibilityNodeInfo start) {
        AccessibilityNodeInfo current = start;
        while (current != null) {
            if (current.isScrollable()) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private AccessibilityNodeInfo findScrollableDescendant(AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }
        if (node.isScrollable()) {
            return node;
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo found = findScrollableDescendant(node.getChild(index));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private boolean supports(AccessibilityNodeInfo node, int action) {
        for (AccessibilityNodeInfo.AccessibilityAction supported : node.getActionList()) {
            if (supported.getId() == action) {
                return true;
            }
        }
        return false;
    }

    private void trackExpectedClick(AccessibilityEvent event) {
        AccessibilityNodeInfo source = event.getSource();
        if (source == null) {
            return;
        }
        GuideStage stage = LiveGuideRuntime.stage();
        TargetSpec spec = GuideTargetCatalog.forStage(LiveGuideRuntime.family(), stage);
        if (spec == null) {
            return;
        }
        Rect bounds = new Rect();
        source.getBoundsInScreen(bounds);
        TargetCandidate candidate = new TargetCandidate(
                string(source.getText()),
                string(source.getContentDescription()),
                source.getViewIdResourceName(),
                currentScreen,
                source.getParent() == null ? "" : string(source.getParent().getText()),
                "",
                string(source.getClassName()),
                source.isClickable(),
                bounds);
        if (matcher.score(spec, candidate) != TargetMatcher.Confidence.HIGH) {
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
        String joined = event.getText() == null ? "" : event.getText().toString();
        String normalized = TargetMatcher.normalize(joined);
        if (normalized.contains("ya sos desarrollador")
                || normalized.contains("you are now a developer")
                || normalized.contains("modo desarrollador activado")) {
            LiveGuideRuntime.setStage(GuideStage.SUPPORT_PREPARING);
            clearVisuals();
            SupportSessionCoordinator.get(this).confirmDeveloperOptions();
        }
    }

    private void submitDetectedCode(String detected) {
        clearVisuals();
        LiveGuideRuntime.setStage(GuideStage.PAIRING);
        Intent intent = new Intent(this, RemotePairingService.class)
                .setAction(RemotePairingService.ACTION_SUBMIT_CODE)
                .putExtra(RemotePairingService.EXTRA_PAIRING_CODE, detected);
        startService(intent);
    }

    private void rescue() {
        scroll.reset();
        scheduleScan();
    }

    private void showRecovery() {
        if (highlight != null) {
            highlight.clear();
        }
        if (bubble != null) {
            Rect display = getSystemService(android.view.WindowManager.class)
                    .getCurrentWindowMetrics().getBounds();
            Rect anchor = new Rect(display.centerX() - 1, display.centerY() - 1,
                    display.centerX() + 1, display.centerY() + 1);
            bubble.showRecovery("Volvamos al punto correcto", anchor);
        }
    }

    private void openCorrectSettings() {
        GuideStage stage = LiveGuideRuntime.stage();
        String action = stage == GuideStage.WIRELESS_DEBUGGING || stage == GuideStage.PAIR_CODE_TARGET
                ? Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
                : Settings.ACTION_DEVICE_INFO_SETTINGS;
        Intent intent = new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.resolveActivity(getPackageManager()) == null) {
            intent = new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        clearVisuals();
        startActivity(intent);
    }

    private void closeGuide() {
        clearVisuals();
        LiveGuideRuntime.reset();
    }

    private void clearVisuals() {
        if (highlight != null) {
            highlight.clear();
        }
        if (bubble != null) {
            bubble.clear();
        }
    }

    private String instruction(GuideStage stage) {
        if (stage == GuideStage.DEV_BUILD_NUMBER && buildTapCount > 0) {
            return "Seguí tocando Número de compilación · " + buildTapCount + " de 7";
        }
        return GuideTargetCatalog.instruction(stage);
    }

    private String fingerprint(SettingsTreeScanner.ScanResult result) {
        String first = result.nodes().isEmpty() ? "" : result.nodes().get(0).candidate().text();
        String last = result.nodes().isEmpty()
                ? ""
                : result.nodes().get(result.nodes().size() - 1).candidate().text();
        return TargetMatcher.normalize(result.screenTitle() + "|" + first + "|" + last);
    }

    private String string(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    private record LocatedTarget(GuideStage stage, SettingsTreeScanner.NodeRecord record) {
    }
}
