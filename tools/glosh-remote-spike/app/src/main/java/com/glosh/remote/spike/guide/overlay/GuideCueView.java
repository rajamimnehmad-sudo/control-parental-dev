package com.glosh.remote.spike.guide.overlay;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import com.glosh.remote.spike.wizard.GuidePresentation;

/** Small line-art cue used by both the floating coach and the in-app step card. */
public final class GuideCueView extends View {
    private static final int LIME = Color.rgb(190, 242, 84);
    private static final int MUTED = Color.rgb(141, 150, 136);
    private static final long DURATION_MS = 1_250L;

    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private GuidePresentation.Cue cue = GuidePresentation.Cue.WAIT;
    private ValueAnimator animator;
    private float progress;

    public GuideCueView(Context context) {
        super(context);
        setMinimumWidth(dp(48));
        setMinimumHeight(dp(48));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setStrokeWidth(dp(2.4f));
        stroke.setColor(LIME);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(LIME);
        text.setColor(LIME);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        text.setTextSize(dp(10));
    }

    public void setCue(GuidePresentation.Cue value) {
        cue = value == null ? GuidePresentation.Cue.WAIT : value;
        restart();
        invalidate();
    }

    public void onHostResume() {
        restart();
    }

    public void onHostPause() {
        stop();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        restart();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float cx = width / 2f;
        float cy = height / 2f;
        switch (cue) {
            case TAP -> drawTap(canvas, cx, cy, false);
            case MULTI_TAP -> drawTap(canvas, cx, cy, true);
            case TOGGLE -> drawToggle(canvas, cx, cy);
            case CODE -> drawCode(canvas, cx, cy);
            case SUCCESS -> drawSuccess(canvas, cx, cy);
            case ATTENTION -> drawAttention(canvas, cx, cy);
            case WAIT -> drawWait(canvas, cx, cy);
        }
    }

    private void drawTap(Canvas canvas, float cx, float cy, boolean multi) {
        float pulse = easedPulse();
        stroke.setAlpha(Math.round((1f - pulse) * 170));
        canvas.drawCircle(cx - dp(6), cy - dp(5), dp(6) + dp(6) * pulse, stroke);
        stroke.setAlpha(255);
        float fingerY = cy + dp(9) - dp(5) * pulse;
        canvas.drawCircle(cx - dp(6), fingerY - dp(10), dp(3.5f), fill);
        canvas.drawLine(cx - dp(6), fingerY - dp(6), cx - dp(6), fingerY + dp(5), stroke);
        canvas.drawLine(cx - dp(6), fingerY + dp(5), cx + dp(1), fingerY + dp(8), stroke);
        if (multi) {
            canvas.drawText("×7", cx + dp(11), cy + dp(14), text);
        }
    }

    private void drawToggle(Canvas canvas, float cx, float cy) {
        RectF track = new RectF(cx - dp(17), cy - dp(8), cx + dp(17), cy + dp(8));
        Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setStyle(Paint.Style.FILL);
        trackPaint.setColor(Color.argb(72, 190, 242, 84));
        canvas.drawRoundRect(track, dp(9), dp(9), trackPaint);
        stroke.setAlpha(220);
        canvas.drawRoundRect(track, dp(9), dp(9), stroke);
        stroke.setAlpha(255);
        float x = track.left + dp(8) + (track.width() - dp(16)) * easedPulse();
        canvas.drawCircle(x, cy, dp(6), fill);
        float ring = dp(9) + dp(4) * easedPulse();
        stroke.setAlpha(Math.round((1f - easedPulse()) * 150));
        canvas.drawCircle(x, cy, ring, stroke);
        stroke.setAlpha(255);
    }

    private void drawCode(Canvas canvas, float cx, float cy) {
        float box = dp(5.2f);
        float gap = dp(2.3f);
        float total = box * 6 + gap * 5;
        float start = cx - total / 2f;
        for (int index = 0; index < 6; index++) {
            float phase = (progress + index * 0.11f) % 1f;
            float alpha = 0.45f + 0.55f * (1f - Math.abs(phase * 2f - 1f));
            fill.setAlpha(Math.round(alpha * 255));
            RectF rect = new RectF(
                    start + index * (box + gap),
                    cy - box / 2f,
                    start + index * (box + gap) + box,
                    cy + box / 2f);
            canvas.drawRoundRect(rect, dp(1.5f), dp(1.5f), fill);
        }
        fill.setAlpha(255);
    }

    private void drawWait(Canvas canvas, float cx, float cy) {
        RectF oval = new RectF(cx - dp(13), cy - dp(13), cx + dp(13), cy + dp(13));
        stroke.setAlpha(70);
        canvas.drawArc(oval, 0, 360, false, stroke);
        stroke.setAlpha(255);
        canvas.drawArc(oval, progress * 360f - 90f, 105f, false, stroke);
    }

    private void drawSuccess(Canvas canvas, float cx, float cy) {
        stroke.setStrokeWidth(dp(3f));
        path.reset();
        path.moveTo(cx - dp(12), cy);
        path.lineTo(cx - dp(3), cy + dp(9));
        path.lineTo(cx + dp(14), cy - dp(10));
        canvas.drawPath(path, stroke);
        stroke.setStrokeWidth(dp(2.4f));
    }

    private void drawAttention(Canvas canvas, float cx, float cy) {
        path.reset();
        path.moveTo(cx, cy - dp(15));
        path.lineTo(cx - dp(16), cy + dp(13));
        path.lineTo(cx + dp(16), cy + dp(13));
        path.close();
        stroke.setAlpha(255);
        canvas.drawPath(path, stroke);
        canvas.drawLine(cx, cy - dp(6), cx, cy + dp(4), stroke);
        canvas.drawCircle(cx, cy + dp(9), dp(1.6f), fill);
    }

    private float easedPulse() {
        float triangle = 1f - Math.abs(progress * 2f - 1f);
        return triangle * triangle * (3f - 2f * triangle);
    }

    private void restart() {
        stop();
        if (!ValueAnimator.areAnimatorsEnabled() || cue == GuidePresentation.Cue.SUCCESS) {
            progress = cue == GuidePresentation.Cue.TOGGLE ? 1f : 0.5f;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(DURATION_MS);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.RESTART);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(value -> {
            progress = (float) value.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private void stop() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
