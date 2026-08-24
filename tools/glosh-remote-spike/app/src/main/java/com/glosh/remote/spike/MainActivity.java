package com.glosh.remote.spike;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.glosh.remote.spike.broker.SupportSessionCoordinator;
import com.glosh.remote.spike.protocol.JoinDescriptor;
import com.glosh.remote.spike.session.SessionState;
import com.glosh.remote.spike.wizard.OemGuide;
import com.glosh.remote.spike.wizard.OnboardingState;
import com.glosh.remote.spike.wizard.SettingsNavigator;

public final class MainActivity extends Activity implements SupportSessionCoordinator.Listener {
    private static final int REQUEST_NOTIFICATIONS = 9001;
    private static final long STATE_REFRESH_MS = 500;

    private static final int COLOR_BACKGROUND = Color.rgb(248, 248, 243);
    private static final int COLOR_GRAPHITE = Color.rgb(25, 27, 24);
    private static final int COLOR_MUTED = Color.rgb(92, 96, 88);
    private static final int COLOR_LIME = Color.rgb(190, 242, 84);
    private static final int COLOR_LIME_SOFT = Color.rgb(234, 250, 202);
    private static final int COLOR_LINE = Color.rgb(218, 221, 211);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SettingsNavigator settingsNavigator = new SettingsNavigator();
    private final Runnable refreshState = new Runnable() {
        @Override
        public void run() {
            render();
            handler.postDelayed(this, STATE_REFRESH_MS);
        }
    };

    private SupportSessionCoordinator coordinator;
    private TextView progressView;
    private TextView titleView;
    private TextView bodyView;
    private TextView informationView;
    private Button primaryButton;
    private Button secondaryButton;
    private Button tertiaryButton;
    private LinearLayout homeDetails;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        coordinator = SupportSessionCoordinator.get(this);
        setContentView(buildUi());
        consumeDebugIntent(getIntent());
        render();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (RemotePairingService.getSessionState() == SessionState.IDLE) {
            consumeDebugIntent(intent);
        }
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        coordinator.attach(this);
        handler.removeCallbacks(refreshState);
        handler.post(refreshState);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refreshState);
        coordinator.detach(this);
        super.onPause();
    }

    @Override
    public void onStateChanged() {
        render();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_NOTIFICATIONS) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startSupportSession();
        } else {
            informationView.setText(
                    "Necesitamos mostrarte una notificación para que puedas ingresar los 6 números.");
        }
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BACKGROUND);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(26), dp(28), dp(26), dp(38));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView wordmark = text("glosh", 20, COLOR_GRAPHITE, Typeface.BOLD);
        wordmark.setLetterSpacing(0.04f);
        add(root, wordmark, 0, 34);

        progressView = text("", 14, COLOR_MUTED, Typeface.BOLD);
        progressView.setVisibility(View.GONE);
        add(root, progressView, 0, 12);

        titleView = text("", 38, COLOR_GRAPHITE, Typeface.BOLD);
        titleView.setLineSpacing(0, 0.94f);
        add(root, titleView, 0, 12);

        bodyView = text("", 18, COLOR_MUTED, Typeface.NORMAL);
        bodyView.setLineSpacing(dp(4), 1f);
        add(root, bodyView, 0, 20);

        informationView = text("", 16, COLOR_GRAPHITE, Typeface.NORMAL);
        informationView.setLineSpacing(dp(5), 1f);
        informationView.setPadding(dp(16), dp(15), dp(16), dp(15));
        informationView.setBackground(rounded(Color.WHITE, 16));
        add(root, informationView, 0, 18);

        primaryButton = primaryButton("");
        add(root, primaryButton, 0, 10);

        secondaryButton = secondaryButton("");
        add(root, secondaryButton, 0, 6);

        tertiaryButton = textButton("");
        add(root, tertiaryButton, 0, 28);

        homeDetails = new LinearLayout(this);
        homeDetails.setOrientation(LinearLayout.VERTICAL);
        TextView reassurance = text(
                "Conexión temporal · Segura · Vos tenés el control",
                14,
                COLOR_GRAPHITE,
                Typeface.BOLD);
        reassurance.setBackground(rounded(COLOR_LIME_SOFT, 14));
        reassurance.setGravity(Gravity.CENTER);
        reassurance.setPadding(dp(14), dp(11), dp(14), dp(11));
        add(homeDetails, reassurance, 0, 28);
        add(homeDetails, sectionTitle("Así de simple"), 0, 18);
        addStep(homeDetails, "1", "Conectamos tu teléfono");
        addStep(homeDetails, "2", "Ingresás 6 números");
        addStep(homeDetails, "3", "Soporte continúa");
        add(homeDetails, divider(), 20, 24);
        add(homeDetails, sectionTitle("Tu privacidad primero"), 0, 14);
        add(homeDetails, text(
                "• La conexión es temporal.\n"
                        + "• Podés cancelarla cuando quieras.\n"
                        + "• No dejamos acceso permanente en tu teléfono.",
                16,
                COLOR_MUTED,
                Typeface.NORMAL), 0, 0);
        add(root, homeDetails, 0, 0);
        return scroll;
    }

    private void render() {
        SessionState session = RemotePairingService.getSessionState();
        if (session == SessionState.CONNECTED) {
            renderConnected();
            return;
        }
        if (session == SessionState.PREPARING) {
            renderPreparing();
            return;
        }
        if (coordinator.step() == OnboardingState.Step.SESSION_ACTIVE) {
            coordinator.reset();
        }

        switch (coordinator.step()) {
            case REQUESTING_SUPPORT -> renderRequesting();
            case DEVELOPER_OPTIONS -> renderDeveloperOptions();
            case WIRELESS_DEBUGGING -> renderWirelessDebugging();
            case UNAVAILABLE -> renderUnavailable();
            default -> renderHome();
        }
    }

    private void renderHome() {
        progressView.setVisibility(View.GONE);
        titleView.setText("¡Bienvenido, Glosher!");
        bodyView.setText(
                "Te estábamos esperando.\n\n"
                        + "En unos minutos vamos a dejar tu teléfono listo para que soporte pueda ayudarte de forma segura.");
        informationView.setVisibility(View.GONE);
        homeDetails.setVisibility(View.VISIBLE);
        showButton(primaryButton, "CONECTAR CON SOPORTE", v -> coordinator.requestSupport());
        hideButton(secondaryButton);
        hideButton(tertiaryButton);
    }

    private void renderRequesting() {
        renderWizardBase(
                "Conectando con soporte",
                "Estamos preparando tu sesión",
                "Esto puede tardar unos segundos. No cierres Glosh Remote.");
        informationView.setText("Un operador debe aceptar tu solicitud antes de continuar.");
        hideButton(primaryButton);
        showButton(secondaryButton, "CANCELAR", v -> coordinator.reset());
        hideButton(tertiaryButton);
    }

    private void renderDeveloperOptions() {
        OemGuide guide = OemGuide.forDevice(Build.MANUFACTURER, Build.MODEL);
        boolean enabled = Settings.Global.getInt(
                getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0) == 1;
        renderWizardBase(
                "Paso 1 de 3",
                guide.title(),
                enabled
                        ? "Las Opciones de desarrollador ya están activadas. Podemos continuar."
                        : "Primero habilitemos una opción de Android que permite esta conexión temporal.");
        informationView.setText(guide.instructions());
        showButton(primaryButton, "ABRIR ACERCA DEL TELÉFONO", v -> settingsNavigator.openAboutPhone(this));
        showButton(secondaryButton, "SÍ, CONTINUAR", v -> coordinator.continueToWirelessDebugging());
        showButton(tertiaryButton, "NO LA ENCUENTRO", v -> settingsNavigator.openAboutPhone(this));
    }

    private void renderWirelessDebugging() {
        renderWizardBase(
                "Paso 2 de 3",
                "Activemos la conexión segura",
                "Ahora vamos a abrir la pantalla donde Android permite conectar temporalmente el soporte.");
        informationView.setText(
                "En la próxima pantalla:\n\n"
                        + "1. Activá Depuración inalámbrica.\n"
                        + "2. Tocá Emparejar dispositivo con código.\n"
                        + "3. Android va a mostrarte 6 números.");
        showButton(primaryButton, "ABRIR DEPURACIÓN INALÁMBRICA", v -> prepareAndOpenWirelessDebugging());
        showButton(secondaryButton, "CANCELAR CONEXIÓN", v -> cancelConnection());
        hideButton(tertiaryButton);
    }

    private void renderPreparing() {
        renderWizardBase(
                "Paso 3 de 3",
                "Perfecto, conectando…",
                "Ingresá en la notificación de Glosh los 6 números que muestra Android.");
        informationView.setText("Después seguimos automáticamente. Esto tarda sólo unos segundos.");
        hideButton(primaryButton);
        showButton(secondaryButton, "CANCELAR CONEXIÓN", v -> cancelConnection());
        hideButton(tertiaryButton);
    }

    private void renderConnected() {
        renderWizardBase(
                "Paso 3 de 3 · Completado",
                "¡Listo, Glosher!",
                "Soporte ya está conectado de forma segura.");
        informationView.setText("La conexión es temporal y podés terminarla cuando quieras.");
        hideButton(primaryButton);
        showButton(secondaryButton, "CANCELAR CONEXIÓN", v -> cancelConnection());
        hideButton(tertiaryButton);
    }

    private void renderUnavailable() {
        renderWizardBase(
                "Conexión no disponible",
                "Soporte remoto no está disponible en este momento.",
                "Intentá nuevamente más tarde.");
        informationView.setText("No necesitás ingresar ningún dato técnico.");
        showButton(primaryButton, "VOLVER", v -> coordinator.reset());
        hideButton(secondaryButton);
        hideButton(tertiaryButton);
    }

    private void renderWizardBase(String progress, String title, String body) {
        progressView.setText(progress);
        progressView.setVisibility(View.VISIBLE);
        titleView.setText(title);
        bodyView.setText(body);
        informationView.setVisibility(View.VISIBLE);
        homeDetails.setVisibility(View.GONE);
    }

    private void prepareAndOpenWirelessDebugging() {
        if (coordinator.descriptor() == null) {
            coordinator.reset();
            renderUnavailable();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
            return;
        }
        startSupportSession();
    }

    private void startSupportSession() {
        if (RemotePairingService.getSessionState() != SessionState.IDLE) {
            render();
            return;
        }
        String descriptor = coordinator.markSessionStarted();
        if (descriptor == null) {
            coordinator.reset();
            render();
            return;
        }
        Intent service = new Intent(this, RemotePairingService.class)
                .setAction(RemotePairingService.ACTION_START)
                .putExtra(RemotePairingService.EXTRA_JOIN_URI, descriptor);
        startForegroundService(service);
        if (getIntent() != null) {
            getIntent().setData(null);
        }
        render();
        settingsNavigator.openWirelessDebugging(this);
    }

    private void cancelConnection() {
        coordinator.reset();
        if (RemotePairingService.getSessionState() != SessionState.IDLE) {
            try {
                startService(new Intent(this, RemotePairingService.class)
                        .setAction(RemotePairingService.ACTION_STOP));
            } catch (Throwable ignored) {
                // The session may already have closed itself.
            }
        }
        handler.postDelayed(this::render, 250);
    }

    private void consumeDebugIntent(Intent intent) {
        if (!BuildConfig.DEBUG || intent == null) {
            return;
        }
        Uri data = intent.getData();
        if (data == null || !"gloshremote".equalsIgnoreCase(data.getScheme())) {
            return;
        }
        try {
            String raw = data.toString();
            JoinDescriptor descriptor = JoinDescriptor.parse(raw);
            descriptor.destroy();
            coordinator.seedDebugDescriptor(raw);
        } catch (Throwable ignored) {
            // Direct descriptor intents are a hidden DEV fallback and fail closed.
        } finally {
            intent.setData(null);
        }
    }

    private void showButton(Button button, String label, View.OnClickListener listener) {
        button.setText(label);
        button.setOnClickListener(listener);
        button.setVisibility(View.VISIBLE);
    }

    private void hideButton(Button button) {
        button.setOnClickListener(null);
        button.setVisibility(View.GONE);
    }

    private void addStep(LinearLayout root, String number, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = text(number, 16, COLOR_GRAPHITE, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(rounded(COLOR_LIME, 18));
        row.addView(badge, new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView copy = text(label, 17, COLOR_GRAPHITE, Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f);
        params.setMargins(dp(14), 0, 0, 0);
        row.addView(copy, params);
        add(root, row, 0, 16);
    }

    private TextView sectionTitle(String value) {
        return text(value, 21, COLOR_GRAPHITE, Typeface.BOLD);
    }

    private TextView text(String value, float size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private Button primaryButton(String label) {
        Button button = baseButton(label);
        button.setTextColor(COLOR_GRAPHITE);
        button.setBackground(rounded(COLOR_LIME, 18));
        return button;
    }

    private Button secondaryButton(String label) {
        Button button = baseButton(label);
        button.setTextColor(COLOR_GRAPHITE);
        GradientDrawable background = rounded(Color.TRANSPARENT, 18);
        background.setStroke(dp(1), COLOR_LINE);
        button.setBackground(background);
        return button;
    }

    private Button textButton(String label) {
        Button button = baseButton(label);
        button.setTextColor(COLOR_MUTED);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setMinHeight(dp(44));
        return button;
    }

    private Button baseButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(17);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(58));
        button.setPadding(dp(18), dp(10), dp(18), dp(10));
        return button;
    }

    private View divider() {
        View view = new View(this);
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
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
