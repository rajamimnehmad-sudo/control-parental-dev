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
import android.text.InputFilter;
import android.text.InputType;
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
import com.glosh.remote.spike.wizard.GuideOverlayView;
import com.glosh.remote.spike.wizard.OnboardingState;
import com.glosh.remote.spike.wizard.SamsungGuideStep;
import com.glosh.remote.spike.wizard.SamsungGuideStore;

/** Expanded content rendered by Android/SystemUI inside the Glosh notification Bubble. */
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        guideStore = new SamsungGuideStore(this);
        guideNotification = new GuideNotification(this);
        coordinator = SupportSessionCoordinator.get(this);
        root = new FrameLayout(this);
        root.setPadding(dp(8), dp(8), dp(8), dp(8));
        setContentView(root);
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
            transientInstruction = null;
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
        if (step == SamsungGuideStep.ENTER_CODE) {
            root.addView(codeCard(pairing), match());
            return;
        }

        LinearLayout stack = new LinearLayout(this);
        stack.setOrientation(LinearLayout.VERTICAL);

        GuideOverlayView guide = new GuideOverlayView(this, new GuideOverlayView.Listener() {
            @Override
            public void onBack() {
                handleBubbleBack();
            }

            @Override
            public void onNext() {
                handleBubbleNext();
            }

            @Override
            public void onDragBy(int deltaX, int deltaY) {
                // SystemUI owns Bubble positioning. No app-owned overlay coordinates are used.
            }
        });
        guide.setStep(step, transientInstruction);
        stack.addView(guide, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        stack.addView(compactCodeTray(pairing), margins(0, 8, 0, 0));
        root.addView(stack, match());
    }

    /** Always-present compact ADB code entry inside the expanded Bubble. */
    private View compactCodeTray(PairingUiState pairing) {
        LinearLayout tray = new LinearLayout(this);
        tray.setOrientation(LinearLayout.VERTICAL);
        tray.setPadding(dp(12), dp(10), dp(12), dp(10));
        tray.setBackground(rounded(Color.WHITE, 16));
        tray.setElevation(dp(6));

        tray.addView(
                text("Código ADB · 6 dígitos", 11, GRAPHITE, Typeface.BOLD),
                margins(0, 0, 0, 6));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        EditText code = pairingInput(18);
        code.setEnabled(pairing != PairingUiState.CONNECTING);
        row.addView(code, new LinearLayout.LayoutParams(0, dp(42), 1f));

        TextView send = action(pairing == PairingUiState.CONNECTING ? "LISTO" : "ENVIAR", true);
        send.setEnabled(pairing != PairingUiState.CONNECTING);
        send.setOnClickListener(view -> submitPairingCode(code));
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(94), dp(42));
        sendParams.setMargins(dp(8), 0, 0, 0);
        row.addView(send, sendParams);
        tray.addView(row, margins(0, 0, 0, 0));

        tray.addView(
                text(codeTrayHint(pairing), 10, MUTED, Typeface.NORMAL),
                margins(0, 6, 0, 0));
        return tray;
    }

    private String codeTrayHint(PairingUiState pairing) {
        if (pairing == PairingUiState.CONNECTING) {
            return "Código recibido. Glosh está completando la conexión.";
        }
        if (RemotePairingService.getSessionState() == SessionState.PREPARING) {
            return "Cuando Samsung muestre el código, podés escribirlo acá desde cualquier paso.";
        }
        return "Este campo queda disponible durante toda la guía.";
    }

    private View codeCard(PairingUiState pairing) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(rounded(Color.WHITE, 20));
        card.setElevation(dp(10));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView wordmark = text("glosh", 17, GRAPHITE, Typeface.BOLD);
        header.addView(wordmark, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView progress = text("Paso 7/7", 12, GRAPHITE, Typeface.BOLD);
        progress.setPadding(dp(9), dp(4), dp(9), dp(4));
        progress.setBackground(rounded(Color.rgb(234, 250, 202), 12));
        header.addView(progress);
        card.addView(header, margins(0, 0, 0, 12));

        card.addView(text("Ingresá los 6 números", 20, GRAPHITE, Typeface.BOLD), margins(0, 0, 0, 6));
        String helper = pairing == PairingUiState.CONNECTING
                ? "Código recibido. Glosh está completando la conexión segura…"
                : "Dejá visible el código de Samsung detrás. Podés escribir los seis números acá; si el endpoint todavía no apareció, Glosh los guarda y continúa solo cuando esté listo.";
        card.addView(text(helper, 13, MUTED, Typeface.NORMAL), margins(0, 0, 0, 12));

        EditText code = pairingInput(25);
        code.setEnabled(pairing != PairingUiState.CONNECTING);
        card.addView(code, margins(0, 0, 0, 10));

        TextView connect = action(pairing == PairingUiState.CONNECTING ? "CONECTANDO…" : "CONECTAR", true);
        connect.setEnabled(pairing != PairingUiState.CONNECTING);
        connect.setOnClickListener(view -> submitPairingCode(code));
        card.addView(connect, margins(0, 0, 0, 8));

        TextView back = action("ATRÁS", false);
        back.setOnClickListener(view -> handleBubbleBack());
        card.addView(back, margins(0, 0, 0, 0));
        return card;
    }

    private EditText pairingInput(float size) {
        EditText code = new EditText(this);
        code.setSingleLine(true);
        code.setGravity(Gravity.CENTER);
        code.setTextSize(size);
        code.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        code.setTextColor(GRAPHITE);
        code.setHint("000000");
        code.setInputType(InputType.TYPE_CLASS_NUMBER);
        code.setFilters(new InputFilter[] {new InputFilter.LengthFilter(6)});
        code.setBackground(rounded(Color.rgb(247, 248, 243), 14));
        code.setPadding(dp(12), dp(8), dp(12), dp(8));
        return code;
    }

    /**
     * Bubble controls must remain functional even if Samsung stops/destroys MainActivity while
     * Settings is foreground. Progress therefore lives here instead of relying on an Activity-
     * scoped broadcast receiver.
     */
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
            if (RemotePairingService.getSessionState() == SessionState.IDLE) {
                if (coordinator.step() == OnboardingState.Step.REQUESTING_SUPPORT) {
                    showWaiting(current, "Esperá un momento: estamos preparando la sesión segura con soporte.");
                    return;
                }
                if (!startSupportSession()) {
                    showWaiting(current, "Todavía estamos preparando soporte. Probá nuevamente en unos segundos.");
                    return;
                }
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
        render(true);
    }

    private void showWaiting(SamsungGuideStep step, String message) {
        transientInstruction = message;
        guideNotification.showWaiting(step, message);
        render(true);
    }

    private boolean startSupportSession() {
        if (RemotePairingService.getSessionState() != SessionState.IDLE) {
            return true;
        }
        String descriptor = coordinator.markSessionStarted();
        if (descriptor == null) {
            return false;
        }
        startForegroundService(new Intent(this, RemotePairingService.class)
                .setAction(RemotePairingService.ACTION_START)
                .putExtra(RemotePairingService.EXTRA_JOIN_URI, descriptor));
        return true;
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
        if (RemotePairingService.getSessionState() != SessionState.PREPARING) {
            input.setError("Cuando Samsung muestre el código, Glosh lo acepta desde este mismo campo");
            return;
        }
        startService(new Intent(this, RemotePairingService.class)
                .setAction(RemotePairingService.ACTION_SUBMIT_CODE)
                .putExtra(RemotePairingService.EXTRA_PAIRING_CODE, code));
        input.setEnabled(false);
    }

    private TextView action(String label, boolean primary) {
        TextView view = text(label, 13, GRAPHITE, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(44));
        view.setPadding(dp(12), dp(9), dp(12), dp(9));
        view.setBackground(rounded(primary ? LIME : SOFT, 14));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
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
