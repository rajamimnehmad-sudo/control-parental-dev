package com.glosh.remote.spike.wizard;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

/**
 * Compatibility controller for the floating Samsung instructor.
 *
 * The previous implementation owned a third-party application overlay window, but Samsung
 * Settings physically hides those windows. The controller now delegates the same lifecycle
 * contract to a system-managed notification Bubble, whose expanded content is GuideBubbleActivity.
 */
public final class GuideOverlayController {
    public static final String ACTION_BUBBLE_BACK =
            "com.glosh.remote.spike.BUBBLE_GUIDE_BACK";
    public static final String ACTION_BUBBLE_NEXT =
            "com.glosh.remote.spike.BUBBLE_GUIDE_NEXT";

    public interface Listener {
        void onBack();
        void onNext();
    }

    private final Activity activity;
    private final Listener listener;
    private final GuideNotification notification;
    private boolean visible;
    private boolean receiverRegistered;

    private final BroadcastReceiver actions = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) {
                return;
            }
            if (ACTION_BUBBLE_BACK.equals(intent.getAction())) {
                listener.onBack();
            } else if (ACTION_BUBBLE_NEXT.equals(intent.getAction())) {
                listener.onNext();
            }
        }
    };

    public GuideOverlayController(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        this.notification = new GuideNotification(activity);
    }

    public boolean canShow() {
        return notification.canBubble();
    }

    public boolean show(SamsungGuideStep step) {
        return show(step, null);
    }

    public boolean show(SamsungGuideStep step, String overrideInstruction) {
        if (!canShow()) {
            return false;
        }
        ensureReceiver();
        // Product decision: keep the system Bubble collapsed over Settings. One UI may show its
        // compact conversation preview, and the user expands it only when controls/code are needed.
        notification.showBubbleStep(step, overrideInstruction, false);
        visible = true;
        return true;
    }

    public void updateStep(SamsungGuideStep step) {
        if (visible) {
            notification.showBubbleStep(step, false);
        }
    }

    public void showWaiting(SamsungGuideStep step, String message) {
        if (visible) {
            notification.showBubbleStep(step, message, false);
        }
    }

    public boolean isVisible() {
        return visible;
    }

    public void hide() {
        if (visible) {
            notification.clear();
        }
        visible = false;
        unregisterReceiver();
    }

    private void ensureReceiver() {
        if (receiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_BUBBLE_BACK);
        filter.addAction(ACTION_BUBBLE_NEXT);
        try {
            activity.registerReceiver(actions, filter, Context.RECEIVER_NOT_EXPORTED);
            receiverRegistered = true;
        } catch (Throwable ignored) {
            receiverRegistered = false;
        }
    }

    private void unregisterReceiver() {
        if (!receiverRegistered) {
            return;
        }
        try {
            activity.unregisterReceiver(actions);
        } catch (Throwable ignored) {
            // Activity teardown may race with the Bubble being dismissed.
        }
        receiverRegistered = false;
    }
}
