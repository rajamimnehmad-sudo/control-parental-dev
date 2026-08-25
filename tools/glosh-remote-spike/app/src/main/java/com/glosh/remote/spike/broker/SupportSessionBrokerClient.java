package com.glosh.remote.spike.broker;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class SupportSessionBrokerClient {
    public record DeviceMetadata(String manufacturer, String model, String androidVersion) {
    }

    public interface Listener {
        void onPending(String requestId);

        void onSessionReady(String descriptor);

        void onUnavailable();

        void onError();
    }

    public interface AvailabilityListener {
        void onAvailable();

        void onUnavailable();

        void onError();
    }

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final long POLL_DELAY_MS = 1_500;
    private static final int MAX_RESPONSE_CHARS = 16_384;

    private final OkHttpClient http = new OkHttpClient();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final String baseUrl;
    private final SecureRandom random = new SecureRandom();
    private final BrokerWaitPolicy waitPolicy = new BrokerWaitPolicy();
    private int generation;
    private String requestId;
    private String nonce;
    private RendezvousIdentity identity;
    private Call activeCall;

    public SupportSessionBrokerClient(String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl;
    }

    public synchronized void discover(AvailabilityListener listener) {
        cancelInternal();
        int current = ++generation;
        if (baseUrl.isEmpty()) {
            post(current, listener::onUnavailable);
            return;
        }
        enqueueDiscover(current, listener);
    }

    public synchronized void request(DeviceMetadata device, Listener listener) {
        cancelInternal();
        int current = ++generation;
        if (baseUrl.isEmpty()) {
            post(current, listener::onUnavailable);
            return;
        }
        createRequest(current, device, listener);
    }

    public synchronized void cancel() {
        String id = requestId;
        String requestNonce = nonce;
        ++generation;
        cancelInternal();
        if (!baseUrl.isEmpty() && id != null && requestNonce != null) {
            http.newCall(actionRequest(boundAction("revoke", id, requestNonce)))
                    .enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException error) {
                    // Best-effort revocation; local key destruction already fails closed.
                }

                @Override
                public void onResponse(Call call, Response response) {
                    response.close();
                }
            });
        }
    }

    private void enqueueDiscover(int current, AvailabilityListener listener) {
        enqueue(current, actionRequest(action("discover")),
                new ResponseHandler() {
                    @Override
                    public void handle(Response response) throws Exception {
                        JSONObject value = responseJson(response);
                        if (!response.isSuccessful() || !value.optBoolean("available", false)) {
                            availabilityUnavailable(current, listener);
                            return;
                        }
                        post(current, listener::onAvailable);
                    }

                    @Override
                    public void failed() {
                        availabilityFailed(current, listener);
                    }
                });
    }

    private void availabilityUnavailable(int current, AvailabilityListener listener) {
        synchronized (this) {
            if (current != generation) {
                return;
            }
            cancelInternal();
        }
        post(current, listener::onUnavailable);
    }

    private void availabilityFailed(int current, AvailabilityListener listener) {
        synchronized (this) {
            if (current != generation) {
                return;
            }
            cancelInternal();
        }
        post(current, listener::onError);
    }

    private void createRequest(int current, DeviceMetadata device, Listener listener) {
        synchronized (this) {
            if (current != generation) {
                return;
            }
            try {
                if (!waitPolicy.startNextRequest()) {
                    unavailable(current, listener);
                    return;
                }
                identity = RendezvousIdentity.generate();
                requestId = randomToken(18);
                nonce = randomToken(24);
                JSONObject body = new JSONObject()
                        .put("action", "request")
                        .put("request_id", requestId)
                        .put("nonce", nonce)
                        .put("public_key", identity.encodedPublicKey())
                        .put("manufacturer", safe(device.manufacturer(), 40))
                        .put("model", safe(device.model(), 80))
                        .put("android_version", safe(device.androidVersion(), 30));
                enqueueCreate(current, device, actionRequest(body), listener);
            } catch (Throwable error) {
                fail(current, listener);
            }
        }
    }

    private void enqueueCreate(
            int current,
            DeviceMetadata device,
            Request request,
            Listener listener) {
        enqueue(current, request, new ResponseHandler() {
            @Override
            public void handle(Response response) {
                if (!response.isSuccessful()) {
                    if (response.code() == 503 || isTransient(response.code())) {
                        retryFreshRequest(current, device, listener);
                    } else {
                        fail(current, listener);
                    }
                    return;
                }
                recordSuccess(current);
                post(current, () -> listener.onPending(currentRequestId(current)));
                schedulePoll(current, device, listener);
            }

            @Override
            public void failed() {
                // The request may have reached the broker. Poll the same id/nonce first so a
                // transport error cannot create a duplicate pending request.
                retryPoll(current, device, listener);
            }
        });
    }

    private void schedulePoll(int current, DeviceMetadata device, Listener listener) {
        main.postDelayed(() -> poll(current, device, listener), POLL_DELAY_MS);
    }

    private void poll(int current, DeviceMetadata device, Listener listener) {
        JSONObject body = boundAction(current, "poll");
        if (body == null) {
            return;
        }
        enqueue(current, actionRequest(body), new ResponseHandler() {
            @Override
            public void handle(Response response) throws Exception {
                if (!response.isSuccessful()) {
                    if (response.code() == 404 || response.code() == 410) {
                        retryFreshRequest(current, device, listener);
                    } else if (isTransient(response.code())) {
                        retryPoll(current, device, listener);
                    } else {
                        fail(current, listener);
                    }
                    return;
                }
                recordSuccess(current);
                JSONObject value = responseJson(response);
                String state = value.optString("state", value.optString("status", ""));
                if ("pending".equals(state)) {
                    schedulePoll(current, device, listener);
                } else if ("accepted".equals(state) || "ready".equals(state)) {
                    claim(current, listener);
                } else if (shouldRenew(current, state)) {
                    renewExpiredRequest(current, device, listener);
                } else {
                    unavailable(current, listener);
                }
            }

            @Override
            public void failed() {
                retryPoll(current, device, listener);
            }
        });
    }

    private void retryPoll(int current, DeviceMetadata device, Listener listener) {
        long delay = nextRetryDelay(current);
        if (delay < 0L) {
            fail(current, listener);
            return;
        }
        main.postDelayed(() -> poll(current, device, listener), delay);
    }

    private void retryFreshRequest(int current, DeviceMetadata device, Listener listener) {
        long delay = nextRetryDelay(current);
        if (delay < 0L) {
            unavailable(current, listener);
            return;
        }
        main.postDelayed(() -> renewExpiredRequest(current, device, listener), delay);
    }

    private void renewExpiredRequest(int current, DeviceMetadata device, Listener listener) {
        synchronized (this) {
            if (current != generation) {
                return;
            }
            destroyIdentity();
            requestId = null;
            nonce = null;
            activeCall = null;
        }
        createRequest(current, device, listener);
    }

    private synchronized boolean shouldRenew(int current, String state) {
        return current == generation && waitPolicy.shouldRenew(state);
    }

    private synchronized long nextRetryDelay(int current) {
        return current == generation ? waitPolicy.nextRetryDelayMillis() : -1L;
    }

    private synchronized void recordSuccess(int current) {
        if (current == generation) {
            waitPolicy.recordSuccess();
        }
    }

    private void claim(int current, Listener listener) {
        JSONObject body = boundAction(current, "claim");
        if (body == null) {
            return;
        }
        enqueue(current, actionRequest(body), new ResponseHandler() {
            @Override
            public void handle(Response response) throws Exception {
                if (!response.isSuccessful()) {
                    fail(current, listener);
                    return;
                }
                deliver(current, responseJson(response).getString("ciphertext"), listener);
            }

            @Override
            public void failed() {
                // Claim is intentionally not retried: the broker consumes ciphertext on claim,
                // so ambiguous transport failure must fail closed rather than risk state reuse.
                fail(current, listener);
            }
        });
    }

    private synchronized JSONObject boundAction(int current, String action) {
        if (current != generation || requestId == null || nonce == null) {
            return null;
        }
        return boundAction(action, requestId, nonce);
    }

    private void deliver(int current, String sealed, Listener listener) {
        String id;
        String requestNonce;
        RendezvousIdentity currentIdentity;
        synchronized (this) {
            if (current != generation || identity == null) {
                return;
            }
            id = requestId;
            requestNonce = nonce;
            currentIdentity = identity;
        }
        try {
            String descriptor = SealedSession.open(currentIdentity, sealed, id, requestNonce);
            synchronized (this) {
                if (current != generation) {
                    return;
                }
                destroyIdentity();
                requestId = null;
                nonce = null;
                activeCall = null;
            }
            post(current, () -> listener.onSessionReady(descriptor));
        } catch (Throwable error) {
            fail(current, listener);
        }
    }

    private void enqueue(int current, Request request, ResponseHandler handler) {
        Call call = http.newCall(request);
        synchronized (this) {
            if (current != generation) {
                call.cancel();
                return;
            }
            activeCall = call;
        }
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call ignored, IOException error) {
                handler.failed();
            }

            @Override
            public void onResponse(Call ignored, Response response) {
                try (response) {
                    handler.handle(response);
                } catch (Throwable error) {
                    handler.failed();
                }
            }
        });
    }

    private Request actionRequest(JSONObject body) {
        return new Request.Builder()
                .url(baseUrl)
                .header("Cache-Control", "no-store")
                .post(RequestBody.create(body.toString(), JSON))
                .build();
    }

    private static JSONObject action(String value) {
        try {
            return new JSONObject().put("action", value);
        } catch (JSONException error) {
            throw new IllegalStateException("Could not create broker action", error);
        }
    }

    private static JSONObject boundAction(String action, String requestId, String nonce) {
        try {
            return new JSONObject()
                    .put("action", action)
                    .put("request_id", requestId)
                    .put("nonce", nonce);
        } catch (JSONException error) {
            throw new IllegalStateException("Could not create broker action", error);
        }
    }

    private JSONObject responseJson(Response response) throws Exception {
        if (response.body() == null) {
            throw new IOException("Empty broker response");
        }
        String raw = response.body().string();
        if (raw.length() > MAX_RESPONSE_CHARS) {
            throw new IOException("Broker response too large");
        }
        return new JSONObject(raw);
    }

    private static boolean isTransient(int code) {
        return code == 408 || code == 425 || code == 429 || code >= 500;
    }

    private void unavailable(int current, Listener listener) {
        synchronized (this) {
            if (current != generation) {
                return;
            }
            cancelInternal();
        }
        post(current, listener::onUnavailable);
    }

    private void fail(int current, Listener listener) {
        synchronized (this) {
            if (current != generation) {
                return;
            }
            cancelInternal();
        }
        post(current, listener::onError);
    }

    private synchronized String currentRequestId(int current) {
        return current == generation && requestId != null ? requestId : "";
    }

    private void post(int current, Runnable action) {
        main.post(() -> {
            synchronized (SupportSessionBrokerClient.this) {
                if (current != generation) {
                    return;
                }
            }
            action.run();
        });
    }

    private synchronized void cancelInternal() {
        if (activeCall != null) {
            activeCall.cancel();
            activeCall = null;
        }
        destroyIdentity();
        requestId = null;
        nonce = null;
        waitPolicy.reset();
    }

    private void destroyIdentity() {
        if (identity != null) {
            identity.destroy();
            identity = null;
        }
    }

    private String randomToken(int bytes) {
        byte[] value = new byte[bytes];
        random.nextBytes(value);
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
        } finally {
            java.util.Arrays.fill(value, (byte) 0);
        }
    }

    private static String safe(String value, int limit) {
        String clean = value == null ? "" : value.replaceAll("[\\r\\n\\t]", " ").trim();
        return clean.length() <= limit ? clean : clean.substring(0, limit);
    }

    private interface ResponseHandler {
        void handle(Response response) throws Exception;

        void failed();
    }
}
