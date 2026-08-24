package com.glosh.remote.spike.guide.overlay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.glosh.remote.spike.R;

public final class CoachBarController {
    private static final int LIME = Color.rgb(190, 242, 84);

    private final Context context;
    private final WindowManager windowManager;
    private final LinearLayout bar;
    private final TextView copy;
    private final TextView showMe;
    private final TextView rescue;
    private final TextView close;
    private final Runnable onShowMe;
    private final Runnable onRescue;
    private final Runnable onClose;
    private boolean attached;
    private boolean confirmingClose;
    private Rect targetBounds;

    public CoachBarController(
            Context context,
            Runnable onShowMe,
            Runnable onRescue,
            Runnable onClose) {
        this.context = context;
        this.onShowMe = onShowMe;
        this.onRescue = onRescue;
        this.onClose = onClose;
        windowManager = context.getSystemService(WindowManager.class);

        bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), dp(8), dp(10), dp(8));
        bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(43, 47, 42));
        background.setCornerRadius(dp(16));
        bar.setBackground(background);

        copy = text(15, Color.WHITE, true);
        bar.addView(copy, new LinearLayout.LayoutParams(0, dp(44), 1f));

        showMe = action(R.string.live_guide_coach_show_me);
        showMe.setOnClickListener(view -> onShowMe.run());
        bar.addView(showMe);

        rescue = action(R.string.live_guide_coach_rescue);
        rescue.setOnClickListener(view -> onRescue.run());
        bar.addView(rescue);

        close = action(R.string.live_guide_coach_close);
        close.setTextSize(22);
        close.setTextColor(Color.rgb(215, 220, 211));
        close.setOnClickListener(view -> confirmClose());
        bar.addView(close);
    }

    public void show(String instruction, boolean revealAvailable) {
        show(instruction, revealAvailable, null);
    }

    public void show(String instruction, boolean revealAvailable, Rect targetBounds) {
        confirmingClose = false;
        this.targetBounds = targetBounds == null ? null : new Rect(targetBounds);
        copy.setText(instruction);
        showMe.setVisibility(revealAvailable ? View.VISIBLE : View.GONE);
        rescue.setVisibility(View.VISIBLE);
        rescue.setText(R.string.live_guide_coach_rescue);
        rescue.setOnClickListener(view -> onRescue.run());
        close.setText(R.string.live_guide_coach_close);
        close.setOnClickListener(view -> confirmClose());
        attachOrUpdate();
    }

    public void showRecovery(String message) {
        confirmingClose = false;
        targetBounds = null;
        copy.setText(message);
        showMe.setVisibility(View.GONE);
        rescue.setVisibility(View.VISIBLE);
        rescue.setText(R.string.live_guide_coach_rescue);
        rescue.setOnClickListener(view -> onRescue.run());
        close.setText(R.string.live_guide_coach_close);
        close.setOnClickListener(view -> confirmClose());
        attachOrUpdate();
    }

    public void clear() {
        if (attached) {
            windowManager.removeView(bar);
            attached = false;
        }
        confirmingClose = false;
        targetBounds = null;
    }

    private void confirmClose() {
        if (confirmingClose) {
            onClose.run();
            return;
        }
        confirmingClose = true;
        copy.setText(R.string.live_guide_coach_close_confirm);
        showMe.setVisibility(View.GONE);
        rescue.setText(R.string.live_guide_coach_continue);
        rescue.setOnClickListener(view -> {
            rescue.setText(R.string.live_guide_coach_rescue);
            rescue.setOnClickListener(ignored -> onRescue.run());
            confirmingClose = false;
            onRescue.run();
        });
        close.setText(R.string.live_guide_coach_close_confirm_action);
    }

    private void attachOrUpdate() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        Rect display = windowManager.getCurrentWindowMetrics().getBounds();
        Insets insets = windowManager.getCurrentWindowMetrics()
                .getWindowInsets()
                .getInsetsIgnoringVisibility(
                        android.view.WindowInsets.Type.systemBars()
                                | android.view.WindowInsets.Type.displayCutout());
        boolean atTop = OverlayGeometry.coachAtTop(
                targetBounds == null ? null : new OverlayGeometry.Box(
                        targetBounds.left, targetBounds.top, targetBounds.right, targetBounds.bottom),
                new OverlayGeometry.Box(display.left, display.top, display.right, display.bottom),
                new OverlayGeometry.EdgeInsets(
                        insets.left, insets.top, insets.right, insets.bottom),
                dp(68), dp(12));
        params.gravity = atTop ? Gravity.TOP : Gravity.BOTTOM;
        params.horizontalMargin = 0.025f;
        params.y = dp(12);
        if (attached) {
            windowManager.updateViewLayout(bar, params);
        } else {
            windowManager.addView(bar, params);
            attached = true;
        }
    }

    private TextView action(int label) {
        TextView view = text(11, LIME, true);
        view.setText(label);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(8), 0, dp(8), 0);
        return view;
    }

    private TextView text(int sizeSp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return view;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
