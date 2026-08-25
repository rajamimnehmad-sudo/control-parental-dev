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

/** Compact animated instructor specifically designed for Android Picture-in-Picture. */
public final class SamsungPipCoachView extends View {
    private static final int GRAPHITE = Color.rgb(24, 27, 24);
    private static final int LIME = Color.rgb(190, 242, 84);
    private static final int WHITE = Color.WHITE;
    private static final int MUTED = Color.rgb(196, 202, 192);
    private static final int PANEL = Color.rgb(245, 246, 241);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private SamsungGuideStep step = SamsungGuideStep.ABOUT_PHONE;
    private ValueAnimator animator;
    private float pulse;

    public SamsungPipCoachView(Context context) {
        super(context);
        setBackgroundColor(GRAPHITE);
        setContentDescription("Instructor Glosh para Ajustes Samsung");
    }

    public void setStep(SamsungGuideStep value) {
        step = value == null ? SamsungGuideStep.ABOUT_PHONE : value;
        invalidate();
    }

    public void start() {
        stop();
        if (!ValueAnimator.areAnimatorsEnabled()) {
            pulse = 0.65f;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(950L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(value -> {
            pulse = (float) value.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    public void stop() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        float pad = Math.max(8f, h * 0.075f);
        float visualWidth = Math.min(w * 0.43f, h * 0.85f);
        float visualLeft = pad;
        float visualRight = visualLeft + visualWidth;
        float visualTop = pad;
        float visualBottom = h - pad;

        paint.setColor(PANEL);
        rect.set(visualLeft, visualTop, visualRight, visualBottom);
        canvas.drawRoundRect(rect, h * 0.08f, h * 0.08f, paint);

        List<String> rows = step.visual().rows();
        float rowHeight = (visualBottom - visualTop) * 0.26f;
        float rowGap = (visualBottom - visualTop) * 0.08f;
        float rowTop = visualTop + (visualBottom - visualTop) * 0.18f;
        float targetCenterY = rowTop + rowHeight / 2f;
        for (int i = 0; i < rows.size(); i++) {
            paint.setColor(i == rows.size() - 1 ? LIME : Color.rgb(225, 228, 219));
            rect.set(
                    visualLeft + visualWidth * 0.08f,
                    rowTop,
                    visualRight - visualWidth * 0.08f,
                    rowTop + rowHeight);
            canvas.drawRoundRect(rect, rowHeight * 0.28f, rowHeight * 0.28f, paint);
            paint.setColor(GRAPHITE);
            paint.setFakeBoldText(i == rows.size() - 1);
            paint.setTextSize(Math.max(9f, h * 0.085f));
            canvas.drawText(trim(rows.get(i), 24), rect.left + 8f, rect.centerY() + paint.getTextSize() * 0.34f, paint);
            if (i == rows.size() - 1) {
                targetCenterY = rect.centerY();
            }
            rowTop += rowHeight + rowGap;
        }

        float tapX = visualRight - visualWidth * 0.15f;
        float radius = h * (0.035f + pulse * 0.025f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, h * 0.012f));
        paint.setColor(GRAPHITE);
        canvas.drawCircle(tapX, targetCenterY, radius, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(tapX, targetCenterY, Math.max(3f, h * 0.018f), paint);

        if (step == SamsungGuideStep.BUILD_NUMBER) {
            int tap = 1 + Math.min(6, Math.round(pulse * 6f));
            paint.setColor(GRAPHITE);
            paint.setFakeBoldText(true);
            paint.setTextSize(Math.max(11f, h * 0.11f));
            canvas.drawText("×" + tap, visualRight - visualWidth * 0.28f, visualBottom - h * 0.08f, paint);
        }

        float copyLeft = visualRight + pad;
        paint.setColor(LIME);
        paint.setFakeBoldText(true);
        paint.setTextSize(Math.max(10f, h * 0.09f));
        canvas.drawText(
                "PASO " + step.number() + " DE " + SamsungGuideStep.TOTAL_STEPS,
                copyLeft,
                h * 0.27f,
                paint);

        paint.setColor(WHITE);
        paint.setTextSize(Math.max(12f, h * 0.135f));
        drawWrapped(canvas, step.title(), copyLeft, h * 0.47f, w - copyLeft - pad, paint, 2);

        paint.setColor(MUTED);
        paint.setFakeBoldText(false);
        paint.setTextSize(Math.max(9f, h * 0.075f));
        canvas.drawText("Tocá la ventana para Atrás / Siguiente", copyLeft, h * 0.84f, paint);
    }

    private void drawWrapped(
            Canvas canvas,
            String text,
            float x,
            float y,
            float maxWidth,
            Paint paint,
            int maxLines) {
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        int lines = 0;
        float baseline = y;
        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (paint.measureText(candidate) <= maxWidth || line.length() == 0) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            canvas.drawText(line.toString(), x, baseline, paint);
            lines++;
            if (lines >= maxLines) {
                return;
            }
            baseline += paint.getTextSize() * 1.15f;
            line.setLength(0);
            line.append(word);
        }
        if (line.length() > 0 && lines < maxLines) {
            canvas.drawText(line.toString(), x, baseline, paint);
        }
    }

    private static String trim(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit - 1) + "…";
    }
}
