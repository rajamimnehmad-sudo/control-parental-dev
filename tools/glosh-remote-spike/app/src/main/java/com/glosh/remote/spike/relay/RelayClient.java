package com.glosh.remote.spike.relay;

import android.os.Build;

import com.glosh.remote.spike.adb.AdbShell;
import com.glosh.remote.spike.adb.RemoteProvisioningController;
import com.glosh.remote.spike.crypto.SessionCrypto;
import com.glosh.remote.spike.protocol.JoinDescriptor;

import org.json.JSONObject;

import java.io.Closeable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public final class RelayClient implements Closeable {
    public interface Listener {
        void onState(String state);
        void onAuthenticated();
        void onError(String message, Throwable error);
        void onClosed();
    }

    private final AdbShell shell;
    private final RemoteProvisioningController provisioning;
    private final OkHttpClient client;
    private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor();

    private volatile WebSocket socket;
    private volatile JoinDescriptor descriptor;
    private volatile Listener listener;
    private volatile boolean authenticated;
    private volatile String challengeNonce;
    private long inboundSeq;
    private long outboundSeq;
    private long generation;

    public RelayClient(AdbShell shell) {
        this(shell, null);
    }

    public RelayClient(AdbShell shell, RemoteProvisioningController provisioning) {
        this.shell = shell;
        this.provisioning = provisioning;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .build();
    }

    public synchronized void connect(String rawDescriptor, Listener listener) {
        disconnectLocked();
        JoinDescriptor parsed = JoinDescriptor.parse(rawDescriptor);
        long newGeneration = ++generation;
        this.descriptor = parsed;
        this.listener = listener;
        this.authenticated = false;
        this.challengeNonce = null;
        this.inboundSeq = 0;
        this.outboundSeq = 0;

        String base = parsed.websocketUrl().endsWith("/")
                ? parsed.websocketUrl().substring(0, parsed.websocketUrl().length() - 1)
                : parsed.websocketUrl();
        String url = base + "/agent?sid=" + urlEncode(parsed.sessionId());

        listener.onState("Conectando al relay temporal…");
        Request request = new Request.Builder().url(url).build();
        socket = client.newWebSocket(request, new SocketListener(newGeneration));
    }

    public synchronized void disconnect() {
        generation++;
        disconnectLocked();
        listener = null;
    }

    private void disconnectLocked() {
        authenticated = false;
        challengeNonce = null;
        WebSocket currentSocket = socket;
        socket = null;
        if (currentSocket != null) {
            currentSocket.close(1000, "session closed");
        }
        JoinDescriptor currentDescriptor = descriptor;
        descriptor = null;
        if (currentDescriptor != null) {
            currentDescriptor.destroy();
        }
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public void close() {
        disconnect();
        if (provisioning != null) {
            provisioning.close();
        }
        commandExecutor.shutdownNow();
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }

    private final class SocketListener extends WebSocketListener {
        private final long socketGeneration;

        private SocketListener(long socketGeneration) {
            this.socketGeneration = socketGeneration;
        }

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            if (!isCurrent(socketGeneration, webSocket)) {
                webSocket.close(1000, "stale connection");
                return;
            }
            notifyState("Relay conectado. Autenticando sesión…");
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            if (!isCurrent(socketGeneration, webSocket)) {
                return;
            }
            try {
                JSONObject message = new JSONObject(text);
                String type = message.optString("type", "");
                switch (type) {
                    case "challenge":
                        handleChallenge(socketGeneration, webSocket, message);
                        break;
                    case "ready":
                        handleReady(socketGeneration, message);
                        break;
                    case "box":
                        handleBox(socketGeneration, message);
                        break;
                    default:
                        throw new SecurityException("Tipo de mensaje remoto no permitido: " + type);
                }
            } catch (Throwable error) {
                fail(socketGeneration, webSocket, "Mensaje remoto inválido.", error);
            }
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            if (!isCurrent(socketGeneration, webSocket)) {
                return;
            }
            fail(socketGeneration, webSocket, "Se perdió la conexión con el relay.", t);
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            if (!isCurrent(socketGeneration, webSocket)) {
                return;
            }
            Listener current = listener;
            terminateCurrent(socketGeneration, webSocket);
            if (current != null) {
                current.onClosed();
            }
        }
    }

    private void handleChallenge(long expectedGeneration, WebSocket webSocket, JSONObject message) throws Exception {
        ensureCurrent(expectedGeneration, webSocket);
        JoinDescriptor current = requireDescriptor();
        String nonce = message.getString("nonce");
        if (nonce.length() < 20 || nonce.length() > 128) {
            throw new SecurityException("Challenge inválido.");
        }
        challengeNonce = nonce;

        byte[] key = current.sessionKey();
        try {
            String proof = SessionCrypto.hmac(
                    key,
                    "agent-auth:" + current.sessionId() + ":" + nonce);

            JSONObject device = new JSONObject()
                    .put("manufacturer", Build.MANUFACTURER)
                    .put("model", Build.MODEL)
                    .put("device", Build.DEVICE)
                    .put("android", Build.VERSION.RELEASE)
                    .put("sdk", Build.VERSION.SDK_INT);

            JSONObject auth = new JSONObject()
                    .put("v", 1)
                    .put("type", "auth")
                    .put("proof", proof)
                    .put("device", device);
            if (!webSocket.send(auth.toString())) {
                throw new IllegalStateException("WebSocket rechazó la autenticación.");
            }
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    private void handleReady(long expectedGeneration, JSONObject message) throws Exception {
        ensureCurrent(expectedGeneration, socket);
        JoinDescriptor current = requireDescriptor();
        String nonce = challengeNonce;
        if (nonce == null) {
            throw new SecurityException("Ready recibido sin challenge.");
        }

        byte[] key = current.sessionKey();
        try {
            String expected = SessionCrypto.hmac(
                    key,
                    "server-ready:" + current.sessionId() + ":" + nonce);
            if (!SessionCrypto.constantTimeEquals(expected, message.getString("serverProof"))) {
                throw new SecurityException("El relay no pudo demostrar la clave de sesión.");
            }
        } finally {
            Arrays.fill(key, (byte) 0);
        }

        authenticated = true;
        challengeNonce = null;
        notifyState("Sesión remota autenticada extremo a extremo.");
        Listener currentListener = listener;
        if (currentListener != null) {
            currentListener.onAuthenticated();
        }
    }

    private void handleBox(long expectedGeneration, JSONObject message) throws Exception {
        ensureCurrent(expectedGeneration, socket);
        if (!authenticated) {
            throw new SecurityException("Comando cifrado recibido antes de autenticar.");
        }
        JoinDescriptor current = requireDescriptor();
        long seq = message.getLong("seq");
        synchronized (this) {
            if (expectedGeneration != generation) {
                throw new IllegalStateException("Conexión remota stale.");
            }
            if (seq <= inboundSeq) {
                throw new SecurityException("Replay o secuencia remota inválida.");
            }
            inboundSeq = seq;
        }

        String aad = current.sessionId() + ":server:" + seq;
        byte[] key = current.sessionKey();
        byte[] plaintext;
        try {
            plaintext = SessionCrypto.decrypt(
                    key,
                    message.getString("nonce"),
                    message.getString("ciphertext"),
                    aad);
        } finally {
            Arrays.fill(key, (byte) 0);
        }

        JSONObject payload;
        try {
            payload = new JSONObject(new String(plaintext, StandardCharsets.UTF_8));
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
        if (!"command".equals(payload.optString("kind"))) {
            throw new SecurityException("Payload remoto no es un comando.");
        }

        String requestId = payload.getString("requestId");
        String action = payload.getString("action");
        JSONObject params = payload.optJSONObject("params");
        if (requestId.length() == 0 || requestId.length() > 80 || action.length() == 0 || action.length() > 40) {
            throw new SecurityException("Comando fuera de límites.");
        }

        commandExecutor.execute(() -> executeAndReply(expectedGeneration, requestId, action, params));
    }

    private void executeAndReply(
            long expectedGeneration,
            String requestId,
            String action,
            JSONObject params) {
        if (!isCurrentGeneration(expectedGeneration)) {
            return;
        }
        boolean ok = true;
        String output;
        try {
            if ("ping".equals(action)) {
                output = "pong";
            } else if (RemoteProvisioningController.supports(action)) {
                if (provisioning == null) {
                    throw new SecurityException("Aprovisionamiento no disponible en esta sesión.");
                }
                output = provisioning.execute(action, params);
            } else {
                output = shell.execute(action);
            }
        } catch (Throwable error) {
            ok = false;
            output = error.getClass().getSimpleName() + ": "
                    + (error.getMessage() == null ? "sin detalle" : error.getMessage());
        }

        try {
            sendResult(expectedGeneration, requestId, action, ok, output);
        } catch (Throwable error) {
            WebSocket current = socket;
            if (current != null) {
                fail(expectedGeneration, current, "No se pudo devolver el resultado remoto.", error);
            }
        }
    }

    private void sendResult(long expectedGeneration, String requestId, String action, boolean ok, String output)
            throws Exception {
        WebSocket currentSocket = socket;
        ensureCurrent(expectedGeneration, currentSocket);
        JoinDescriptor current = requireDescriptor();
        if (!authenticated) {
            throw new IllegalStateException("Sesión remota cerrada.");
        }

        JSONObject payload = new JSONObject()
                .put("kind", "result")
                .put("requestId", requestId)
                .put("action", action)
                .put("ok", ok)
                .put("output", output);

        long seq;
        synchronized (this) {
            if (expectedGeneration != generation) {
                throw new IllegalStateException("Conexión remota stale.");
            }
            seq = ++outboundSeq;
        }
        String aad = current.sessionId() + ":agent:" + seq;
        byte[] key = current.sessionKey();
        SessionCrypto.Box box;
        try {
            box = SessionCrypto.encrypt(
                    key,
                    payload.toString().getBytes(StandardCharsets.UTF_8),
                    aad);
        } finally {
            Arrays.fill(key, (byte) 0);
        }

        JSONObject envelope = new JSONObject()
                .put("v", 1)
                .put("type", "box")
                .put("seq", seq)
                .put("nonce", box.nonce())
                .put("ciphertext", box.ciphertext());
        if (!currentSocket.send(envelope.toString())) {
            throw new IllegalStateException("WebSocket rechazó el resultado.");
        }
    }

    private synchronized boolean isCurrent(long expectedGeneration, WebSocket webSocket) {
        return expectedGeneration == generation && webSocket != null && webSocket == socket;
    }

    private synchronized boolean isCurrentGeneration(long expectedGeneration) {
        return expectedGeneration == generation && socket != null;
    }

    private void ensureCurrent(long expectedGeneration, WebSocket webSocket) {
        if (!isCurrent(expectedGeneration, webSocket)) {
            throw new IllegalStateException("Conexión remota stale.");
        }
    }

    private JoinDescriptor requireDescriptor() {
        JoinDescriptor current = descriptor;
        if (current == null) {
            throw new IllegalStateException("No hay descriptor de sesión activo.");
        }
        return current;
    }

    private synchronized void terminateCurrent(long expectedGeneration, WebSocket expectedSocket) {
        if (expectedGeneration != generation || expectedSocket == null || expectedSocket != socket) {
            return;
        }
        authenticated = false;
        challengeNonce = null;
        socket = null;
        JoinDescriptor current = descriptor;
        descriptor = null;
        if (current != null) {
            current.destroy();
        }
        generation++;
    }

    private void notifyState(String state) {
        Listener current = listener;
        if (current != null) {
            current.onState(state);
        }
    }

    private void fail(long expectedGeneration, WebSocket failingSocket, String message, Throwable error) {
        if (!isCurrent(expectedGeneration, failingSocket)) {
            return;
        }
        Listener current = listener;
        terminateCurrent(expectedGeneration, failingSocket);
        if (current != null) {
            current.onError(message, error);
        }
        failingSocket.close(1008, "protocol error");
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo codificar Session ID.", e);
        }
    }
}
