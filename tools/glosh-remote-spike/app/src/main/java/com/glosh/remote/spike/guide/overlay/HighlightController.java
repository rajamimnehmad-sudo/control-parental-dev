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

import com.glosh.remote.spike.guide.accessibility.ScanGenerationGuard;
import com.glosh.remote.spike.guide.accessibility.SettingsSnapshot;

public final class HighlightController {
    private static final int LIME = Color.rgb(190, 242, 84);

    private final WindowManager windowManager;
    private final ScanGenerationGuard guard;
    private final HighlightView view;
    private boolean attached;

    public HighlightController(Context context, ScanGenerationGuard guard) {
        windowManager = context.getSystemService(WindowManager.class);
        this.guard = guard;
        view = new HighlightView(context);
    }

    public boolean show(
            ScanGenerationGuard.Token token,
            SettingsSnapshot snapshot,
            Rect bounds) {
        if (!guard.isCurrent(token, snapshot) || bounds == null || bounds.isEmpty()) {
            clear();
            return false;
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
        return true;
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
        private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF target = new RectF();
        private final RectF frame = new RectF();
        private ValueAnimator animator;
        private float alpha = 1f;

        HighlightView(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            framePaint.setStyle(Paint.Style.STROKE);
            framePaint.setStrokeWidth(dp(4));
            framePaint.setColor(LIME);
            markerPaint.setStyle(Paint.Style.FILL);
            markerPaint.setColor(LIME);
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
            animator = ValueAnimator.ofFloat(0.58f, 1f);
            animator.setDuration(950);
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
            framePaint.setAlpha(Math.round(alpha * 255));
            float padding = dp(5);
            int[] windowOrigin = new int[2];
            getLocationOnScreen(windowOrigin);
            Rect localTarget = OverlayGeometry.toLocal(
                    new Rect(
                            Math.round(target.left),
                            Math.round(target.top),
                            Math.round(target.right),
                            Math.round(target.bottom)),
                    windowOrigin[0],
                    windowOrigin[1]);
            frame.set(localTarget.left - padding, localTarget.top - padding,
                    localTarget.right + padding, localTarget.bottom + padding);
            canvas.drawRoundRect(frame, dp(13), dp(13), framePaint);
            markerPaint.setAlpha(framePaint.getAlpha());
            canvas.drawCircle(frame.left + dp(12), frame.centerY(), dp(6), markerPaint);
        }

        private float dp(int value) {
            return value * getResources().getDisplayMetrics().density;
        }
    }
}
