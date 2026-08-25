package com.glosh.remote.spike.guide.accessibility;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Set;

public class SettingsScopeAndSafetyTest {
    @Test
    public void onlyDynamicallyResolvedSettingsPackageIsAllowed() {
        Set<String> allowed = Set.of("com.android.settings");
        assertTrue(SettingsPackageResolver.isAllowed("com.android.settings", allowed));
        assertFalse(SettingsPackageResolver.isAllowed("com.android.chrome", allowed));
        assertFalse(SettingsPackageResolver.isAllowed("com.whatsapp", allowed));
        assertFalse(SettingsPackageResolver.isAllowed(null, allowed));
    }

    @Test
    public void actionPolicyAllowsOnlyTransactionalClickShowAndScroll() {
        assertTrue(GuideActionPolicy.isAllowed(GuideActionPolicy.Operation.CLICK));
        assertTrue(GuideActionPolicy.isAllowed(GuideActionPolicy.Operation.SHOW_ON_SCREEN));
        assertTrue(GuideActionPolicy.isAllowed(GuideActionPolicy.Operation.SCROLL_DOWN));
        assertTrue(GuideActionPolicy.isAllowed(GuideActionPolicy.Operation.SCROLL_FORWARD));
        assertFalse(GuideActionPolicy.allowsFrameworkActionName("ACTION_SET_TEXT"));
        assertFalse(GuideActionPolicy.allowsFrameworkActionName("dispatchGesture"));
        assertFalse(GuideActionPolicy.allowsFrameworkActionName("GLOBAL_ACTION_BACK"));
        assertFalse(GuideActionPolicy.allowsFrameworkActionName("GLOBAL_ACTION_HOME"));
    }
}
