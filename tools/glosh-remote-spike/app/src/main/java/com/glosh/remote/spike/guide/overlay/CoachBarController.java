package com.glosh.remote.spike.guide.overlay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.glosh.remote.spike.guide.state.GuideStage;
import com.glosh.remote.spike.guide.state.LiveGuideRuntime;
import com.glosh.remote.spike.wizard.GuidePresentation;

/** Compact, non-modal coach shown above Android Settings. */
public final class CoachBarController {
    private static final int LIME = Color.rgb(190, 242, 84);
    private static final int MUTED = Color.rgb(191, 198, 186);
    private static final int SEGMENT_OFF = Color.rgb(76, 82, 72);

    private final Context context;
    private final WindowManager windowManager;
    private final FrameLayout root;
    private final LinearLayout card;
    private final GuideCueView cue;
    private final TextView progress;
    private final TextView title;
    private final TextView body;
    private final TextView hide;
    private final View[] progressSegments = new View[4];
    private boolean attached;
    private boolean userHidden;
    private String presentationKey = "";
    private Rect targetBounds;

    public CoachBarController(
            Context context,
            Runnable ignoredShowMe,
            Runnable ignoredRescue,
            Runnable ignoredClose) {
        this.context = context;
        windowManager = context.getSystemService(WindowManager.class);

        root = new FrameLayout(context);
        root.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

        card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(12), dp(12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(248, 27, 30, 26));
        background.setCornerRadius(dp(20));
        background.setStroke(dp(1), Color.argb(72, 190, 242, 84));
        card.setBackground(background);
        card.setElevation(dp(10));
        root.addView(card, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout progressRow = new LinearLayout(context);
        progressRow.setOrientation(LinearLayout.HORIZONTAL);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);
        progress = text(11, LIME, Typeface.BOLD);
        progressRow.addView(progress, new LinearLayout.LayoutParams(0, dp(22), 1f));
        hide = text(19, MUTED, Typeface.NORMAL);
        hide.setText("×");
        hide.setGravity(Gravity.CENTER);
        hide.setPadding(dp(10), 0, dp(4), 0);
        hide.setOnClickListener(view -> hideForCurrentStep());
        progressRow.addView(hide, new LinearLayout.LayoutParams(dp(38), dp(30)));
        card.addView(progressRow, matchWrap());

        LinearLayout segments = new LinearLayout(context);
        segments.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams segmentParams = new LinearLayout.LayoutParams(0, dp(3), 1f);
        segmentParams.setMargins(0, 0, dp(5), 0);
        for (int index = 0; index < progressSegments.length; index++) {
            View segment = new View(context);
            progressSegments[index] = segment;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(segmentParams);
            if (index == progressSegments.length - 1) {
                params.setMargins(0, 0, 0, 0);
            }
            segments.addView(segment, params);
        }
        LinearLayout.LayoutParams segmentsLayout = matchWrap();
        segmentsLayout.setMargins(0, 0, 0, dp(10));
        card.addView(segments, segmentsLayout);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        cue = new GuideCueView(context);
        LinearLayout.LayoutParams cueParams = new LinearLayout.LayoutParams(dp(50), dp(50));
        cueParams.setMargins(0, 0, dp(12), 0);
        content.addView(cue, cueParams);

        LinearLayout copy = new LinearLayout(context);
        copy.setOrientation(LinearLayout.VERTICAL);
        title = text(16, Color.WHITE, Typeface.BOLD);
        title.setMaxLines(2);
        body = text(13, MUTED, Typeface.NORMAL);
        body.setMaxLines(3);
        body.setLineSpacing(dp(2), 1f);
        copy.addView(title, matchWrap());
        LinearLayout.LayoutParams bodyParams = matchWrap();
        bodyParams.setMargins(0, dp(3), 0, 0);
        copy.addView(body, bodyParams);
        content.addView(copy, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(content, matchWrap());
    }

    public void show(String instruction, boolean revealAvailable) {
        show(instruction, revealAvailable, null);
    }

    public void show(String instruction, boolean revealAvailable, Rect targetBounds) {
        this.targetBounds = targetBounds == null ? null : new Rect(targetBounds);
        render(GuidePresentation.forStage(LiveGuideRuntime.stage(), instruction));
    }

    public void showWaiting(String message) {
        targetBounds = null;
        render(GuidePresentation.waiting(LiveGuideRuntime.stage(), message));
    }

    public void showRecovery(String message) {
        targetBounds = null;
        render(GuidePresentation.recovery(LiveGuideRuntime.stage(), message));
    }

    public void clear() {
        userHidden = false;
        presentationKey = "";
        detach();
        targetBounds = null;
    }

    private void hideForCurrentStep() {
        userHidden = true;
        detach();
    }

    private void detach() {
        cue.onHostPause();
        if (attached) {
            try {
                windowManager.removeView(root);
            } catch (Throwable ignored) {
                // The system may already have detached the accessibility overlay.
            }
            attached = false;
        }
    }

    private void render(GuidePresentation presentation) {
        String nextKey = presentation.step()
                + "|" + presentation.title()
                + "|" + presentation.body()
                + "|" + presentation.cue();
        if (!nextKey.equals(presentationKey)) {
            presentationKey = nextKey;
            userHidden = false;
        }
        progress.setText("Glosh · " + presentation.progressLabel());
        title.setText(presentation.title());
        body.setText(presentation.body());
        cue.setCue(presentation.cue());
        for (int index = 0; index < progressSegments.length; index++) {
            progressSegments[index].setBackground(rounded(
                    index < presentation.progressValue() ? LIME : SEGMENT_OFF,
                    3));
        }
        if (userHidden) {
            cue.onHostPause();
            return;
        }
        attachOrUpdate();
        cue.onHostResume();
    }

    private void attachOrUpdate() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        WindowMetrics metrics = windowManager.getCurrentWindowMetrics();
        Rect display = metrics.getBounds();
        WindowInsets windowInsets = metrics.getWindowInsets();
        Insets insets = windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        boolean credential = LiveGuideRuntime.stage() == GuideStage.AUTOPILOT_CREDENTIAL;
        boolean imeVisible = windowInsets.isVisible(WindowInsets.Type.ime());
        boolean keyboardSafe = credential || imeVisible;

        body.setMaxLines(keyboardSafe ? 1 : 3);
        card.setPadding(
                dp(14),
                dp(keyboardSafe ? 8 : 12),
                dp(12),
                dp(keyboardSafe ? 8 : 12));

        boolean atTop = keyboardSafe || OverlayGeometry.coachAtTop(
                targetBounds == null ? null : new OverlayGeometry.Box(
                        targetBounds.left,
                        targetBounds.top,
                        targetBounds.right,
                        targetBounds.bottom),
                new OverlayGeometry.Box(display.left, display.top, display.right, display.bottom),
                new OverlayGeometry.EdgeInsets(
                        insets.left, insets.top, insets.right, insets.bottom),
                dp(112),
                dp(10));
        params.gravity = atTop ? Gravity.TOP : Gravity.BOTTOM;
        params.y = dp(keyboardSafe ? 2 : 8);
        if (attached) {
            windowManager.updateViewLayout(root, params);
        } else {
            windowManager.addView(root, params);
            attached = true;
        }
    }

    private TextView text(float sizeSp, int color, int style) {
        TextView view = new TextView(context);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
