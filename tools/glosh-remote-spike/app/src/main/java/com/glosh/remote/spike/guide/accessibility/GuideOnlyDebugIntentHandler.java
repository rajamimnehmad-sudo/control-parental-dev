package com.glosh.remote.spike.guide.accessibility;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import com.glosh.remote.spike.BuildConfig;
import com.glosh.remote.spike.RemotePairingService;
import com.glosh.remote.spike.broker.SupportSessionCoordinator;
import com.glosh.remote.spike.guide.state.GuideStage;
import com.glosh.remote.spike.guide.state.LiveGuideRuntime;
import com.glosh.remote.spike.protocol.JoinDescriptor;
import com.glosh.remote.spike.session.SessionState;
import com.glosh.remote.spike.wizard.SettingsNavigator;

public final class GuideOnlyDebugIntentHandler {
    public static final String ACTION_GUIDE_ONLY = "com.glosh.remote.spike.action.GUIDE_ONLY";
    public static final String EXTRA_ROUTE = "guide_route";
    public static final String ROUTE_ABOUT = "about";
    public static final String ROUTE_WIRELESS = "wireless";

    private GuideOnlyDebugIntentHandler() {
    }

    public static void consume(
            Activity activity,
            Intent intent,
            SupportSessionCoordinator coordinator,
            SettingsNavigator navigator,
            SettingsPackageResolver packageResolver) {
        if (!BuildConfig.DEBUG || intent == null) {
            return;
        }
        if (ACTION_GUIDE_ONLY.equals(intent.getAction())) {
            launchGuideOnly(activity, intent, coordinator, navigator, packageResolver);
            intent.setAction(Intent.ACTION_MAIN);
            return;
        }
        consumeDescriptor(intent, coordinator);
    }

    private static void launchGuideOnly(
            Activity activity,
            Intent intent,
            SupportSessionCoordinator coordinator,
            SettingsNavigator navigator,
            SettingsPackageResolver packageResolver) {
        if (RemotePairingService.getSessionState() != SessionState.IDLE) {
            return;
        }
        LiveGuideRuntime.beginPermission(
                coordinator.profile().family(), packageResolver.resolve(activity));
        if (!GuideServiceStatus.isEnabled(activity)) {
            navigator.openAccessibility(activity);
            return;
        }
        LiveGuideRuntime.guideEnabled();
        String route = intent.getStringExtra(EXTRA_ROUTE);
        if (ROUTE_WIRELESS.equals(route)) {
            LiveGuideRuntime.setStage(GuideStage.WIRELESS_DEBUGGING);
            navigator.openDeveloperOptions(activity);
        } else {
            LiveGuideRuntime.setStage(GuideStage.DEV_SOFTWARE_INFO);
            navigator.openAboutPhone(activity);
        }
    }

    private static void consumeDescriptor(Intent intent, SupportSessionCoordinator coordinator) {
        Uri data = intent.getData();
        if (data == null || !"gloshremote".equalsIgnoreCase(data.getScheme())) {
            return;
        }
        try {
            String raw = data.toString();
            JoinDescriptor descriptor = JoinDescriptor.parse(raw);
            descriptor.destroy();
            coordinator.seedDebugDescriptor(raw);
        } catch (Throwable ignored) {
            // Hidden DEV fallback; malformed descriptors fail closed.
        } finally {
            intent.setData(null);
        }
    }
}
