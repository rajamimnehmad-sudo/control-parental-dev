package com.glosh.remote.spike.guide.overlay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.glosh.remote.spike.R;

public final class GuideBubbleController {
    private static final int WIDTH_DP = 280;
    private static final int HEIGHT_DP = 160;

    private final Context context;
    private final WindowManager windowManager;
    private final LinearLayout card;
    private final TextView copy;
    private final TextView action;
    private final TextView close;
    private final Runnable rescue;
    private final Runnable openSettings;
    private final Runnable closeGuide;
    private boolean attached;
    private boolean minimized;
    private String instruction = "";
    private Runnable activeAction;
    private int activeActionLabel = R.string.live_guide_bubble_help;

    public GuideBubbleController(
            Context context,
            Runnable rescue,
            Runnable openSettings,
            Runnable closeGuide) {
        this.context = context;
        this.rescue = rescue;
        this.openSettings = openSettings;
        this.closeGuide = closeGuide;
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(14), dp(18), dp(12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(48, 52, 46));
        background.setCornerRadius(dp(18));
        card.setBackground(background);

        copy = new TextView(context);
        copy.setTextColor(Color.WHITE);
        copy.setTextSize(16);
        copy.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(copy);

        action = new TextView(context);
        action.setText(R.string.live_guide_bubble_help);
        action.setTextColor(Color.rgb(190, 242, 84));
        action.setTextSize(13);
        action.setTypeface(Typeface.DEFAULT_BOLD);
        action.setGravity(Gravity.END);
        action.setPadding(0, dp(9), 0, 0);
        action.setOnClickListener(view -> rescue.run());
        card.addView(action);

        close = new TextView(context);
        close.setText(R.string.live_guide_bubble_close);
        close.setTextColor(Color.rgb(205, 209, 199));
        close.setTextSize(12);
        close.setGravity(Gravity.END);
        close.setPadding(0, dp(8), 0, 0);
        close.setOnClickListener(view -> confirmClose());
        card.addView(close);
        copy.setOnClickListener(view -> toggleMinimized());
    }

    public void show(String instruction, Rect target) {
        activeAction = rescue;
        activeActionLabel = R.string.live_guide_bubble_help;
        restoreCurrentAction();
        display(instruction, target);
    }

    public void showRecovery(String instruction, Rect target) {
        activeAction = openSettings;
        activeActionLabel = R.string.live_guide_bubble_open_settings;
        restoreCurrentAction();
        display(instruction, target);
    }

    private void display(String instruction, Rect target) {
        this.instruction = instruction;
        close.setText(R.string.live_guide_bubble_close);
        close.setOnClickListener(view -> confirmClose());
        copy.setText(context.getString(R.string.live_guide_bubble_copy, instruction));
        Rect display = windowManager.getCurrentWindowMetrics().getBounds();
        WindowInsets windowInsets = windowManager.getCurrentWindowMetrics().getWindowInsets();
        Insets insets = windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        Point point = OverlayGeometry.bubblePosition(
                display, target, dp(WIDTH_DP), dp(HEIGHT_DP), insets);
        WindowManager.LayoutParams params = params(point.x, point.y);
        if (attached) {
            windowManager.updateViewLayout(card, params);
        } else {
            windowManager.addView(card, params);
            attached = true;
        }
    }

    public void clear() {
        if (attached) {
            windowManager.removeView(card);
            attached = false;
        }
        minimized = false;
    }

    private void toggleMinimized() {
        minimized = !minimized;
        action.setVisibility(minimized ? View.GONE : View.VISIBLE);
        close.setVisibility(minimized ? View.GONE : View.VISIBLE);
        copy.setText(minimized
                ? context.getString(R.string.live_guide_bubble_minimized)
                : context.getString(R.string.live_guide_bubble_copy, instruction));
    }

    private void confirmClose() {
        minimized = false;
        copy.setText(R.string.live_guide_bubble_close_confirm);
        action.setVisibility(View.VISIBLE);
        action.setText(R.string.live_guide_bubble_continue);
        action.setOnClickListener(view -> {
            restoreCurrentAction();
            close.setText(R.string.live_guide_bubble_close);
            close.setOnClickListener(ignored -> confirmClose());
            copy.setText(context.getString(R.string.live_guide_bubble_copy, instruction));
        });
        close.setVisibility(View.VISIBLE);
        close.setText(R.string.live_guide_bubble_close);
        close.setOnClickListener(view -> closeGuide.run());
    }

    private void restoreCurrentAction() {
        action.setText(activeActionLabel);
        action.setOnClickListener(view -> activeAction.run());
    }

    private WindowManager.LayoutParams params(int x, int y) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                dp(minimized ? 76 : WIDTH_DP),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = x;
        params.y = y;
        return params;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
