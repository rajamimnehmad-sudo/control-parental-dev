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
import com.glosh.remote.spike.adb.LocalAdbSession;
import com.glosh.remote.spike.protocol.JoinDescriptor;
import com.glosh.remote.spike.protocol.PairingPin;
import com.glosh.remote.spike.relay.RelaySessionSupervisor;
import com.glosh.remote.spike.session.PairingAuthorityPolicy;
import com.glosh.remote.spike.session.PairingEndpointTracker;
import com.glosh.remote.spike.session.PairingFailureClassifier;
import com.glosh.remote.spike.session.PairingFailureKind;
import com.glosh.remote.spike.session.PairingReusePolicy;
import com.glosh.remote.spike.session.PairingSubmissionGuard;
import com.glosh.remote.spike.session.PairingUiState;
import com.glosh.remote.spike.session.SessionState;
import com.glosh.remote.spike.wizard.SamsungGuideStep;

import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.muntashirakon.adb.android.AdbMdns;

/** Foreground owner of Wireless ADB bootstrap and the recoverable secure support session. */
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
    private static final long REUSE_IDENTITY_TIMEOUT_MS = 2_500L;

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
    private LocalAdbSession localAdbSession;
    private RelaySessionSupervisor relaySession;
    private String joinUri;
    private volatile String pendingPairingCode;
    private volatile boolean reusedAdbIdentity;

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
                                ? "Preparando ADB. Si hace falta, abrí Vincular dispositivo con código."
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
        reusedAdbIdentity = false;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            finishWithError("Glosh Remote requiere Android 11 o superior.");
            return;
        }

        final AdbConnectionManager manager;
        try {
            JoinDescriptor check = JoinDescriptor.parse(rawJoin);
            check.destroy();
            sessionState = SessionState.PREPARING;
            pairingUiState = PairingUiState.DISCOVERING_ENDPOINT;
            joinUri = rawJoin;
            manager = AdbConnectionManager.getInstance(getApplicationContext());
        } catch (Throwable error) {
            finishWithError("La sesión de soporte no es válida. Intentá nuevamente.");
            return;
        }

        updateForeground(
                6,
                "Preparando ADB",
                "Glosh está buscando una vinculación guardada. Si no existe, usará el código nuevo automáticamente.");
        executor.execute(() -> reuseIdentityOrStartPairing(manager));
    }

    private void reuseIdentityOrStartPairing(AdbConnectionManager manager) {
        try {
            if (manager.ensureConnected(this, REUSE_IDENTITY_TIMEOUT_MS)) {
                reusedAdbIdentity = true;
                pairingUiState = PairingUiState.CONNECTING;
                startConnectedRuntime(manager);
                return;
            }
        } catch (Throwable expectedWhenNotPaired) {
            Log.d(TAG, "Stored ADB identity is not currently reusable.");
        }
        try {
            manager.disconnect();
        } catch (Throwable ignored) {
        }
        if (!ending.get()) {
            startPairingDiscovery();
        }
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
        Intent replyIntent = new Intent(this, RemotePairingService.class).setAction(ACTION_REPLY);
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
        if (PairingReusePolicy.shouldIgnoreSubmittedCode(reusedAdbIdentity, pairingUiState)) {
            pendingPairingCode = null;
            return;
        }
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
        if (sessionState == SessionState.PREPARING && joinUri != null && endpoint == null) {
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
                joinUri != null)
                || !pairingGuard.tryStart()) {
            return;
        }
        pairingUiState = PairingUiState.CONNECTING;
        updateForeground(7, "Conectando ADB", "Código recibido. Completando la vinculación segura…");
        executor.execute(() -> pairAndConnect(endpoint, code));
    }

    private void pairAndConnect(PairingEndpointTracker.Endpoint endpoint, String code) {
        final AdbConnectionManager manager;
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
            if (!manager.ensureConnected(this, CONNECT_TIMEOUT_MS)) {
                throw new IllegalStateException("No se pudo abrir el canal TLS local de ADB.");
            }
            startConnectedRuntime(manager);
        } catch (Throwable error) {
            Log.w(TAG, "ADB paired but local/support connection failed", error);
            if (!ending.get()) {
                finishWithError("ADB se vinculó, pero no pudimos abrir la sesión. Intentá nuevamente.");
            }
        }
    }

    private void startConnectedRuntime(AdbConnectionManager manager) throws Exception {
        stopPairingDiscovery();
        pairingEndpoints.clear();
        LocalAdbSession local = new LocalAdbSession(this, manager);
        local.activate(new LocalAdbSession.Listener() {
            @Override
            public void onConnectionLost() {
                if (ending.get()) {
                    return;
                }
                if (sessionState == SessionState.CONNECTED) {
                    sessionState = SessionState.RECONNECTING;
                    pairingUiState = PairingUiState.INACTIVE;
                }
                updateForeground(7, "Reconectando ADB", "Glosh está recuperando ADB automáticamente…");
            }

            @Override
            public void onConnectionRestored() {
                if (ending.get()) {
                    return;
                }
                RelaySessionSupervisor relay = relaySession;
                if (relay != null && relay.isAuthenticated()) {
                    sessionState = SessionState.CONNECTED;
                    pairingUiState = PairingUiState.INACTIVE;
                    updateForeground(7, "Conectado con soporte", "La sesión temporal y segura ya está activa.");
                }
            }

            @Override
            public void onReconnectError(Throwable error) {
                Log.w(TAG, "Wireless ADB reconnect still pending", error);
            }
        });
        localAdbSession = local;

        RelaySessionSupervisor relay = new RelaySessionSupervisor();
        relaySession = relay;
        relay.start(joinUri, local.shell(), new RelaySessionSupervisor.Listener() {
            @Override
            public void onState(String state, boolean recovery) {
                Log.d(TAG, "Relay state: " + state);
                if (ending.get()) {
                    return;
                }
                if (recovery) {
                    sessionState = SessionState.RECONNECTING;
                    pairingUiState = PairingUiState.INACTIVE;
                }
                updateForeground(7, recovery ? "Reconectando…" : "Conectando con soporte", state);
            }

            @Override
            public void onAuthenticated(boolean recovery) {
                if (ending.get()) {
                    return;
                }
                if (!recovery) {
                    boolean validPairedBootstrap = PairingAuthorityPolicy.canBecomeConnected(
                            sessionState,
                            pairingUiState,
                            pairingGuard.isActive(),
                            joinUri != null);
                    boolean validReusedBootstrap = reusedAdbIdentity
                            && sessionState == SessionState.PREPARING
                            && pairingUiState == PairingUiState.CONNECTING
                            && joinUri != null;
                    if (!validPairedBootstrap && !validReusedBootstrap) {
                        finishWithError("La conexión no pudo validarse. Intentá nuevamente.");
                        return;
                    }
                    pairingGuard.finish();
                    reusedAdbIdentity = false;
                } else if (sessionState != SessionState.RECONNECTING
                        && sessionState != SessionState.CONNECTED) {
                    finishWithError("La reconexión no pudo validarse. Intentá nuevamente.");
                    return;
                }
                pairingUiState = PairingUiState.INACTIVE;
                LocalAdbSession currentLocal = localAdbSession;
                sessionState = currentLocal != null && currentLocal.isConnected()
                        ? SessionState.CONNECTED
                        : SessionState.RECONNECTING;
                updateForeground(
                        7,
                        sessionState == SessionState.CONNECTED ? "Conectado con soporte" : "Reconectando ADB",
                        sessionState == SessionState.CONNECTED
                                ? "La conexión temporal y segura ya está activa."
                                : "Relay listo; Glosh está recuperando ADB automáticamente…");
            }

            @Override
            public void onPermanentFailure(String message, Throwable error) {
                Log.w(TAG, "Relay recovery exhausted", error);
                if (!ending.get()) {
                    finishWithError(message);
                }
            }
        });
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

    private void showPairingFailure(PairingFailureKind failure, String message, Throwable error) {
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
        RelaySessionSupervisor currentRelay = relaySession;
        relaySession = null;
        if (currentRelay != null) {
            currentRelay.close();
        }
        LocalAdbSession currentLocal = localAdbSession;
        localAdbSession = null;
        if (currentLocal != null) {
            currentLocal.close();
        } else {
            AdbConnectionManager.releaseConnection();
        }
        pairingEndpoints.clear();
        joinUri = null;
        pendingPairingCode = null;
        reusedAdbIdentity = false;
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
            }
            multicastLock = null;
        }
    }

    private void updateForeground(int step, String title, String text) {
        if (!ending.get()) {
            notifications.notify(NOTIFICATION_ID, statusNotification(step, title, text));
        }
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
