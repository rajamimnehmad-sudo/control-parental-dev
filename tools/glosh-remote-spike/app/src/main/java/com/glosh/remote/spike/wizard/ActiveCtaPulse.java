package com.glosh.remote.spike.wizard;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;

public final class ActiveCtaPulse {
    private static final long DURATION_MS = 1_100;
    private static final float MAX_SCALE = 1.025f;
    private static final float MIN_ALPHA = 0.92f;

    private final int limeColor;
    private Button target;
    private Drawable originalBackground;
    private AnimatorSet animator;
    private boolean hostActive;

    public ActiveCtaPulse(int limeColor) {
        this.limeColor = limeColor;
    }

    public void setTarget(Button button) {
        if (target == button) {
            return;
        }
        stopAnimation();
        target = button;
        startIfAllowed();
    }

    public void clear() {
        stopAnimation();
        target = null;
    }

    public void onHostResume() {
        hostActive = true;
        startIfAllowed();
    }

    public void onHostPause() {
        hostActive = false;
        stopAnimation();
    }

    private void startIfAllowed() {
        if (!hostActive || target == null || !ValueAnimator.areAnimatorsEnabled()) {
            return;
        }
        originalBackground = target.getBackground();
        GradientDrawable highlight = new GradientDrawable();
        highlight.setColor(limeColor);
        highlight.setCornerRadius(dp(target, 18));
        target.setBackground(highlight);

        ObjectAnimator scaleX = pulse(target, View.SCALE_X, 1f, MAX_SCALE);
        ObjectAnimator scaleY = pulse(target, View.SCALE_Y, 1f, MAX_SCALE);
        ObjectAnimator alpha = pulse(target, View.ALPHA, 1f, MIN_ALPHA);
        animator = new AnimatorSet();
        animator.playTogether(scaleX, scaleY, alpha);
        animator.start();
    }

    private ObjectAnimator pulse(Button button, android.util.Property<View, Float> property, float... values) {
        ObjectAnimator value = ObjectAnimator.ofFloat(button, property, values);
        value.setDuration(DURATION_MS);
        value.setRepeatCount(ValueAnimator.INFINITE);
        value.setRepeatMode(ValueAnimator.REVERSE);
        value.setInterpolator(new AccelerateDecelerateInterpolator());
        return value;
    }

    private void stopAnimation() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        if (target != null) {
            target.setScaleX(1f);
            target.setScaleY(1f);
            target.setAlpha(1f);
            if (originalBackground != null) {
                target.setBackground(originalBackground);
            }
        }
        originalBackground = null;
    }

    private static float dp(View view, int value) {
        return value * view.getResources().getDisplayMetrics().density;
    }
}
