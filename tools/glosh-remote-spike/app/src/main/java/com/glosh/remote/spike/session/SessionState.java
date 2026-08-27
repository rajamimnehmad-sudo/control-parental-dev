package com.glosh.remote.spike.session;

public enum SessionState {
    IDLE,
    PREPARING,
    ADB_READY,
    CONNECTED,
    RECONNECTING;

    public static boolean shouldIgnoreStart(String action, String startAction, SessionState state) {
        return startAction.equals(action) && state != IDLE;
    }
}
