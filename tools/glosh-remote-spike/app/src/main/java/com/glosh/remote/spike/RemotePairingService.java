package com.glosh.remote.spike;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;

import com.glosh.remote.spike.adb.AdbConnectionManager;
import com.glosh.remote.spike.adb.AdbShell;
import com.glosh.remote.spike.protocol.JoinDescriptor;
import com.glosh.remote.spike.relay.RelayClient;

import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.muntashirakon.adb.android.AdbMdns;

/**
 * Foreground bootstrap service for the first remote-install spike.
 *
 * The user stays in Android Settings while this service discovers the local
 * pairing endpoint and accepts the 6-digit code through notification RemoteInput.
 * After pairing, the service connects to the technician relay automatically.
 */
public final class RemotePairingService extends Service {
    public static final String ACTION_START = "com.glosh.remote.spike.START";
    public static final String ACTION_REPLY = "com.glosh.remote.spike.REPLY";
    public static final String ACTION_STOP = "com.glosh.remote.spike.STOP";
    public static final String EXTRA_JOIN_URI = "join_uri";

    private static final String EXTRA_HOST = "pair_host";
    private static final String EXTRA_PORT = "pair_port";
    private static final String REMOTE_INPUT_CODE = "pair_code";
    private static final String CHANNEL_ID = "glosh_remote_pairing";
    private static final int NOTIFICATION_ID = 7401;
    private static final int FINAL_NOTIFICATION_ID = 7402;
    private static final int REQUEST_REPLY = 7411;
    private static final int REQUEST_STOP = 7412;
    private static final long CONNECT_TIMEOUT_MS = 15_000;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean pairingInProgress = new AtomicBoolean(false);
    private final AtomicBoolean ending = new AtomicBoolean(false);

    private NotificationManager notifications;
    private AdbMdns pairingDiscovery;
    private WifiManager.MulticastLock multicastLock;
    private RelayClient relayClient;
    private String joinUri;

    @Override
    public void onCreate() {
        super.onCreate();
        notifications = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Conexión de soporte Glosh",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Emparejamiento temporal para soporte remoto autorizado.");
        channel.setSound(null, null);
        channel.enableVibration(false);
        notifications.createNotificationChannel(channel);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(
                NOTIFICATION_ID,
                statusNotification("Preparando conexión segura…", "No cierres esta notificación."));

        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            finishSession("Sesión cancelada", "El acceso temporal fue revocado.");
            return START_NOT_STICKY;
        }

        if (ACTION_REPLY.equals(action)) {
            handlePairingReply(intent);
            return START_NOT_STICKY;
        }

        if (ACTION_START.equals(action)) {
            String rawJoin = intent.getStringExtra(EXTRA_JOIN_URI);
            intent.removeExtra(EXTRA_JOIN_URI);
            begin(rawJoin);
            return START_NOT_STICKY;
        }

        finishWithError("Solicitud de conexión inválida.");
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        cleanupRuntime();
        executor.shutdownNow();
        super.onDestroy();
    }

    private void begin(String rawJoin) {
        cleanupRuntime();
        ending.set(false);
        pairingInProgress.set(false);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            finishWithError("Glosh Remote V0 requiere Android 11 o superior.");
            return;
        }

        try {
            JoinDescriptor check = JoinDescriptor.parse(rawJoin);
            check.destroy();
            joinUri = rawJoin;
            AdbConnectionManager.getInstance(getApplicationContext());
        } catch (Throwable error) {
            finishWithError("El enlace de soporte no es válido.");
            return;
        }

        updateForeground(
                "Abrí Depuración inalámbrica",
                "Tocá “Emparejar dispositivo con código”. Glosh detectará el puerto solo.");
        startPairingDiscovery();
    }

    private void startPairingDiscovery() {
        stopPairingDiscovery();
        acquireMulticastLock();

        pairingDiscovery = new AdbMdns(
                this,
                AdbMdns.SERVICE_TYPE_TLS_PAIRING,
                (InetAddress address, int port) -> {
                    if (address != null && port > 0 && !ending.get()) {
                        showPairingCodeNotification(address.getHostAddress(), port);
                    }
                });
        pairingDiscovery.start();
    }

    private void showPairingCodeNotification(String host, int port) {
        Intent replyIntent = new Intent(this, RemotePairingService.class)
                .setAction(ACTION_REPLY)
                .putExtra(EXTRA_HOST, host)
                .putExtra(EXTRA_PORT, port);

        int mutableFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mutableFlags |= PendingIntent.FLAG_MUTABLE;
        }

        PendingIntent replyPendingIntent = PendingIntent.getService(
                this,
                REQUEST_REPLY,
                replyIntent,
                mutableFlags);

        RemoteInput remoteInput = new RemoteInput.Builder(REMOTE_INPUT_CODE)
                .setLabel("Código de 6 dígitos")
                .build();

        Notification.Action replyAction = new Notification.Action.Builder(
                android.R.drawable.ic_menu_send,
                "Ingresar código",
                replyPendingIntent)
                .addRemoteInput(remoteInput)
                .build();

        Notification notification = baseNotification()
                .setContentTitle("Código detectado")
                .setContentText("Escribí acá los 6 números que muestra Android.")
                .setOngoing(true)
                .addAction(replyAction)
                .addAction(stopAction())
                .build();

        notifications.notify(NOTIFICATION_ID, notification);
    }

    private void handlePairingReply(Intent intent) {
        Bundle results = RemoteInput.getResultsFromIntent(intent);
        CharSequence value = results == null ? null : results.getCharSequence(REMOTE_INPUT_CODE);
        String code = value == null ? "" : value.toString().trim();
        String host = intent.getStringExtra(EXTRA_HOST);
        int port = intent.getIntExtra(EXTRA_PORT, -1);

        if (!code.matches("\\d{6}") || host == null || port <= 0 || joinUri == null) {
            finishWithError("El código o la sesión expiraron. Volvé a iniciar la conexión.");
            return;
        }
        if (!pairingInProgress.compareAndSet(false, true)) {
            return;
        }

        updateForeground("Emparejando…", "Esto suele tardar sólo unos segundos.");
        executor.execute(() -> pairAndConnect(host, port, code));
    }

    private void pairAndConnect(String host, int port, String code) {
        try {
            AdbConnectionManager manager = AdbConnectionManager.getInstance(getApplicationContext());
            boolean paired = manager.pair(host, port, code);
            if (!paired) {
                throw new IllegalStateException("Android rechazó el pairing.");
            }

            stopPairingDiscovery();
            updateForeground("Pairing correcto", "Conectando al ADB local seguro…");

            boolean connected = manager.connectTls(this, CONNECT_TIMEOUT_MS);
            if (!connected && !manager.isConnected()) {
                throw new IllegalStateException("No se pudo abrir el canal TLS local de ADB.");
            }

            AdbShell shell = new AdbShell(manager);
            String canary = shell.execute("whoami").trim();
            if (canary.length() == 0) {
                throw new IllegalStateException("ADB respondió sin identidad shell.");
            }

            updateForeground("ADB local listo", "Conectando automáticamente con tu Mac…");
            RelayClient client = new RelayClient(shell);
            relayClient = client;
            client.connect(joinUri, new RelayClient.Listener() {
                @Override
                public void onState(String state) {
                    if (!ending.get()) {
                        updateForeground("Conectando con soporte…", state);
                    }
                }

                @Override
                public void onAuthenticated() {
                    if (!ending.get()) {
                        updateForeground(
                                "Conectado con soporte",
                                "Listo. Tu Mac ya puede ejecutar sólo las acciones autorizadas.");
                    }
                }

                @Override
                public void onError(String message, Throwable error) {
                    if (!ending.get()) {
                        finishWithError(message);
                    }
                }

                @Override
                public void onClosed() {
                    if (!ending.get()) {
                        finishSession("Sesión finalizada", "Soporte cerró el acceso temporal.");
                    }
                }
            });
        } catch (Throwable error) {
            if (!ending.get()) {
                String detail = error.getMessage();
                finishWithError(detail == null ? "No se pudo completar el pairing." : detail);
            }
        } finally {
            pairingInProgress.set(false);
        }
    }

    private void finishWithError(String message) {
        finishSession("No se pudo conectar", message);
    }

    private void finishSession(String title, String message) {
        if (!ending.compareAndSet(false, true)) {
            return;
        }
        cleanupRuntime();
        stopForeground(STOP_FOREGROUND_REMOVE);

        Notification finished = baseNotification()
                .setContentTitle(title)
                .setContentText(message)
                .setOngoing(false)
                .setAutoCancel(true)
                .build();
        notifications.notify(FINAL_NOTIFICATION_ID, finished);
        stopSelf();
    }

    private void cleanupRuntime() {
        stopPairingDiscovery();

        RelayClient currentRelay = relayClient;
        relayClient = null;
        if (currentRelay != null) {
            try {
                currentRelay.close();
            } catch (Throwable ignored) {
            }
        }

        AdbConnectionManager.resetIdentity();
        joinUri = null;
    }

    private void stopPairingDiscovery() {
        AdbMdns current = pairingDiscovery;
        pairingDiscovery = null;
        if (current != null) {
            try {
                current.stop();
            } catch (Throwable ignored) {
            }
        }
        releaseMulticastLock();
    }

    private void acquireMulticastLock() {
        if (multicastLock != null && multicastLock.isHeld()) {
            return;
        }
        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifi != null) {
            multicastLock = wifi.createMulticastLock("glosh-remote-pairing");
            multicastLock.setReferenceCounted(false);
            multicastLock.acquire();
        }
    }

    private void releaseMulticastLock() {
        if (multicastLock != null) {
            try {
                if (multicastLock.isHeld()) {
                    multicastLock.release();
                }
            } catch (Throwable ignored) {
            }
            multicastLock = null;
        }
    }

    private void updateForeground(String title, String text) {
        if (ending.get()) {
            return;
        }
        notifications.notify(NOTIFICATION_ID, statusNotification(title, text));
    }

    private Notification statusNotification(String title, String text) {
        return baseNotification()
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .addAction(stopAction())
                .build();
    }

    private Notification.Builder baseNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        int immutable = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            immutable |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent openPendingIntent = PendingIntent.getActivity(this, 7400, openIntent, immutable);

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOnlyAlertOnce(true)
                .setContentIntent(openPendingIntent);
    }

    private Notification.Action stopAction() {
        Intent stopIntent = new Intent(this, RemotePairingService.class).setAction(ACTION_STOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getService(this, REQUEST_STOP, stopIntent, flags);
        return new Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancelar",
                pendingIntent)
                .build();
    }
}
