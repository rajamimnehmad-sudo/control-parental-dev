package com.glosh.remote.spike.wizard;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Compact, app-owned guide card shown above Samsung Settings through TYPE_APPLICATION_OVERLAY. */
public final class GuideOverlayView extends LinearLayout {
    public interface Listener {
        void onBack();
        void onNext();
        void onDragBy(int deltaX, int deltaY);
    }

    private static final int GRAPHITE = Color.rgb(25, 27, 24);
    private static final int MUTED = Color.rgb(92, 96, 88);
    private static final int LIME = Color.rgb(190, 242, 84);
    private static final int PANEL = Color.rgb(248, 248, 243);
    private static final int SOFT = Color.rgb(239, 241, 234);

    private final TextView progress;
    private final TextView title;
    private final TextView instruction;
    private final TextView next;
    private final OverlayIllustration illustration;
    private float lastRawX;
    private float lastRawY;

    public GuideOverlayView(Context context, Listener listener) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(dp(14), dp(12), dp(14), dp(12));
        setBackground(rounded(Color.WHITE, 20));
        setElevation(dp(14));
        setContentDescription("Guía flotante de Glosh para Ajustes Samsung");

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(7));

        TextView wordmark = text("glosh", 15, GRAPHITE, Typeface.BOLD);
        wordmark.setLetterSpacing(0.04f);
        header.addView(wordmark, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        progress = text("", 11, GRAPHITE, Typeface.BOLD);
        progress.setGravity(Gravity.CENTER);
        progress.setPadding(dp(9), dp(4), dp(9), dp(4));
        progress.setBackground(rounded(Color.rgb(234, 250, 202), 12));
        header.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(header, matchWrap());

        header.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> {
                    lastRawX = event.getRawX();
                    lastRawY = event.getRawY();
                    return true;
                }
                case MotionEvent.ACTION_MOVE -> {
                    int dx = Math.round(event.getRawX() - lastRawX);
                    int dy = Math.round(event.getRawY() - lastRawY);
                    lastRawX = event.getRawX();
                    lastRawY = event.getRawY();
                    listener.onDragBy(dx, dy);
                    return true;
                }
                case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    return true;
                }
                default -> {
                    return false;
                }
            }
        });

        title = text("", 17, GRAPHITE, Typeface.BOLD);
        title.setLineSpacing(0, 0.96f);
        addView(title, margins(0, 0, 0, 4));

        instruction = text("", 12, MUTED, Typeface.NORMAL);
        instruction.setLineSpacing(dp(2), 1f);
        addView(instruction, margins(0, 0, 0, 8));

        illustration = new OverlayIllustration(context);
        LinearLayout.LayoutParams visualParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(76));
        visualParams.setMargins(0, 0, 0, dp(9));
        addView(illustration, visualParams);

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = action("Atrás", false);
        back.setOnClickListener(view -> listener.onBack());
        actions.addView(back, weightedAction(0, 6));

        next = action("Ya está", true);
        next.setOnClickListener(view -> listener.onNext());
        LinearLayout.LayoutParams nextParams = weightedAction(dp(7), 10);
        actions.addView(next, nextParams);
        addView(actions, matchWrap());

        TextView hint = text("Arrastrá desde arriba para mover", 10, Color.rgb(126, 130, 121), Typeface.NORMAL);
        hint.setGravity(Gravity.CENTER);
        addView(hint, margins(0, 7, 0, 0));
    }

    public void setStep(SamsungGuideStep step) {
        setStep(step, null);
    }

    public void setStep(SamsungGuideStep step, String overrideInstruction) {
        SamsungGuideStep value = step == null ? SamsungGuideStep.ABOUT_PHONE : step;
        progress.setText("Paso " + value.number() + "/" + SamsungGuideStep.TOTAL_STEPS);
        title.setText(value.title());
        instruction.setText(overrideInstruction == null ? value.instruction() : overrideInstruction);
        next.setText(nextLabel(value));
        illustration.setStep(value);
    }

    public void stop() {
        illustration.stop();
    }

    private TextView action(String label, boolean primary) {
        TextView view = text(label, 12, GRAPHITE, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(42));
        view.setPadding(dp(10), dp(7), dp(10), dp(7));
        view.setBackground(rounded(primary ? LIME : SOFT, 14));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private LinearLayout.LayoutParams weightedAction(int leftMargin, int weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), weight);
        params.setMargins(leftMargin, 0, 0, 0);
        return params;
    }

    private TextView text(String value, float size, int color, int style) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), Color.rgb(229, 231, 224));
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams margins(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String nextLabel(SamsungGuideStep step) {
        return switch (step) {
            case ABOUT_PHONE, SOFTWARE_INFO -> "Ya lo abrí";
            case BUILD_NUMBER -> "Ya está activo";
            case DEVELOPER_OPTIONS -> "Ya estoy ahí";
            case WIRELESS_DEBUGGING -> "Ya la activé";
            case PAIR_DEVICE -> "Ya veo el código";
            case ENTER_CODE -> "Abrir Glosh";
        };
    }

    private static final class OverlayIllustration extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private SamsungGuideStep step = SamsungGuideStep.ABOUT_PHONE;
        private ValueAnimator animator;
        private float pulse;

        OverlayIllustration(Context context) {
            super(context);
            setBackground(roundedStatic(Color.rgb(247, 248, 243), dpStatic(context, 14)));
        }

        void setStep(SamsungGuideStep value) {
            step = value == null ? SamsungGuideStep.ABOUT_PHONE : value;
            start();
            invalidate();
        }

        private void start() {
            stop();
            if (!ValueAnimator.areAnimatorsEnabled()) {
                pulse = 0.65f;
                return;
            }
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(900L);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setRepeatMode(ValueAnimator.REVERSE);
            animator.setInterpolator(new AccelerateDecelerateInterpolator());
            animator.addUpdateListener(value -> {
                pulse = (float) value.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }

        void stop() {
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
            List<String> rows = step.visual().rows();
            if (rows.isEmpty()) {
                return;
            }
            float w = getWidth();
            float h = getHeight();
            float pad = dp(9);

            paint.setColor(MUTED);
            paint.setTextSize(dp(10));
            paint.setFakeBoldText(false);
            canvas.drawText(trim(rows.get(0), 28), pad, dp(18), paint);

            float top = dp(27);
            rect.set(pad, top, w - pad, h - dp(8));
            paint.setColor(LIME);
            canvas.drawRoundRect(rect, dp(11), dp(11), paint);

            String target = rows.get(rows.size() - 1);
            paint.setColor(GRAPHITE);
            paint.setTextSize(dp(12));
            paint.setFakeBoldText(true);
            canvas.drawText(trim(target, 31), rect.left + dp(10), rect.centerY() + dp(4), paint);

            float tapX = rect.right - dp(18);
            float radius = dp(7) + pulse * dp(3);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            canvas.drawCircle(tapX, rect.centerY(), radius, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(tapX, rect.centerY(), dp(3), paint);

            if (step == SamsungGuideStep.BUILD_NUMBER) {
                int tap = 1 + Math.min(6, Math.round(pulse * 6f));
                paint.setTextSize(dp(11));
                paint.setFakeBoldText(true);
                canvas.drawText("×" + tap, rect.right - dp(44), rect.top - dp(5), paint);
            }
        }

        private int dp(int value) {
            return dpStatic(getContext(), value);
        }

        private static GradientDrawable roundedStatic(int color, int radiusPx) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(color);
            drawable.setCornerRadius(radiusPx);
            return drawable;
        }

        private static int dpStatic(Context context, int value) {
            return Math.round(value * context.getResources().getDisplayMetrics().density);
        }

        private static String trim(String value, int limit) {
            return value.length() <= limit ? value : value.substring(0, limit - 1) + "…";
        }
    }
}
