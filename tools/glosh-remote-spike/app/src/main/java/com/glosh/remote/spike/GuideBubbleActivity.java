package com.glosh.remote.spike;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.glosh.remote.spike.broker.SupportSessionCoordinator;
import com.glosh.remote.spike.session.PairingUiState;
import com.glosh.remote.spike.session.SessionState;
import com.glosh.remote.spike.wizard.GuideNotification;
import com.glosh.remote.spike.wizard.OnboardingState;
import com.glosh.remote.spike.wizard.SamsungGuideStep;
import com.glosh.remote.spike.wizard.SamsungGuideStore;

/** Compact content rendered by Android/SystemUI inside the Glosh notification Bubble. */
public final class GuideBubbleActivity extends Activity {
    private static final long REFRESH_MS = 300L;
    private static final int GRAPHITE = Color.rgb(25, 27, 24);
    private static final int MUTED = Color.rgb(92, 96, 88);
    private static final int LIME = Color.rgb(190, 242, 84);
    private static final int SOFT = Color.rgb(239, 241, 234);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            maybeStartSupportSession();
            flushQueuedPairingCode();
            renderIfChanged();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    private SamsungGuideStore guideStore;
    private GuideNotification guideNotification;
    private SupportSessionCoordinator coordinator;
    private FrameLayout root;
    private SamsungGuideStep renderedStep;
    private PairingUiState renderedPairing;
    private SessionState renderedSession;
    private OnboardingState.Step renderedOnboarding;
    private String transientInstruction;
    private String draftCode = "";
    private String queuedPairingCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        guideStore = new SamsungGuideStore(this);
        guideNotification = new GuideNotification(this);
        coordinator = SupportSessionCoordinator.get(this);
        root = new FrameLayout(this);
        root.setPadding(dp(6), dp(6), dp(6), dp(6));
        setContentView(root);
        maybeStartSupportSession();
        render(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(refresh);
        handler.post(refresh);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(refresh);
        draftCode = "";
        queuedPairingCode = null;
        super.onDestroy();
    }

    private void renderIfChanged() {
        SamsungGuideStep step = guideStore.step();
        PairingUiState pairing = RemotePairingService.getPairingUiState();
        SessionState session = RemotePairingService.getSessionState();
        OnboardingState.Step onboarding = coordinator.step();
        if (step != renderedStep
                || pairing != renderedPairing
                || session != renderedSession
                || onboarding != renderedOnboarding) {
            render(false);
        }
    }

    private void render(boolean force) {
        SessionState session = RemotePairingService.getSessionState();
        if (session == SessionState.CONNECTED) {
            guideNotification.clear();
            finishAndRemoveTask();
            return;
        }
        if (!guideStore.active()) {
            finishAndRemoveTask();
            return;
        }

        SamsungGuideStep step = guideStore.step();
        PairingUiState pairing = RemotePairingService.getPairingUiState();
        OnboardingState.Step onboarding = coordinator.step();
        if (!force
                && step == renderedStep
                && pairing == renderedPairing
                && session == renderedSession
                && onboarding == renderedOnboarding) {
            return;
        }
        renderedStep = step;
        renderedPairing = pairing;
        renderedSession = session;
        renderedOnboarding = onboarding;

        root.removeAllViews();
        root.addView(compactPanel(step, pairing), match());
    }

    /**
     * The collapsed Bubble/preview carries the instruction. Expanded content stays intentionally
     * short so Settings remains visible: one line of context, Back/confirm, and ADB code entry.
     */
    private View compactPanel(SamsungGuideStep step, PairingUiState pairing) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(rounded(Color.WHITE, 18));
        card.setElevation(dp(8));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text(step.title(), 15, GRAPHITE, Typeface.BOLD);
        title.setMaxLines(2);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView progress = text(
                step.number() + "/" + SamsungGuideStep.TOTAL_STEPS,
                11,
                GRAPHITE,
                Typeface.BOLD);
        progress.setPadding(dp(8), dp(3), dp(8), dp(3));
        progress.setBackground(rounded(Color.rgb(234, 250, 202), 10));
        header.addView(progress);
        card.addView(header, margins(0, 0, 0, 8));

        String status = compactStatus(pairing);
        if (status != null) {
            card.addView(text(status, 10, MUTED, Typeface.NORMAL), margins(0, 0, 0, 6));
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = action("ATRÁS", false);
        back.setOnClickListener(view -> handleBubbleBack());
        actions.addView(back, new LinearLayout.LayoutParams(0, dp(40), 1f));

        if (step.canAdvanceLocally()) {
            TextView next = action(compactActionLabel(step), true);
            next.setOnClickListener(view -> handleBubbleNext());
            LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(0, dp(40), 1.25f);
            nextParams.setMargins(dp(8), 0, 0, 0);
            actions.addView(next, nextParams);
        }
        card.addView(actions, margins(0, 0, 0, 8));

        card.addView(text("Código ADB", 10, MUTED, Typeface.BOLD), margins(0, 0, 0, 4));

        LinearLayout codeRow = new LinearLayout(this);
        codeRow.setOrientation(LinearLayout.HORIZONTAL);
        codeRow.setGravity(Gravity.CENTER_VERTICAL);

        boolean codeLocked = pairing == PairingUiState.CONNECTING || queuedPairingCode != null;
        EditText code = pairingInput();
        code.setEnabled(!codeLocked);
        codeRow.addView(code, new LinearLayout.LayoutParams(0, dp(40), 1f));

        TextView send = action(
                pairing == PairingUiState.CONNECTING
                        ? "CONECTANDO"
                        : queuedPairingCode != null ? "LISTO" : "ENVIAR",
                true);
        send.setEnabled(!codeLocked);
        send.setOnClickListener(view -> submitPairingCode(code));
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(96), dp(40));
        sendParams.setMargins(dp(8), 0, 0, 0);
        codeRow.addView(send, sendParams);
        card.addView(codeRow);

        return card;
    }

    private String compactStatus(PairingUiState pairing) {
        if (transientInstruction != null && !transientInstruction.isBlank()) {
            return transientInstruction;
        }
        if (pairing == PairingUiState.CONNECTING) {
            return "Código recibido · conectando…";
        }
        if (RemotePairingService.getSessionState() == SessionState.PREPARING) {
            return "Listo para recibir los 6 números.";
        }
        return null;
    }

    private EditText pairingInput() {
        EditText code = new EditText(this);
        code.setSingleLine(true);
        code.setGravity(Gravity.CENTER);
        code.setTextSize(18);
        code.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        code.setTextColor(GRAPHITE);
        code.setHint("000000");
        code.setInputType(InputType.TYPE_CLASS_NUMBER);
        code.setFilters(new InputFilter[] {new InputFilter.LengthFilter(6)});
        code.setBackground(rounded(Color.rgb(247, 248, 243), 12));
        code.setPadding(dp(10), dp(6), dp(10), dp(6));
        if (!draftCode.isEmpty()) {
            code.setText(draftCode);
            code.setSelection(code.length());
        }
        code.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                draftCode = editable == null ? "" : editable.toString();
            }
        });
        return code;
    }

    private void handleBubbleNext() {
        SamsungGuideStep current = guideStore.step();
        if (current == SamsungGuideStep.ENTER_CODE) {
            openGloshFromBubble();
            return;
        }

        if (current == SamsungGuideStep.BUILD_NUMBER) {
            if (coordinator.step() == OnboardingState.Step.GUIDE_PERMISSION) {
                coordinator.guideReady();
            }
            if (coordinator.step() == OnboardingState.Step.DEVELOPER_OPTIONS) {
                coordinator.confirmDeveloperOptions();
            }
            advanceTo(SamsungGuideStep.DEVELOPER_OPTIONS);
            return;
        }

        if (current == SamsungGuideStep.WIRELESS_DEBUGGING) {
            maybeStartSupportSession();
            if (RemotePairingService.getSessionState() == SessionState.IDLE
                    && coordinator.step() == OnboardingState.Step.REQUESTING_SUPPORT) {
                transientInstruction = "Preparando soporte…";
            }
            advanceTo(SamsungGuideStep.PAIR_DEVICE);
            return;
        }

        if (current == SamsungGuideStep.PAIR_DEVICE) {
            advanceTo(SamsungGuideStep.ENTER_CODE);
            return;
        }

        advanceTo(current.next());
    }

    private void handleBubbleBack() {
        SamsungGuideStep current = guideStore.step();
        if (!current.canGoBack()) {
            openGloshFromBubble();
            return;
        }
        advanceTo(current.previous());
    }

    private void advanceTo(SamsungGuideStep step) {
        transientInstruction = null;
        guideStore.setStep(step);
        if (RemotePairingService.getSessionState() == SessionState.IDLE) {
            guideNotification.showBubbleStep(step, false);
        }
        if (step.ordinal() >= SamsungGuideStep.WIRELESS_DEBUGGING.ordinal()) {
            maybeStartSupportSession();
        }
        render(true);
    }

    /** Starts pairing as soon as step 5 is reached, instead of waiting for an extra confirmation. */
    private void maybeStartSupportSession() {
        if (RemotePairingService.getSessionState() != SessionState.IDLE) {
            return;
        }
        SamsungGuideStep step = guideStore.step();
        if (step.ordinal() < SamsungGuideStep.WIRELESS_DEBUGGING.ordinal()
                && queuedPairingCode == null) {
            return;
        }
        if (coordinator.step() != OnboardingState.Step.WIRELESS_DEBUGGING) {
            return;
        }
        String descriptor = coordinator.markSessionStarted();
        if (descriptor == null) {
            return;
        }
        startForegroundService(new Intent(this, RemotePairingService.class)
                .setAction(RemotePairingService.ACTION_START)
                .putExtra(RemotePairingService.EXTRA_JOIN_URI, descriptor));
        transientInstruction = queuedPairingCode == null
                ? "Preparando conexión…"
                : "Código recibido · preparando conexión…";
    }

    /**
     * If the customer typed the PIN before the service reached PREPARING, keep it only in memory
     * for this Bubble Activity and submit automatically on the first safe PREPARING tick.
     */
    private void flushQueuedPairingCode() {
        String code = queuedPairingCode;
        if (code == null || RemotePairingService.getSessionState() != SessionState.PREPARING) {
            return;
        }
        queuedPairingCode = null;
        draftCode = "";
        startService(new Intent(this, RemotePairingService.class)
                .setAction(RemotePairingService.ACTION_SUBMIT_CODE)
                .putExtra(RemotePairingService.EXTRA_PAIRING_CODE, code));
        transientInstruction = "Código recibido · conectando…";
        render(true);
    }

    private void openGloshFromBubble() {
        Intent intent = new Intent(this, MainActivity.class)
                .setAction(MainActivity.ACTION_GUIDE_OPEN)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
        finishAndRemoveTask();
    }

    private void submitPairingCode(EditText input) {
        String code = input.getText() == null ? "" : input.getText().toString().trim();
        if (!code.matches("\\d{6}")) {
            input.setError("Ingresá exactamente 6 números");
            return;
        }

        SessionState session = RemotePairingService.getSessionState();
        if (session == SessionState.CONNECTED) {
            return;
        }
        if (session != SessionState.IDLE && session != SessionState.PREPARING) {
            input.setError("La conexión está ocupada. Esperá un momento.");
            return;
        }

        queuedPairingCode = code;
        draftCode = code;
        if (session == SessionState.IDLE) {
            maybeStartSupportSession();
            transientInstruction = RemotePairingService.getSessionState() == SessionState.IDLE
                    ? "Código guardado · esperando soporte…"
                    : "Código recibido · preparando conexión…";
        }
        flushQueuedPairingCode();
        render(true);
    }

    private TextView action(String label, boolean primary) {
        TextView view = text(label, 12, GRAPHITE, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(40));
        view.setPadding(dp(8), dp(7), dp(8), dp(7));
        view.setBackground(rounded(primary ? LIME : SOFT, 12));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private static String compactActionLabel(SamsungGuideStep step) {
        return switch (step) {
            case ABOUT_PHONE, SOFTWARE_INFO -> "YA LO ABRÍ";
            case BUILD_NUMBER -> "YA ESTÁ ACTIVO";
            case DEVELOPER_OPTIONS -> "YA ESTOY AHÍ";
            case WIRELESS_DEBUGGING -> "YA LA ACTIVÉ";
            case PAIR_DEVICE -> "YA VEO EL CÓDIGO";
            case ENTER_CODE -> "ABRIR GLOSH";
        };
    }

    private TextView text(String value, float size, int color, int style) {
        TextView view = new TextView(this);
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

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
    }

    private LinearLayout.LayoutParams margins(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
