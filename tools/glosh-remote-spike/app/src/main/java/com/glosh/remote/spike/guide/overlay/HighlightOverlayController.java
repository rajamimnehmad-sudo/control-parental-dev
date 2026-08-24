package com.glosh.remote.spike.guide.overlay;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

public final class HighlightOverlayController {
    private static final int LIME = Color.rgb(190, 242, 84);

    private final WindowManager windowManager;
    private final HighlightView view;
    private boolean attached;

    public HighlightOverlayController(Context context) {
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        view = new HighlightView(context);
    }

    public void show(Rect bounds) {
        if (bounds == null || bounds.isEmpty()) {
            clear();
            return;
        }
        view.setTarget(bounds);
        if (!attached) {
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    android.graphics.PixelFormat.TRANSLUCENT);
            windowManager.addView(view, params);
            attached = true;
        }
        view.startPulse();
    }

    public void clear() {
        view.stopPulse();
        if (attached) {
            windowManager.removeView(view);
            attached = false;
        }
    }

    public boolean isAttached() {
        return attached;
    }

    private static final class HighlightView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arrow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF target = new RectF();
        private final RectF frame = new RectF();
        private ValueAnimator animator;
        private float alpha = 1f;

        HighlightView(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            setAccessibilityDelegate(new AccessibilityDelegate() {
                @Override
                public void onInitializeAccessibilityEvent(View host, AccessibilityEvent event) {
                    super.onInitializeAccessibilityEvent(host, event);
                    event.setContentDescription(null);
                }
            });
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(4));
            paint.setColor(LIME);
            arrow.setStyle(Paint.Style.FILL);
            arrow.setColor(LIME);
        }

        void setTarget(Rect bounds) {
            target.set(bounds);
            invalidate();
        }

        void startPulse() {
            stopPulse();
            if (!OverlayMotionPolicy.shouldPulse(ValueAnimator.areAnimatorsEnabled())) {
                alpha = 1f;
                invalidate();
                return;
            }
            animator = ValueAnimator.ofFloat(0.55f, 1f);
            animator.setDuration(900);
            animator.setRepeatMode(ValueAnimator.REVERSE);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.addUpdateListener(value -> {
                alpha = (float) value.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }

        void stopPulse() {
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            paint.setAlpha(Math.round(alpha * 255));
            float padding = dp(5);
            frame.set(
                    target.left - padding,
                    target.top - padding,
                    target.right + padding,
                    target.bottom + padding);
            canvas.drawRoundRect(frame, dp(13), dp(13), paint);
            arrow.setAlpha(paint.getAlpha());
            canvas.drawCircle(frame.left + dp(12), frame.centerY(), dp(6), arrow);
        }

        private float dp(int value) {
            return value * getResources().getDisplayMetrics().density;
        }
    }
}
