package com.glosh.remote.spike.guide.accessibility;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ApplicationInfo;
import android.provider.Settings;

import com.glosh.remote.spike.wizard.SettingsRoute;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SettingsPackageResolver {
    private static final List<String> SETTINGS_ACTIONS = List.of(
            Settings.ACTION_SETTINGS,
            Settings.ACTION_ACCESSIBILITY_SETTINGS,
            Settings.ACTION_DEVICE_INFO_SETTINGS,
            Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
            SettingsRoute.WIRELESS_DEBUGGING);

    public Set<String> resolve(Context context) {
        PackageManager manager = context.getPackageManager();
        Set<String> packages = new LinkedHashSet<>();
        for (String action : SETTINGS_ACTIONS) {
            ResolveInfo resolved = manager.resolveActivity(new Intent(action), PackageManager.MATCH_DEFAULT_ONLY);
            if (resolved != null && resolved.activityInfo != null) {
                String packageName = resolved.activityInfo.packageName;
                if (packageName != null
                        && !packageName.equals(context.getPackageName())
                        && isTrustedSystemPackage(manager, packageName)) {
                    packages.add(packageName);
                }
            }
        }
        return Set.copyOf(packages);
    }

    public static boolean isAllowed(String packageName, Set<String> allowed) {
        return packageName != null && allowed != null && allowed.contains(packageName);
    }

    private boolean isTrustedSystemPackage(PackageManager manager, String packageName) {
        try {
            ApplicationInfo info = manager.getApplicationInfo(packageName, 0);
            int trustedFlags = ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
            return (info.flags & trustedFlags) != 0;
        } catch (PackageManager.NameNotFoundException error) {
            return false;
        }
    }
}
