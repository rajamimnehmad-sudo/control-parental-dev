package com.glosh.remote.spike.wizard;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

@SuppressLint("SetTextI18n")
public final class WizardLayout {
    public static final int COLOR_LIME = Color.rgb(190, 242, 84);
    private static final int COLOR_BACKGROUND = Color.rgb(248, 248, 243);
    private static final int COLOR_GRAPHITE = Color.rgb(25, 27, 24);
    private static final int COLOR_MUTED = Color.rgb(92, 96, 88);
    private static final int COLOR_LIME_SOFT = Color.rgb(234, 250, 202);
    private static final int COLOR_LINE = Color.rgb(218, 221, 211);

    private final Activity activity;
    private final ScrollView root;
    private final TextView progress;
    private final TextView title;
    private final TextView body;
    private final TextView information;
    private final LinearLayout visualSlot;
    private final Button primary;
    private final Button secondary;
    private final Button tertiary;
    private final LinearLayout homeDetails;
    private final ActiveCtaPulse pulse = new ActiveCtaPulse(COLOR_LIME);
    private GuideAnimation guideAnimation;
    private PairingCodeInputView pairingInput;
    private boolean hostActive;

    public WizardLayout(Activity activity) {
        this.activity = activity;
        root = new ScrollView(activity);
        root.setFillViewport(true);
        root.setBackgroundColor(COLOR_BACKGROUND);

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(26), dp(28), dp(26), dp(38));
        root.addView(column, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView wordmark = text("glosh", 20, COLOR_GRAPHITE, Typeface.BOLD);
        wordmark.setLetterSpacing(0.04f);
        add(column, wordmark, 0, 34);

        progress = text("", 14, COLOR_MUTED, Typeface.BOLD);
        progress.setVisibility(View.GONE);
        add(column, progress, 0, 12);

        title = text("", 38, COLOR_GRAPHITE, Typeface.BOLD);
        title.setLineSpacing(0, 0.94f);
        add(column, title, 0, 12);

        body = text("", 18, COLOR_MUTED, Typeface.NORMAL);
        body.setLineSpacing(dp(4), 1f);
        add(column, body, 0, 18);

        information = text("", 16, COLOR_GRAPHITE, Typeface.NORMAL);
        information.setLineSpacing(dp(5), 1f);
        information.setPadding(dp(16), dp(15), dp(16), dp(15));
        information.setBackground(rounded(Color.WHITE, 16));
        add(column, information, 0, 16);

        visualSlot = new LinearLayout(activity);
        visualSlot.setOrientation(LinearLayout.VERTICAL);
        add(column, visualSlot, 0, 18);

        primary = button(true);
        add(column, primary, 0, 6);
        secondary = button(false);
        add(column, secondary, 0, 2);
        tertiary = button(false);
        tertiary.setTextColor(COLOR_MUTED);
        add(column, tertiary, 0, 24);

        homeDetails = buildHomeDetails();
        add(column, homeDetails, 0, 0);
    }

    public View view() {
        return root;
    }

    public void onHostResume() {
        hostActive = true;
        pulse.onHostResume();
        if (guideAnimation != null) {
            guideAnimation.onHostResume();
        }
    }

    public void onHostPause() {
        hostActive = false;
        pulse.onHostPause();
        if (guideAnimation != null) {
            guideAnimation.onHostPause();
        }
    }

    public void showHome(View.OnClickListener connect) {
        progress.setVisibility(View.GONE);
        title.setText("¡Bienvenido, Glosher!");
        body.setText("Te estábamos esperando.\n\nGlosh detecta tu teléfono y te muestra exactamente qué tocar.");
        information.setVisibility(View.GONE);
        clearVisual();
        homeDetails.setVisibility(View.VISIBLE);
        showPrimary("CONECTAR CON SOPORTE", connect, true);
        hide(secondary);
        hide(tertiary);
    }

    public void showScreen(String progressCopy, String titleCopy, String bodyCopy, String infoCopy) {
        progress.setText(progressCopy);
        progress.setVisibility(View.VISIBLE);
        title.setText(titleCopy);
        body.setText(bodyCopy);
        information.setText(infoCopy);
        information.setVisibility(infoCopy == null || infoCopy.isEmpty() ? View.GONE : View.VISIBLE);
        homeDetails.setVisibility(View.GONE);
        hide(primary);
        hide(secondary);
        hide(tertiary);
        pulse.clear();
    }

    public void showGuide(OemGuideStep step) {
        clearVisual();
        guideAnimation = new GuideAnimation(activity);
        guideAnimation.setStep(step);
        visualSlot.addView(guideAnimation, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(GuideAnimation.HEIGHT_DP)));
        if (hostActive) {
            guideAnimation.onHostResume();
        }
    }

    public void showPairingInput(PairingCodeController.Listener listener, boolean retry) {
        clearVisual();
        pairingInput = new PairingCodeInputView(activity, listener);
        visualSlot.addView(pairingInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        if (retry) {
            pairingInput.allowRetry();
        } else {
            pairingInput.focusKeyboard();
        }
    }

    public void clearVisual() {
        if (guideAnimation != null) {
            guideAnimation.onHostPause();
            guideAnimation = null;
        }
        pairingInput = null;
        visualSlot.removeAllViews();
    }

    public void showPrimary(String label, View.OnClickListener listener, boolean animate) {
        show(primary, label, listener);
        if (animate) {
            pulse.setTarget(primary);
        }
    }

    public void showSecondary(String label, View.OnClickListener listener) {
        show(secondary, label, listener);
    }

    public void showTertiary(String label, View.OnClickListener listener) {
        show(tertiary, label, listener);
    }

    private LinearLayout buildHomeDetails() {
        LinearLayout details = new LinearLayout(activity);
        details.setOrientation(LinearLayout.VERTICAL);
        TextView reassurance = text(
                "Conexión temporal · Segura · Vos tenés el control",
                14,
                COLOR_GRAPHITE,
                Typeface.BOLD);
        reassurance.setBackground(rounded(COLOR_LIME_SOFT, 14));
        reassurance.setGravity(Gravity.CENTER);
        reassurance.setPadding(dp(14), dp(11), dp(14), dp(11));
        add(details, reassurance, 0, 26);
        add(details, text("Así de simple", 21, COLOR_GRAPHITE, Typeface.BOLD), 0, 16);
        addStep(details, "1", "Preparamos tu teléfono");
        addStep(details, "2", "Abrimos la conexión");
        addStep(details, "3", "Ingresás 6 números");
        add(details, divider(), 18, 22);
        add(details, text("Tu privacidad primero", 21, COLOR_GRAPHITE, Typeface.BOLD), 0, 12);
        add(details, text(
                "• La conexión es temporal.\n"
                        + "• Podés cancelarla cuando quieras.\n"
                        + "• No dejamos acceso permanente en tu teléfono.",
                16,
                COLOR_MUTED,
                Typeface.NORMAL), 0, 0);
        return details;
    }

    private void addStep(LinearLayout parent, String number, String label) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = text(number, 16, COLOR_GRAPHITE, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(rounded(COLOR_LIME, 18));
        row.addView(badge, new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView copy = text(label, 17, COLOR_GRAPHITE, Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(14), 0, 0, 0);
        row.addView(copy, params);
        add(parent, row, 0, 14);
    }

    private Button button(boolean primaryStyle) {
        Button button = new Button(activity);
        button.setTextSize(16);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(primaryStyle ? 58 : 48));
        button.setPadding(dp(18), dp(8), dp(18), dp(8));
        button.setTextColor(primaryStyle ? COLOR_GRAPHITE : COLOR_GRAPHITE);
        button.setBackground(primaryStyle ? rounded(COLOR_LIME, 18) : rounded(Color.TRANSPARENT, 18));
        return button;
    }

    private void show(Button button, String label, View.OnClickListener listener) {
        button.setText(label);
        button.setOnClickListener(listener);
        button.setVisibility(View.VISIBLE);
    }

    private void hide(Button button) {
        button.setOnClickListener(null);
        button.setVisibility(View.GONE);
    }

    private TextView text(String value, float size, int color, int style) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private View divider() {
        View view = new View(activity);
        view.setBackgroundColor(COLOR_LINE);
        view.setMinimumHeight(dp(1));
        return view;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private void add(LinearLayout parent, View child, int topDp, int bottomDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(topDp), 0, dp(bottomDp));
        parent.addView(child, params);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
