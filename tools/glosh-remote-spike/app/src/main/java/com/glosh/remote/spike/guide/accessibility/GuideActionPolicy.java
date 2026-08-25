package com.glosh.remote.spike.guide.accessibility;

import java.util.Set;

public final class GuideActionPolicy {
    public enum Operation {
        CLICK,
        SHOW_ON_SCREEN,
        SCROLL_DOWN,
        SCROLL_FORWARD
    }

    private static final Set<Operation> ALLOWED = Set.of(Operation.values());

    private GuideActionPolicy() {
    }

    public static boolean isAllowed(Operation operation) {
        return ALLOWED.contains(operation);
    }

    public static boolean allowsFrameworkActionName(String name) {
        try {
            return ALLOWED.contains(Operation.valueOf(name));
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    public static Set<Operation> allowedActions() {
        return ALLOWED;
    }
}
