package com.glosh.remote.spike;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.glosh.remote.spike.protocol.JoinDescriptor;

/**
 * Minimal consent/onboarding screen for REMOTE-INSTALL-CONNECTION-00.
 *
 * Normal client flow:
 *   open one-time link -> tap Connect -> Android Developer options ->
 *   Pair device with pairing code -> enter six digits in notification -> done.
 */
public final class MainActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 9001;

    private TextView statusView;
    private EditText joinUriView;
    private Button connectButton;
    private String pendingJoinUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(buildUi());
        consumeJoinIntent(getIntent());

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            setStatus("Este prototipo requiere Android 11 o superior.");
            connectButton.setEnabled(false);
        } else if (pendingJoinUri == null) {
            setStatus("Abrí el enlace temporal que te envió soporte.");
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        consumeJoinIntent(intent);
    }

    @Override
    protected void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
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
            setStatus("Necesitamos notificaciones para que puedas escribir el código sin salir de Ajustes.");
        }
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(22);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Glosh Remote");
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = body(
                "Conexión temporal para que soporte pueda preparar este Android a distancia. "
                        + "No instala Device Owner ni modifica Glosh en esta primera prueba.");
        subtitle.setPadding(0, dp(8), 0, dp(18));
        root.addView(subtitle);

        statusView = new TextView(this);
        statusView.setTextSize(17);
        statusView.setTypeface(Typeface.DEFAULT_BOLD);
        statusView.setPadding(0, 0, 0, dp(18));
        root.addView(statusView);

        root.addView(heading("Conexión rápida"));
        root.addView(body(
                "1. Tocá “Conectar con soporte”.\n"
                        + "2. Activá Depuración inalámbrica.\n"
                        + "3. Tocá “Emparejar dispositivo con código”.\n"
                        + "4. Bajá la notificación de Glosh Remote y escribí los 6 números.\n\n"
                        + "No hace falta volver a esta app: después del código, la conexión con la Mac es automática."));

        joinUriView = new EditText(this);
        joinUriView.setHint("Enlace temporal de soporte (si no se abrió automáticamente)");
        joinUriView.setMinLines(2);
        joinUriView.setSaveEnabled(false);
        joinUriView.setAutofillHints((String[]) null);
        joinUriView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        root.addView(joinUriView);

        connectButton = new Button(this);
        connectButton.setText("Conectar con soporte");
        connectButton.setOnClickListener(v -> requestConnection());
        root.addView(connectButton);

        Button cancelButton = new Button(this);
        cancelButton.setText("Cancelar conexión");
        cancelButton.setOnClickListener(v -> cancelConnection());
        root.addView(cancelButton);

        root.addView(spacer());
        root.addView(heading("Seguridad"));
        root.addView(body(
                "La sesión es temporal. ADB queda sólo dentro del teléfono; no se publica el puerto 5555. "
                        + "La identidad ADB vive únicamente en memoria y desaparece al cerrar la sesión o el proceso. "
                        + "Desde la Mac, esta V0 sólo acepta un conjunto pequeño de diagnósticos permitidos."));

        TextView device = body(
                "Este teléfono: " + Build.MANUFACTURER + " " + Build.MODEL
                        + " · Android " + Build.VERSION.RELEASE + " · SDK " + Build.VERSION.SDK_INT);
        device.setTextIsSelectable(true);
        root.addView(device);

        return scroll;
    }

    private void requestConnection() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            setStatus("Android 11 o superior es obligatorio para este método.");
            return;
        }

        String raw = pendingJoinUri;
        if (raw == null || raw.trim().isEmpty()) {
            raw = joinUriView.getText().toString().trim();
        }

        try {
            JoinDescriptor check = JoinDescriptor.parse(raw);
            check.destroy();
        } catch (Throwable error) {
            setStatus("El enlace de soporte no es válido.");
            return;
        }

        pendingJoinUri = raw;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
            return;
        }
        startSupportSession(raw);
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

        setStatus("Ahora activá Depuración inalámbrica y tocá “Emparejar dispositivo con código”. Después mirá la notificación de Glosh Remote.");
        openDeveloperSettings();
    }

    private void cancelConnection() {
        Intent service = new Intent(this, RemotePairingService.class)
                .setAction(RemotePairingService.ACTION_STOP);
        try {
            startService(service);
        } catch (Throwable ignored) {
            // If the service no longer exists there is nothing left to revoke.
        }
        pendingJoinUri = null;
        joinUriView.setText("");
        setStatus("Conexión cancelada.");
    }

    private void consumeJoinIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        Uri data = intent.getData();
        if (data == null || !"gloshremote".equalsIgnoreCase(data.getScheme())) {
            return;
        }
        try {
            JoinDescriptor check = JoinDescriptor.parse(data.toString());
            check.destroy();
            pendingJoinUri = data.toString();
            if (joinUriView != null) {
                joinUriView.setText("");
            }
            if (statusView != null) {
                setStatus("Enlace de soporte cargado. Tocá “Conectar con soporte”.");
            }
        } catch (Throwable error) {
            pendingJoinUri = null;
            if (statusView != null) {
                setStatus("El enlace recibido no es válido.");
            }
        }
    }

    private void openDeveloperSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        } catch (Throwable error) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    clipboard.clearPrimaryClip();
                } else {
                    clipboard.setPrimaryClip(ClipData.newPlainText("", ""));
                }
            }
        } catch (Throwable ignored) {
            // Best effort only.
        }
    }

    private TextView heading(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(19);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(0, dp(10), 0, dp(7));
        return view;
    }

    private TextView body(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(15);
        view.setPadding(0, 0, 0, dp(12));
        return view;
    }

    private View spacer() {
        View view = new View(this);
        view.setMinimumHeight(dp(16));
        return view;
    }

    private void setStatus(String text) {
        statusView.setText(text);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
