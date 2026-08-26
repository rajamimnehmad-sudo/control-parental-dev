package com.glosh.remote.spike.session;

import android.content.Context;
import android.os.PowerManager;

import java.util.concurrent.TimeUnit;

/** Process-owned screen lease; Android releases it automatically if the process dies. */
public final class ScreenAwakeLease {
    private static final String TAG = "GloshRemote:ActiveSupport";
    private static final int MAX_SESSION_MINUTES = 120;

    private final PowerManager.WakeLock wakeLock;

    @SuppressWarnings("deprecation")
    public ScreenAwakeLease(Context context) {
        PowerManager power = context.getSystemService(PowerManager.class);
        if (power == null) {
            throw new IllegalStateException("PowerManager unavailable");
        }
        wakeLock = power.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, TAG);
        wakeLock.setReferenceCounted(false);
    }

    public void acquireForSessionMinutes(int sessionMinutes) {
        wakeLock.acquire(timeoutForSessionMinutes(sessionMinutes));
    }

    public void release() {
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    static long timeoutForSessionMinutes(int sessionMinutes) {
        if (sessionMinutes < 1 || sessionMinutes > MAX_SESSION_MINUTES) {
            throw new IllegalArgumentException("sessionMinutes out of range");
        }
        return TimeUnit.MINUTES.toMillis(sessionMinutes + 1L);
    }
}
