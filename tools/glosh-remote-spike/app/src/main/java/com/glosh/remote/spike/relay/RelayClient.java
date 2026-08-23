package com.glosh.remote.spike.relay;

import android.os.Build;

import com.glosh.remote.spike.adb.AdbShell;
import com.glosh.remote.spike.crypto.SessionCrypto;
import com.glosh.remote.spike.protocol.JoinDescriptor;

import org.json.JSONObject;

import java.io.Closeable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
    private final OkHttpClient client;
    private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor();

    private volatile WebSocket socket;
    private volatile JoinDescriptor descriptor;
    private volatile Listener listener;
    private volatile boolean authenticated;
    private volatile String challengeNonce;
    private long inboundSeq;
    private long outboundSeq;

    public RelayClient(AdbShell shell) {
        this.shell = shell;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .build();
    }

    public synchronized void connect(String rawDescriptor, Listener listener) {
        disconnect();
        JoinDescriptor parsed = JoinDescriptor.parse(rawDescriptor);
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
        socket = client.newWebSocket(request, new SocketListener());
    }

    public synchronized void disconnect() {
        authenticated = false;
        challengeNonce = null;
        WebSocket current = socket;
        socket = null;
        if (current != null) {
            current.close(1000, "session closed");
        }
        descriptor = null;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public void close() {
        disconnect();
        commandExecutor.shutdownNow();
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }

    private final class SocketListener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            notifyState("Relay conectado. Autenticando sesión…");
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            try {
                JSONObject message = new JSONObject(text);
                String type = message.optString("type", "");
                switch (type) {
                    case "challenge":
                        handleChallenge(webSocket, message);
                        break;
                    case "ready":
                        handleReady(message);
                        break;
                    case "box":
                        handleBox(message);
                        break;
                    default:
                        throw new SecurityException("Tipo de mensaje remoto no permitido: " + type);
                }
            } catch (Throwable error) {
                fail("Mensaje remoto inválido.", error);
            }
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            authenticated = false;
            fail("Se perdió la conexión con el relay.", t);
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            authenticated = false;
            Listener current = listener;
            if (current != null) {
                current.onClosed();
            }
        }
    }

    private void handleChallenge(WebSocket webSocket, JSONObject message) throws Exception {
        JoinDescriptor current = requireDescriptor();
        String nonce = message.getString("nonce");
        if (nonce.length() < 20 || nonce.length() > 128) {
            throw new SecurityException("Challenge inválido.");
        }
        challengeNonce = nonce;

        String proof = SessionCrypto.hmac(
                current.sessionKey(),
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
        webSocket.send(auth.toString());
    }

    private void handleReady(JSONObject message) throws Exception {
        JoinDescriptor current = requireDescriptor();
        String nonce = challengeNonce;
        if (nonce == null) {
            throw new SecurityException("Ready recibido sin challenge.");
        }
        String expected = SessionCrypto.hmac(
                current.sessionKey(),
                "server-ready:" + current.sessionId() + ":" + nonce);
        if (!SessionCrypto.constantTimeEquals(expected, message.getString("serverProof"))) {
            throw new SecurityException("El relay no pudo demostrar la clave de sesión.");
        }
        authenticated = true;
        notifyState("Sesión remota autenticada extremo a extremo.");
        Listener currentListener = listener;
        if (currentListener != null) {
            currentListener.onAuthenticated();
        }
    }

    private void handleBox(JSONObject message) throws Exception {
        if (!authenticated) {
            throw new SecurityException("Comando cifrado recibido antes de autenticar.");
        }
        JoinDescriptor current = requireDescriptor();
        long seq = message.getLong("seq");
        synchronized (this) {
            if (seq <= inboundSeq) {
                throw new SecurityException("Replay o secuencia remota inválida.");
            }
            inboundSeq = seq;
        }

        String aad = current.sessionId() + ":server:" + seq;
        byte[] plaintext = SessionCrypto.decrypt(
                current.sessionKey(),
                message.getString("nonce"),
                message.getString("ciphertext"),
                aad);
        JSONObject payload = new JSONObject(new String(plaintext, StandardCharsets.UTF_8));
        if (!"command".equals(payload.optString("kind"))) {
            throw new SecurityException("Payload remoto no es un comando.");
        }

        String requestId = payload.getString("requestId");
        String action = payload.getString("action");
        if (requestId.length() > 80 || action.length() > 40) {
            throw new SecurityException("Comando fuera de límites.");
        }

        commandExecutor.execute(() -> executeAndReply(requestId, action));
    }

    private void executeAndReply(String requestId, String action) {
        boolean ok = true;
        String output;
        try {
            if ("ping".equals(action)) {
                output = "pong";
            } else {
                output = shell.execute(action);
            }
        } catch (Throwable error) {
            ok = false;
            output = error.getClass().getSimpleName() + ": "
                    + (error.getMessage() == null ? "sin detalle" : error.getMessage());
        }

        try {
            sendResult(requestId, action, ok, output);
        } catch (Throwable error) {
            fail("No se pudo devolver el resultado remoto.", error);
        }
    }

    private void sendResult(String requestId, String action, boolean ok, String output) throws Exception {
        JoinDescriptor current = requireDescriptor();
        WebSocket currentSocket = socket;
        if (currentSocket == null || !authenticated) {
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
            seq = ++outboundSeq;
        }
        String aad = current.sessionId() + ":agent:" + seq;
        SessionCrypto.Box box = SessionCrypto.encrypt(
                current.sessionKey(),
                payload.toString().getBytes(StandardCharsets.UTF_8),
                aad);

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

    private JoinDescriptor requireDescriptor() {
        JoinDescriptor current = descriptor;
        if (current == null) {
            throw new IllegalStateException("No hay descriptor de sesión activo.");
        }
        return current;
    }

    private void notifyState(String state) {
        Listener current = listener;
        if (current != null) {
            current.onState(state);
        }
    }

    private void fail(String message, Throwable error) {
        Listener current = listener;
        if (current != null) {
            current.onError(message, error);
        }
        WebSocket currentSocket = socket;
        if (currentSocket != null) {
            currentSocket.close(1008, "protocol error");
        }
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo codificar Session ID.", e);
        }
    }
}
