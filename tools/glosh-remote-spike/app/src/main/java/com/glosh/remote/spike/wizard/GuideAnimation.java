package com.glosh.remote.spike.wizard;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import java.util.List;

public final class GuideAnimation extends View {
    public static final int HEIGHT_DP = 292;
    private static final int GRAPHITE = Color.rgb(25, 27, 24);
    private static final int MUTED = Color.rgb(104, 108, 100);
    private static final int LIME = Color.rgb(190, 242, 84);
    private static final int PANEL = Color.WHITE;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF phoneRect = new RectF();
    private final RectF screenRect = new RectF();
    private final RectF rowRect = new RectF();
    private OemGuideStep step;
    private ValueAnimator animator;
    private float progress;
    private boolean hostActive;

    public GuideAnimation(Context context) {
        super(context);
        setMinimumHeight(dp(HEIGHT_DP));
        setContentDescription("Tutorial visual de los pasos en Ajustes");
    }

    public void setStep(OemGuideStep value) {
        step = value;
        progress = 0f;
        restartIfAllowed();
        invalidate();
    }

    public void onHostResume() {
        hostActive = true;
        restartIfAllowed();
    }

    public void onHostPause() {
        hostActive = false;
        stopAnimator();
    }

    @Override
    protected void onDetachedFromWindow() {
        onHostPause();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(width, resolveSize(dp(HEIGHT_DP), heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (step == null || step.rows().isEmpty()) {
            return;
        }
        float left = dp(18);
        float top = dp(8);
        float right = getWidth() - dp(18);
        float bottom = getHeight() - dp(8);
        paint.setColor(GRAPHITE);
        phoneRect.set(left, top, right, bottom);
        canvas.drawRoundRect(phoneRect, dp(30), dp(30), paint);
        paint.setColor(PANEL);
        screenRect.set(left + dp(9), top + dp(18), right - dp(9), bottom - dp(12));
        canvas.drawRoundRect(screenRect, dp(22), dp(22), paint);

        int active = activeRow(step.rows());
        float rowTop = top + dp(46);
        float activeCenter = rowTop + dp(23);
        for (int index = 0; index < step.rows().size(); index++) {
            rowRect.set(left + dp(22), rowTop, right - dp(22), rowTop + dp(46));
            paint.setColor(index == active ? LIME : Color.rgb(243, 244, 238));
            canvas.drawRoundRect(rowRect, dp(13), dp(13), paint);
            paint.setColor(index == active ? GRAPHITE : MUTED);
            paint.setTextSize(dp(13));
            paint.setFakeBoldText(index == active);
            canvas.drawText(
                    trim(step.rows().get(index), 38),
                    rowRect.left + dp(14),
                    rowRect.centerY() + dp(5),
                    paint);
            if (index == active) {
                activeCenter = rowRect.centerY();
            }
            rowTop += dp(54);
        }

        paint.setColor(GRAPHITE);
        canvas.drawCircle(right - dp(42), activeCenter, dp(9), paint);
        paint.setColor(LIME);
        canvas.drawCircle(right - dp(42), activeCenter, dp(4), paint);

        if (step.showSevenTaps() && active == step.rows().size() - 1) {
            int tap = Math.min(7, 1 + Math.round(fractionWithinRow(step.rows()) * 6));
            paint.setColor(GRAPHITE);
            paint.setTextSize(dp(16));
            paint.setFakeBoldText(true);
            canvas.drawText("×" + tap, right - dp(58), bottom - dp(26), paint);
        }
    }

    private void restartIfAllowed() {
        stopAnimator();
        if (step == null || !GuideAnimationPolicy.shouldAnimate(hostActive, ValueAnimator.areAnimatorsEnabled())) {
            progress = step == null ? 0f : staticProgress(step);
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(Math.max(3_600L, step.rows().size() * 1_500L));
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.RESTART);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(value -> {
            progress = (float) value.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private void stopAnimator() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    private int activeRow(List<String> rows) {
        return Math.min(rows.size() - 1, (int) (progress * rows.size()));
    }

    private float fractionWithinRow(List<String> rows) {
        float scaled = progress * rows.size();
        return Math.max(0f, Math.min(1f, scaled - (int) scaled));
    }

    private static float staticProgress(OemGuideStep step) {
        return (step.highlightedRow() + 0.5f) / Math.max(1, step.rows().size());
    }

    private static String trim(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit - 1) + "…";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
