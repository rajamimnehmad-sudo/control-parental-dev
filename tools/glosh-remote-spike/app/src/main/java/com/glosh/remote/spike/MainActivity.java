package com.glosh.remote.spike;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.glosh.remote.spike.adb.AdbConnectionManager;
import com.glosh.remote.spike.adb.AdbShell;
import com.glosh.remote.spike.adb.PairingCoordinator;
import com.glosh.remote.spike.relay.RelayClient;

public final class MainActivity extends Activity {
    private PairingCoordinator pairingCoordinator;
    private RelayClient relayClient;

    private TextView statusView;
    private TextView logView;
    private EditText pairingCodeView;
    private EditText joinUriView;
    private Button pairButton;
    private Button relayButton;

    private volatile boolean adbReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pairingCoordinator = new PairingCoordinator(getApplicationContext());
        setContentView(buildUi());
        consumeDeepLink(getIntent());

        appendLog("Dispositivo: " + Build.MANUFACTURER + " " + Build.MODEL
                + " · Android " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            setStatus("Este spike requiere Android 11 o superior.");
            pairButton.setEnabled(false);
        } else {
            setStatus("Listo para emparejar ADB local.");
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        consumeDeepLink(intent);
    }

    @Override
    protected void onDestroy() {
        if (relayClient != null) {
            relayClient.close();
            relayClient = null;
        }
        pairingCoordinator.close();
        super.onDestroy();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Glosh Remote · Spike");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Prueba temporal de conexión remota sin PC del cliente. No instala Glosh ni Device Owner todavía.");
        subtitle.setTextSize(16);
        subtitle.setPadding(0, dp(8), 0, dp(18));
        root.addView(subtitle);

        statusView = new TextView(this);
        statusView.setTextSize(17);
        statusView.setTypeface(Typeface.DEFAULT_BOLD);
        statusView.setPadding(0, 0, 0, dp(18));
        root.addView(statusView);

        TextView step1 = heading("1 · Activar Depuración inalámbrica");
        root.addView(step1);

        TextView help1 = body("Abrí Opciones de desarrollador → Depuración inalámbrica → Emparejar dispositivo con código. Dejá visible esa pantalla, recordá el código de 6 dígitos y volvé acá.");
        root.addView(help1);

        Button settingsButton = new Button(this);
        settingsButton.setText("Abrir Opciones de desarrollador");
        settingsButton.setOnClickListener(v -> openDeveloperSettings());
        root.addView(settingsButton);

        pairingCodeView = new EditText(this);
        pairingCodeView.setHint("Código de 6 dígitos");
        pairingCodeView.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(pairingCodeView);

        pairButton = new Button(this);
        pairButton.setText("Emparejar ADB local");
        pairButton.setOnClickListener(v -> startPairing());
        root.addView(pairButton);

        root.addView(spacer());
        root.addView(heading("2 · Unir esta sesión con tu Mac"));
        root.addView(body("Pegá el enlace gloshremote:// que genera la herramienta de Mac. La clave queda sólo en memoria y los comandos viajan cifrados extremo a extremo."));

        joinUriView = new EditText(this);
        joinUriView.setHint("gloshremote://join?...");
        joinUriView.setMinLines(3);
        joinUriView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        root.addView(joinUriView);

        relayButton = new Button(this);
        relayButton.setText("Conectar con Mac");
        relayButton.setEnabled(false);
        relayButton.setOnClickListener(v -> startRelay());
        root.addView(relayButton);

        Button closeButton = new Button(this);
        closeButton.setText("Cerrar sesión remota");
        closeButton.setOnClickListener(v -> closeRemoteSession());
        root.addView(closeButton);

        Button revokeButton = new Button(this);
        revokeButton.setText("Revocar identidad ADB temporal");
        revokeButton.setOnClickListener(v -> revokeIdentity());
        root.addView(revokeButton);

        root.addView(spacer());
        root.addView(heading("Registro local"));
        logView = new TextView(this);
        logView.setTextSize(13);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        root.addView(logView);

        return scroll;
    }

    private TextView heading(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(19);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(0, dp(10), 0, dp(6));
        return view;
    }

    private TextView body(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(15);
        view.setPadding(0, 0, 0, dp(10));
        return view;
    }

    private View spacer() {
        View view = new View(this);
        view.setMinimumHeight(dp(18));
        return view;
    }

    private void openDeveloperSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void startPairing() {
        String code = pairingCodeView.getText().toString().trim();
        pairButton.setEnabled(false);
        setStatus("Preparando pairing…");

        pairingCoordinator.pairAndConnect(code, new PairingCoordinator.Listener() {
            @Override
            public void onStatus(String status) {
                ui(() -> {
                    setStatus(status);
                    appendLog(status);
                });
            }

            @Override
            public void onConnected(String canaryOutput) {
                ui(() -> {
                    adbReady = true;
                    pairButton.setEnabled(true);
                    relayButton.setEnabled(true);
                    setStatus("ADB local listo. Ya podés unir la sesión con tu Mac.");
                    appendLog("ADB canary: " + canaryOutput.trim());
                    ensureRelayClient();
                });
            }

            @Override
            public void onError(String message, Throwable error) {
                ui(() -> {
                    adbReady = false;
                    pairButton.setEnabled(true);
                    relayButton.setEnabled(false);
                    setStatus("Pairing falló: " + message);
                    appendLog("ERROR pairing: " + message);
                });
            }
        });
    }

    private void startRelay() {
        if (!adbReady || !pairingCoordinator.isConnected()) {
            setStatus("Primero hay que completar el pairing ADB local.");
            return;
        }
        try {
            ensureRelayClient();
            relayClient.connect(joinUriView.getText().toString().trim(), new RelayClient.Listener() {
                @Override
                public void onState(String state) {
                    ui(() -> {
                        setStatus(state);
                        appendLog(state);
                    });
                }

                @Override
                public void onAuthenticated() {
                    ui(() -> appendLog("Mac autenticada. Allowlist remota activa."));
                }

                @Override
                public void onError(String message, Throwable error) {
                    ui(() -> {
                        setStatus(message);
                        appendLog("ERROR relay: " + message);
                    });
                }

                @Override
                public void onClosed() {
                    ui(() -> appendLog("Relay cerrado."));
                }
            });
        } catch (Throwable error) {
            setStatus("Enlace inválido: " + error.getMessage());
            appendLog("ERROR descriptor: " + error.getMessage());
        }
    }

    private void closeRemoteSession() {
        if (relayClient != null) {
            relayClient.disconnect();
        }
        pairingCoordinator.disconnect();
        adbReady = false;
        relayButton.setEnabled(false);
        setStatus("Sesión cerrada. No quedan comandos remotos activos.");
        appendLog("Sesión remota y conexión ADB cerradas.");
    }

    private void revokeIdentity() {
        if (relayClient != null) {
            relayClient.close();
            relayClient = null;
        }
        pairingCoordinator.revokeIdentity();
        adbReady = false;
        relayButton.setEnabled(false);
        setStatus("Identidad ADB temporal revocada. Para otra sesión habrá que emparejar de nuevo.");
        appendLog("Clave/certificado ADB locales eliminados.");
    }

    private void ensureRelayClient() {
        if (relayClient != null) {
            return;
        }
        try {
            AdbShell shell = new AdbShell(AdbConnectionManager.getInstance(getApplicationContext()));
            relayClient = new RelayClient(shell);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo abrir el cliente remoto.", e);
        }
    }

    private void consumeDeepLink(Intent intent) {
        if (intent == null || joinUriView == null) {
            return;
        }
        Uri data = intent.getData();
        if (data != null && "gloshremote".equalsIgnoreCase(data.getScheme())) {
            joinUriView.setText(data.toString());
        }
    }

    private void setStatus(String text) {
        statusView.setText(text);
    }

    private void appendLog(String line) {
        String existing = logView.getText().toString();
        String next = existing.isEmpty() ? line : existing + "\n" + line;
        logView.setText(next);
    }

    private void ui(Runnable runnable) {
        runOnUiThread(runnable);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
