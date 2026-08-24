package com.glosh.remote.spike;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.glosh.remote.spike.protocol.JoinDescriptor;

public final class MainActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 9001;
    private static final String ACTION_WIRELESS_DEBUGGING_SETTINGS = "android.settings.WIRELESS_DEBUGGING_SETTINGS";

    private static final int COLOR_BACKGROUND = Color.rgb(248, 248, 243);
    private static final int COLOR_GRAPHITE = Color.rgb(25, 27, 24);
    private static final int COLOR_MUTED = Color.rgb(92, 96, 88);
    private static final int COLOR_LIME = Color.rgb(190, 242, 84);
    private static final int COLOR_LIME_SOFT = Color.rgb(234, 250, 202);
    private static final int COLOR_LINE = Color.rgb(218, 221, 211);
    private TextView statusView;
    private EditText joinUriView;
    private LinearLayout manualSection;
    private Button connectButton;
    private Button cancelButton;
    private String pendingJoinUri;
    private boolean sessionStarted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(buildUi());
        consumeJoinIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        consumeJoinIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sessionStarted) {
            showSessionStartedState();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_NOTIFICATIONS) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            String raw = pendingJoinUri;
            if (raw != null) {
                startSupportSession(raw);
            }
        } else {
            setStatus("Necesitamos mostrarte una notificación para que puedas ingresar los 6 números.");
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
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView wordmark = text("glosh", 20, COLOR_GRAPHITE, Typeface.BOLD);
        wordmark.setLetterSpacing(0.04f);
        add(root, wordmark, 0, 42);

        TextView title = text("¡Bienvenido, Glosher!", 38, COLOR_GRAPHITE, Typeface.BOLD);
        title.setLineSpacing(0, 0.94f);
        add(root, title, 0, 8);

        TextView subheadline = text("Te estábamos esperando.", 22, COLOR_GRAPHITE, Typeface.BOLD);
        add(root, subheadline, 0, 18);

        TextView intro = text(
                "En unos minutos vamos a dejar tu teléfono listo para que soporte pueda ayudarte de forma segura.",
                17,
                COLOR_MUTED,
                Typeface.NORMAL);
        intro.setLineSpacing(dp(4), 1f);
        add(root, intro, 0, 22);

        TextView reassurance = text(
                "Conexión temporal · Segura · Vos tenés el control",
                14,
                COLOR_GRAPHITE,
                Typeface.BOLD);
        reassurance.setBackground(rounded(COLOR_LIME_SOFT, 14));
        reassurance.setGravity(Gravity.CENTER);
        reassurance.setPadding(dp(14), dp(11), dp(14), dp(11));
        add(root, reassurance, 0, 18);

        statusView = text("", 16, COLOR_GRAPHITE, Typeface.BOLD);
        statusView.setBackground(rounded(Color.WHITE, 14));
        statusView.setPadding(dp(16), dp(13), dp(16), dp(13));
        statusView.setVisibility(View.GONE);
        add(root, statusView, 0, 14);

        manualSection = buildManualSection();
        manualSection.setVisibility(View.GONE);
        add(root, manualSection, 0, 14);

        connectButton = primaryButton("Empezar");
        connectButton.setOnClickListener(v -> requestConnection());
        add(root, connectButton, 0, 10);

        cancelButton = secondaryButton("Cancelar conexión");
        cancelButton.setOnClickListener(v -> cancelConnection());
        cancelButton.setVisibility(View.GONE);
        add(root, cancelButton, 0, 40);

        add(root, sectionTitle("Así de simple"), 0, 20);
        addStep(root, "1", "Conectamos tu teléfono");
        addStep(root, "2", "Ingresás 6 números");
        addStep(root, "3", "Soporte continúa");

        add(root, divider(), 26, 26);
        add(root, sectionTitle("Tu privacidad primero"), 0, 14);
        add(root, text(
                "• La conexión es temporal.\n"
                        + "• Podés cancelarla cuando quieras.\n"
                        + "• No dejamos acceso permanente en tu teléfono.",
                16,
                COLOR_MUTED,
                Typeface.NORMAL), 0, 0);

        return scroll;
    }

    private LinearLayout buildManualSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(16), dp(16), dp(16), dp(14));
        section.setBackground(rounded(Color.WHITE, 16));

        add(section, text(
                "Pegá el enlace que te envió soporte",
                17,
                COLOR_GRAPHITE,
                Typeface.BOLD), 0, 12);

        Button pasteButton = secondaryButton("Pegar enlace");
        pasteButton.setOnClickListener(v -> pasteSupportLink());
        add(section, pasteButton, 0, 4);

        Button manualButton = textButton("Ingresarlo manualmente");
        manualButton.setOnClickListener(v -> {
            joinUriView.setVisibility(View.VISIBLE);
            joinUriView.requestFocus();
        });
        add(section, manualButton, 0, 8);

        joinUriView = new EditText(this);
        joinUriView.setHint("Enlace de soporte");
        joinUriView.setTextColor(COLOR_GRAPHITE);
        joinUriView.setHintTextColor(COLOR_MUTED);
        joinUriView.setTextSize(14);
        joinUriView.setMinLines(2);
        joinUriView.setSaveEnabled(false);
        joinUriView.setAutofillHints((String[]) null);
        joinUriView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        joinUriView.setPadding(dp(14), dp(12), dp(14), dp(12));
        joinUriView.setBackground(outlined(Color.WHITE, COLOR_LINE, 14));
        joinUriView.setVisibility(View.GONE);
        add(section, joinUriView, 0, 0);

        return section;
    }

    private void requestConnection() {
        String raw = pendingJoinUri;
        if (raw == null) {
            raw = readValidJoinFromClipboard();
        }
        if (raw == null && joinUriView.getVisibility() == View.VISIBLE) {
            raw = validJoinOrNull(joinUriView.getText().toString());
        }

        if (raw == null) {
            showManualEntry("No encontramos el enlace todavía. Podés pegarlo acá.");
            return;
        }

        pendingJoinUri = raw;
        showReadyState();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
            return;
        }
        startSupportSession(raw);
    }

    private void pasteSupportLink() {
        String raw = readValidJoinFromClipboard();
        if (raw == null) {
            showManualEntry("No pudimos reconocer un enlace válido. Copialo de nuevo o ingresalo manualmente.");
            return;
        }
        pendingJoinUri = raw;
        joinUriView.setText("");
        showReadyState();
    }

    private String readValidJoinFromClipboard() {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip()) {
                return null;
            }
            ClipData clip = clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                return null;
            }
            CharSequence value = clip.getItemAt(0).coerceToText(this);
            return value == null ? null : validJoinOrNull(value.toString());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String validJoinOrNull(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String candidate = raw.trim();
        try {
            JoinDescriptor check = JoinDescriptor.parse(candidate);
            check.destroy();
            return candidate;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void startSupportSession(String raw) {
        Intent service = new Intent(this, RemotePairingService.class)
                .setAction(RemotePairingService.ACTION_START)
                .putExtra(RemotePairingService.EXTRA_JOIN_URI, raw);
        startForegroundService(service);

        clearClipboardIfMatches(raw);
        pendingJoinUri = null;
        joinUriView.setText("");
        if (getIntent() != null) {
            getIntent().setData(null);
        }

        sessionStarted = true;
        showSessionStartedState();
        openWirelessDebuggingSettings();
    }

    private void cancelConnection() {
        Intent service = new Intent(this, RemotePairingService.class)
                .setAction(RemotePairingService.ACTION_STOP);
        try {
            startService(service);
        } catch (Throwable ignored) {
            // If the service no longer exists there is nothing left to revoke.
        }
        sessionStarted = false;
        pendingJoinUri = null;
        joinUriView.setText("");
        manualSection.setVisibility(View.GONE);
        connectButton.setText("Empezar");
        connectButton.setVisibility(View.VISIBLE);
        cancelButton.setVisibility(View.GONE);
        setStatus("Conexión cerrada. Podés empezar de nuevo cuando quieras.");
    }

    private void consumeJoinIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        Uri data = intent.getData();
        if (data == null || !"gloshremote".equalsIgnoreCase(data.getScheme())) {
            return;
        }
        String raw = validJoinOrNull(data.toString());
        if (raw != null) {
            pendingJoinUri = raw;
            joinUriView.setText("");
            showReadyState();
        } else {
            pendingJoinUri = null;
            showManualEntry("El enlace no está completo. Pedile a soporte que te lo envíe nuevamente.");
        }
    }

    private void showReadyState() {
        sessionStarted = false;
        manualSection.setVisibility(View.GONE);
        connectButton.setText("Empezar");
        connectButton.setVisibility(View.VISIBLE);
        cancelButton.setVisibility(View.GONE);
        setStatus("Todo listo para empezar");
    }

    private void showManualEntry(String message) {
        manualSection.setVisibility(View.VISIBLE);
        connectButton.setText("Continuar");
        connectButton.setVisibility(View.VISIBLE);
        cancelButton.setVisibility(View.GONE);
        setStatus(message);
    }

    private void showSessionStartedState() {
        manualSection.setVisibility(View.GONE);
        connectButton.setVisibility(View.GONE);
        cancelButton.setVisibility(View.VISIBLE);
        setStatus(
                "Preparando la conexión…\n\n"
                        + "Ahora activá Depuración inalámbrica y elegí “Emparejar dispositivo con código”.");
    }

    private void openWirelessDebuggingSettings() {
        Intent wirelessDebugging = new Intent(ACTION_WIRELESS_DEBUGGING_SETTINGS);
        if (wirelessDebugging.resolveActivity(getPackageManager()) != null) {
            try {
                startActivity(wirelessDebugging);
                return;
            } catch (Throwable ignored) {
                // Continue with the documented fallback.
            }
        }

        Intent developerSettings = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
        if (developerSettings.resolveActivity(getPackageManager()) != null) {
            try {
                startActivity(developerSettings);
                return;
            } catch (Throwable ignored) {
                // Continue with the final settings fallback.
            }
        }
        startActivity(new Intent(Settings.ACTION_SETTINGS));
    }

    private void clearClipboardIfMatches(String secret) {
        if (secret == null || secret.isEmpty()) {
            return;
        }
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip()) {
                return;
            }
            ClipData clip = clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                return;
            }
            CharSequence current = clip.getItemAt(0).coerceToText(this);
            if (current != null && secret.contentEquals(current)) {
                clipboard.clearPrimaryClip();
            }
        } catch (Throwable ignored) {
            // Best effort only.
        }
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
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f);
        copyParams.setMargins(dp(14), 0, 0, 0);
        row.addView(copy, copyParams);
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
        button.setBackground(outlined(Color.TRANSPARENT, COLOR_LINE, 18));
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

    private GradientDrawable outlined(int color, int strokeColor, int radiusDp) {
        GradientDrawable drawable = rounded(color, radiusDp);
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private void add(LinearLayout parent, View child, int topDp, int bottomDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(topDp), 0, dp(bottomDp));
        parent.addView(child, params);
    }

    private void setStatus(String text) {
        statusView.setText(text);
        statusView.setVisibility(View.VISIBLE);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
