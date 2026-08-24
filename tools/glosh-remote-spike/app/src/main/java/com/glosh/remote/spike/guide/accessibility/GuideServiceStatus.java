package com.glosh.remote.spike.guide.accessibility;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.view.accessibility.AccessibilityManager;

import java.util.List;

public final class GuideServiceStatus {
    private GuideServiceStatus() {
    }

    public static boolean isEnabled(Context context) {
        AccessibilityManager manager = (AccessibilityManager)
                context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager == null || !manager.isEnabled()) {
            return false;
        }
        ComponentName expected = new ComponentName(context, LiveGuideAccessibilityService.class);
        List<AccessibilityServiceInfo> services = manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_GENERIC);
        for (AccessibilityServiceInfo service : services) {
            if (service.getResolveInfo() == null || service.getResolveInfo().serviceInfo == null) {
                continue;
            }
            ComponentName enabled = new ComponentName(
                    service.getResolveInfo().serviceInfo.packageName,
                    service.getResolveInfo().serviceInfo.name);
            if (expected.equals(enabled)) {
                return true;
            }
        }
        return false;
    }
}
