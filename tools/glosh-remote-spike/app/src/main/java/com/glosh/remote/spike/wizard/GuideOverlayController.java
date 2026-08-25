package com.glosh.remote.spike.wizard;

import android.app.Activity;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;

/** Owns the TYPE_APPLICATION_OVERLAY window used as the Samsung setup instructor. */
public final class GuideOverlayController {
    public interface Listener {
        void onBack();
        void onNext();
    }

    private final Activity activity;
    private final WindowManager windowManager;
    private final Listener listener;
    private GuideOverlayView view;
    private WindowManager.LayoutParams params;
    private boolean visible;

    public GuideOverlayController(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        this.windowManager = activity.getSystemService(WindowManager.class);
    }

    public boolean canShow() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && Settings.canDrawOverlays(activity);
    }

    public boolean show(SamsungGuideStep step) {
        return show(step, null);
    }

    public boolean show(SamsungGuideStep step, String overrideInstruction) {
        if (!canShow()) {
            return false;
        }
        if (view == null) {
            view = new GuideOverlayView(activity, new GuideOverlayView.Listener() {
                @Override
                public void onBack() {
                    listener.onBack();
                }

                @Override
                public void onNext() {
                    listener.onNext();
                }

                @Override
                public void onDragBy(int deltaX, int deltaY) {
                    moveBy(deltaX, deltaY);
                }
            });
        }
        view.setStep(step, overrideInstruction);
        if (visible) {
            return true;
        }

        params = buildParams();
        try {
            windowManager.addView(view, params);
            visible = true;
            return true;
        } catch (Throwable ignored) {
            visible = false;
            params = null;
            return false;
        }
    }

    public void updateStep(SamsungGuideStep step) {
        if (visible && view != null) {
            view.setStep(step);
        }
    }

    public void showWaiting(SamsungGuideStep step, String message) {
        if (visible && view != null) {
            view.setStep(step, message);
        }
    }

    public boolean isVisible() {
        return visible;
    }

    public void hide() {
        if (!visible || view == null) {
            return;
        }
        try {
            windowManager.removeView(view);
        } catch (Throwable ignored) {
            // Window may already have been removed by the system.
        }
        view.stop();
        visible = false;
        params = null;
    }

    private WindowManager.LayoutParams buildParams() {
        int width = dp(292);
        WindowManager.LayoutParams value = new WindowManager.LayoutParams(
                width,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        value.gravity = Gravity.TOP | Gravity.START;
        Rect bounds = currentBounds();
        value.x = Math.max(dp(10), bounds.width() - width - dp(12));
        value.y = dp(70);
        value.windowAnimations = 0;
        return value;
    }

    private void moveBy(int deltaX, int deltaY) {
        if (!visible || params == null || view == null) {
            return;
        }
        Rect bounds = currentBounds();
        int maxX = Math.max(0, bounds.width() - params.width);
        int measuredHeight = view.getHeight() > 0 ? view.getHeight() : dp(210);
        int maxY = Math.max(0, bounds.height() - measuredHeight);
        params.x = clamp(params.x + deltaX, 0, maxX);
        params.y = clamp(params.y + deltaY, 0, maxY);
        try {
            windowManager.updateViewLayout(view, params);
        } catch (Throwable ignored) {
            // Keep the previous safe position if the window is being torn down.
        }
    }

    private Rect currentBounds() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return windowManager.getCurrentWindowMetrics().getBounds();
        }
        return new Rect(0, 0,
                activity.getResources().getDisplayMetrics().widthPixels,
                activity.getResources().getDisplayMetrics().heightPixels);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
