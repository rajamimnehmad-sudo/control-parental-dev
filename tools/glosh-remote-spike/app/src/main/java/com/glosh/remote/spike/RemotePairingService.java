package com.glosh.remote.spike;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.app.Service;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.glosh.remote.spike.adb.AdbConnectionManager;
import com.glosh.remote.spike.adb.AdbShell;
import com.glosh.remote.spike.protocol.JoinDescriptor;
import com.glosh.remote.spike.protocol.PairingPin;
import com.glosh.remote.spike.relay.RelayClient;
import com.glosh.remote.spike.session.PairingAuthorityPolicy;
import com.glosh.remote.spike.session.PairingEndpointTracker;
import com.glosh.remote.spike.session.PairingFailureClassifier;
import com.glosh.remote.spike.session.PairingFailureKind;
import com.glosh.remote.spike.session.PairingSubmissionGuard;
import com.glosh.remote.spike.session.PairingUiState;
import com.glosh.remote.spike.session.SessionState;
import com.glosh.remote.spike.wizard.SamsungGuideStep;

import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.muntashirakon.adb.android.AdbMdns;

/** Foreground owner of local Wireless ADB pairing and the secure relay session. */
public final class RemotePairingService extends Service {
    public static final String ACTION_START = "com.glosh.remote.spike.START";
    public static final String ACTION_REPLY = "com.glosh.remote.spike.REPLY";
    public static final String ACTION_SUBMIT_CODE = "com.glosh.remote.spike.SUBMIT_CODE";
    public static final String ACTION_STOP = "com.glosh.remote.spike.STOP";
    public static final String EXTRA_JOIN_URI = "join_uri";
    public static final String EXTRA_PAIRING_CODE = "pairing_code";

    private static final String REMOTE_INPUT_CODE = "pair_code";
    private static final String CHANNEL_ID = "glosh_remote_pairing";
    private static final String TAG = "GloshRemote";
    private static final int NOTIFICATION_ID = 7401;
    private static final int FINAL_NOTIFICATION_ID = 7402;
    private static final int REQUEST_REPLY = 7411;
    private static final int REQUEST_STOP = 7412;
    private static final int TOTAL_STEPS = SamsungGuideStep.TOTAL_STEPS;
    private static final long CONNECT_TIMEOUT_MS = 15_000L;
    private static volatile SessionState sessionState = SessionState.IDLE;
    private static volatile PairingUiState pairingUiState = PairingUiState.INACTIVE;
    private static volatile PairingFailureKind pairingFailureKind = PairingFailureKind.NONE;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final PairingSubmissionGuard pairingGuard = new PairingSubmissionGuard();
    private final PairingEndpointTracker pairingEndpoints = new PairingEndpointTracker();
    private final AtomicBoolean ending = new AtomicBoolean(false);

    private NotificationManager notifications;
    private AdbMdns pairingDiscovery;
    private WifiManager.MulticastLock multicastLock;
    private RelayClient relayClient;
    private String joinUri;
    private volatile String pendingPairingCode;

    public static SessionState getSessionState() {
        return sessionState;
    }

    public static PairingUiState getPairingUiState() {
        return pairingUiState;
    }

    public static PairingFailureKind getPairingFailureKind() {
        return pairingFailureKind;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notifications = getSystemService(NotificationManager.class);
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

        String action = intent.getAction();
        if (SessionState.shouldIgnoreStart(action, ACTION_START, sessionState)) {
            intent.removeExtra(EXTRA_JOIN_URI);
            return START_NOT_STICKY;
        }
        if ((ACTION_REPLY.equals(action) || ACTION_SUBMIT_CODE.equals(action))
                && sessionState != SessionState.PREPARING) {
            intent.removeExtra(EXTRA_PAIRING_CODE);
            return START_NOT_STICKY;
        }

        int initialStep = ACTION_START.equals(action) ? 6 : 7;
        startForeground(
                NOTIFICATION_ID,
                statusNotification(
                        initialStep,
                        "Glosh · Paso " + initialStep + " de " + TOTAL_STEPS,
                        initialStep == 6
                                ? "Tocá Vincular dispositivo con código. Glosh detectará la vinculación local automáticamente."
                                : "Glosh está completando la conexión segura."));

        if (ACTION_STOP.equals(action)) {
            finishSession("Conexión cerrada", "El acceso temporal terminó correctamente.");
            return START_NOT_STICKY;
        }
        if (ACTION_REPLY.equals(action)) {
            handlePairingReply(intent);
            return START_NOT_STICKY;
        }
        if (ACTION_SUBMIT_CODE.equals(action)) {
            handlePairingCode(intent.getStringExtra(EXTRA_PAIRING_CODE));
            intent.removeExtra(EXTRA_PAIRING_CODE);
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
        sessionState = SessionState.IDLE;
        pairingUiState = PairingUiState.INACTIVE;
        pairingFailureKind = PairingFailureKind.NONE;
        cleanupRuntime();
        executor.shutdownNow();
        super.onDestroy();
    }

    private void begin(String rawJoin) {
        cleanupRuntime();
        ending.set(false);
        pairingGuard.finish();
        pairingFailureKind = PairingFailureKind.NONE;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            finishWithError("Glosh Remote requiere Android 11 o superior.");
            return;
        }

        try {
            JoinDescriptor check = JoinDescriptor.parse(rawJoin);
            check.destroy();
            sessionState = SessionState.PREPARING;
            pairingUiState = PairingUiState.DISCOVERING_ENDPOINT;
            joinUri = rawJoin;
            AdbConnectionManager.getInstance(getApplicationContext());
        } catch (Throwable error) {
            finishWithError("La sesión de soporte no es válida. Intentá nuevamente.");
            return;
        }

        updateForeground(
                6,
                "Glosh · Paso 6 de " + TOTAL_STEPS,
                "Tocá Vincular dispositivo con código. Cuando Android abra el código, Glosh habilita el ingreso automáticamente.");
        startPairingDiscovery();
    }

    private void startPairingDiscovery() {
        stopPairingDiscovery();
        acquireMulticastLock();

        pairingDiscovery = new AdbMdns(
                this,
                AdbMdns.SERVICE_TYPE_TLS_PAIRING,
                (InetAddress address, int port) -> {
                    if (ending.get()) {
                        return;
                    }
                    String host = address == null ? null : address.getHostAddress();
                    if (host == null || port <= 0) {
                        pairingEndpoints.lost(host);
                        if (!pairingGuard.isActive() && sessionState == SessionState.PREPARING) {
                            pairingUiState = PairingUiState.DISCOVERING_ENDPOINT;
                            updateForeground(
                                    6,
                                    "Esperando vinculación",
                                    "Abrí Vincular dispositivo con código. Glosh detectará la vinculación nueva automáticamente.");
                        }
                        return;
                    }

                    PairingEndpointTracker.Endpoint endpoint = pairingEndpoints.observe(host, port);
                    if (!pairingGuard.isActive()) {
                        showPairingCodeNotification(endpoint);
                    }
                });
        pairingDiscovery.start();
    }

    private void showPairingCodeNotification(PairingEndpointTracker.Endpoint endpoint) {
        if (ending.get() || !pairingEndpoints.isCurrent(endpoint)) {
            return;
        }

        String pendingCode = pendingPairingCode;
        if (PairingPin.isValid(pendingCode)) {
            pendingPairingCode = null;
            pairingUiState = PairingUiState.WAITING_FOR_CODE;
            handlePairingCode(pendingCode);
            return;
        }

        notifyCodeEntry(
                "Glosh · Paso 7 de " + TOTAL_STEPS,
                "Mirá los 6 números que muestra Android y escribilos acá. No hace falta volver a Glosh.",
                PairingUiState.WAITING_FOR_CODE);
    }

    private void notifyCodeEntry(String title, String text, PairingUiState state) {
        pairingUiState = state;
        Intent replyIntent = new Intent(this, RemotePairingService.class)
                .setAction(ACTION_REPLY);

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
                .setOnlyAlertOnce(false)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setSubText("Último paso")
                .setProgress(TOTAL_STEPS, 7, false)
                .setOngoing(true)
                .addAction(replyAction)
                .addAction(stopAction())
                .build();

        notifications.notify(NOTIFICATION_ID, notification);
    }

    private void handlePairingReply(Intent intent) {
        Bundle results = RemoteInput.getResultsFromIntent(intent);
        CharSequence value = results == null ? null : results.getCharSequence(REMOTE_INPUT_CODE);
        handlePairingCode(value == null ? "" : value.toString().trim());
    }

    private void handlePairingCode(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim();
        if (!PairingPin.isValid(code)) {
            showPairingFailure(
                    PairingFailureKind.PIN_REJECTED,
                    "Ingresá exactamente los 6 números que muestra Android.",
                    null);
            return;
        }

        pairingFailureKind = PairingFailureKind.NONE;
        PairingEndpointTracker.Endpoint endpoint = pairingEndpoints.current();
        if (sessionState == SessionState.PREPARING
                && joinUri != null
                && endpoint == null) {
            pendingPairingCode = code;
            pairingUiState = PairingUiState.DISCOVERING_ENDPOINT;
            updateForeground(
                    7,
                    "Código recibido",
                    "Esperando la vinculación local de Android para continuar automáticamente…");
            return;
        }

        if (!PairingAuthorityPolicy.canSubmit(
                sessionState,
                pairingUiState,
                pairingGuard.isActive(),
                endpoint != null,
                joinUri != null)) {
            return;
        }
        if (!pairingGuard.tryStart()) {
            return;
        }

        pairingUiState = PairingUiState.CONNECTING;
        updateForeground(
                7,
                "Glosh · Paso 7 de " + TOTAL_STEPS,
                "Código recibido. Completando la conexión segura…");
        executor.execute(() -> pairAndConnect(endpoint, code));
    }

    private void pairAndConnect(PairingEndpointTracker.Endpoint endpoint, String code) {
        AdbConnectionManager manager;
        try {
            manager = AdbConnectionManager.getInstance(getApplicationContext());
        } catch (Throwable error) {
            showPairingFailure(
                    PairingFailureKind.ADB_ERROR,
                    "No pudimos preparar ADB. Abrí nuevamente Vincular dispositivo con código.",
                    error);
            return;
        }

        if (!pairingEndpoints.isCurrent(endpoint)) {
            showPairingFailure(
                    PairingFailureKind.ENDPOINT_CHANGED,
                    "Android cambió la vinculación mientras conectábamos. Generá un código nuevo.",
                    null);
            return;
        }

        try {
            boolean paired = manager.pair(endpoint.host(), endpoint.port(), code);
            if (!paired) {
                showPairingFailure(
                        PairingFailureKind.ADB_ERROR,
                        "Android no completó el emparejamiento. Generá un código nuevo.",
                        null);
                return;
            }
        } catch (Throwable error) {
            PairingFailureKind failure = PairingFailureClassifier.classify(
                    error,
                    pairingEndpoints.isCurrent(endpoint));
            showPairingFailure(failure, pairingMessage(failure), error);
            return;
        }

        try {
            stopPairingDiscovery();
            pairingEndpoints.clear();
            pairingFailureKind = PairingFailureKind.NONE;
            updateForeground(
                    7,
                    "Glosh · Paso 7 de " + TOTAL_STEPS,
                    "Emparejamiento listo. Abriendo el canal local seguro…");

            boolean connected = manager.connectTls(this, CONNECT_TIMEOUT_MS);
            if (!connected && !manager.isConnected()) {
                throw new IllegalStateException("No se pudo abrir el canal TLS local de ADB.");
            }

            AdbShell shell = new AdbShell(manager);
            String canary = shell.execute("whoami").trim();
            if (canary.length() == 0) {
                throw new IllegalStateException("ADB respondió sin identidad shell.");
            }

            updateForeground(
                    7,
                    "Glosh · Paso 7 de " + TOTAL_STEPS,
                    "ADB local listo. Conectando de forma segura con soporte…");
            RelayClient client = new RelayClient(shell);
            relayClient = client;
            client.connect(joinUri, new RelayClient.Listener() {
                @Override
                public void onState(String state) {
                    Log.d(TAG, "Relay state: " + state);
                    if (!ending.get()) {
                        updateForeground(
                                7,
                                "Glosh · Paso 7 de " + TOTAL_STEPS,
                                "Conectando de forma segura con soporte…");
                    }
                }

                @Override
                public void onAuthenticated() {
                    if (!ending.get()) {
                        if (!PairingAuthorityPolicy.canBecomeConnected(
                                sessionState,
                                pairingUiState,
                                pairingGuard.isActive(),
                                joinUri != null)) {
                            finishWithError("La conexión no pudo validarse. Intentá nuevamente.");
                            return;
                        }
                        sessionState = SessionState.CONNECTED;
                        pairingUiState = PairingUiState.INACTIVE;
                        pairingFailureKind = PairingFailureKind.NONE;
                        pairingGuard.finish();
                        updateForeground(
                                7,
                                "Conectado con soporte",
                                "La conexión temporal y segura ya está activa.");
                    }
                }

                @Override
                public void onError(String message, Throwable error) {
                    Log.w(TAG, "Relay connection failed: " + message, error);
                    if (!ending.get()) {
                        finishWithError("La conexión con soporte se interrumpió. Intentá nuevamente.");
                    }
                }

                @Override
                public void onClosed() {
                    if (!ending.get()) {
                        finishSession("Conexión cerrada", "El acceso temporal terminó correctamente.");
                    }
                }
            });
        } catch (Throwable error) {
            Log.w(TAG, "ADB paired but local/support connection failed", error);
            if (!ending.get()) {
                finishWithError("ADB se vinculó, pero no pudimos abrir la sesión. Intentá nuevamente.");
            }
        }
    }

    private String pairingMessage(PairingFailureKind failure) {
        switch (failure) {
            case PIN_REJECTED:
                return "Android rechazó el código. Generá uno nuevo y escribilo acá.";
            case ENDPOINT_CHANGED:
                return "Android cambió la vinculación mientras conectábamos. Generá un código nuevo.";
            case ENDPOINT_UNAVAILABLE:
                return "La vinculación anterior dejó de estar disponible. Generá un código nuevo.";
            case ADB_ERROR:
            case NONE:
            default:
                return "No pudimos completar ADB. Abrí nuevamente Vincular dispositivo con código.";
        }
    }

    private void showPairingFailure(
            PairingFailureKind failure,
            String message,
            Throwable error) {
        if (ending.get()) {
            return;
        }
        if (error != null) {
            Log.w(TAG, "Pairing failed kind=" + failure, error);
        } else {
            Log.w(TAG, "Pairing failed kind=" + failure);
        }
        pendingPairingCode = null;
        pairingGuard.finish();
        pairingFailureKind = failure;
        notifyCodeEntry(
                failure == PairingFailureKind.PIN_REJECTED
                        ? "Necesitamos un código nuevo"
                        : "Reintentemos la vinculación",
                message,
                PairingUiState.CODE_FAILED);
    }

    private void finishWithError(String message) {
        finishSession("No pudimos conectar", message);
    }

    private void finishSession(String title, String message) {
        if (!ending.compareAndSet(false, true)) {
            return;
        }
        sessionState = SessionState.IDLE;
        pairingUiState = PairingUiState.INACTIVE;
        pairingFailureKind = PairingFailureKind.NONE;
        pairingGuard.finish();
        cleanupRuntime();
        stopForeground(STOP_FOREGROUND_REMOVE);

        Notification finished = baseNotification()
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message))
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
                // Best effort; local ADB identity is destroyed below regardless.
            }
        }

        AdbConnectionManager.resetIdentity();
        pairingEndpoints.clear();
        joinUri = null;
        pendingPairingCode = null;
    }

    private void stopPairingDiscovery() {
        AdbMdns current = pairingDiscovery;
        pairingDiscovery = null;
        if (current != null) {
            try {
                current.stop();
            } catch (Throwable ignored) {
                // Best effort shutdown.
            }
        }
        releaseMulticastLock();
    }

    private void acquireMulticastLock() {
        if (multicastLock != null && multicastLock.isHeld()) {
            return;
        }
        WifiManager wifi = getApplicationContext().getSystemService(WifiManager.class);
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
                // Already released by Android.
            }
            multicastLock = null;
        }
    }

    private void updateForeground(int step, String title, String text) {
        if (ending.get()) {
            return;
        }
        notifications.notify(NOTIFICATION_ID, statusNotification(step, title, text));
    }

    private Notification statusNotification(int step, String title, String text) {
        return baseNotification()
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setSubText(step >= TOTAL_STEPS ? "Último paso" : "Guía Samsung")
                .setProgress(TOTAL_STEPS, Math.max(0, Math.min(TOTAL_STEPS, step)), false)
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
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                this,
                7400,
                openIntent,
                immutable);

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
        PendingIntent pendingIntent = PendingIntent.getService(
                this,
                REQUEST_STOP,
                stopIntent,
                flags);
        return new Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancelar",
                pendingIntent)
                .build();
    }
}
