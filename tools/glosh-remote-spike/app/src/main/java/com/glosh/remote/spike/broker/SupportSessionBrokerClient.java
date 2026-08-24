package com.glosh.remote.spike.broker;

import android.os.Handler;
import android.os.Looper;

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

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final long POLL_DELAY_MS = 1_500;
    private static final int MAX_RESPONSE_CHARS = 16_384;

    private final OkHttpClient http = new OkHttpClient();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final String baseUrl;
    private final SecureRandom random = new SecureRandom();
    private int generation;
    private String requestId;
    private String nonce;
    private RendezvousIdentity identity;
    private Call activeCall;

    public SupportSessionBrokerClient(String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl;
    }

    public synchronized void request(DeviceMetadata device, Listener listener) {
        cancelInternal(false);
        int current = ++generation;
        if (baseUrl.isEmpty()) {
            post(current, listener::onUnavailable);
            return;
        }
        try {
            identity = RendezvousIdentity.generate();
            requestId = randomToken(18);
            nonce = randomToken(24);
            JSONObject body = new JSONObject()
                    .put("requestId", requestId)
                    .put("publicKey", identity.encodedPublicKey())
                    .put("nonce", nonce)
                    .put("manufacturer", safe(device.manufacturer(), 40))
                    .put("model", safe(device.model(), 80))
                    .put("android", safe(device.androidVersion(), 30));
            Request request = new Request.Builder()
                    .url(baseUrl + "/v1/requests")
                    .post(RequestBody.create(body.toString(), JSON))
                    .build();
            enqueueCreate(current, request, listener);
        } catch (Throwable error) {
            cancelInternal(false);
            post(current, listener::onError);
        }
    }

    public synchronized void cancel() {
        String id = requestId;
        String requestNonce = nonce;
        int current = ++generation;
        cancelInternal(false);
        if (!baseUrl.isEmpty() && id != null && requestNonce != null) {
            Request request = new Request.Builder()
                    .url(baseUrl + "/v1/requests/" + id + "?nonce=" + requestNonce)
                    .delete()
                    .build();
            http.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException error) {
                    // Best-effort revocation; local key destruction already makes delivery unusable.
                }

                @Override
                public void onResponse(Call call, Response response) {
                    response.close();
                }
            });
        }
    }

    private void enqueueCreate(int current, Request request, Listener listener) {
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
                fail(current, listener);
            }

            @Override
            public void onResponse(Call ignored, Response response) {
                try (response) {
                    if (response.code() == 503) {
                        unavailable(current, listener);
                        return;
                    }
                    if (response.code() != 201) {
                        fail(current, listener);
                        return;
                    }
                    post(current, () -> listener.onPending(currentRequestId(current)));
                    schedulePoll(current, listener);
                }
            }
        });
    }

    private void schedulePoll(int current, Listener listener) {
        main.postDelayed(() -> poll(current, listener), POLL_DELAY_MS);
    }

    private void poll(int current, Listener listener) {
        String id;
        String requestNonce;
        synchronized (this) {
            if (current != generation || requestId == null || nonce == null) {
                return;
            }
            id = requestId;
            requestNonce = nonce;
        }
        Request request = new Request.Builder()
                .url(baseUrl + "/v1/requests/" + id + "?nonce=" + requestNonce)
                .get()
                .build();
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
                fail(current, listener);
            }

            @Override
            public void onResponse(Call ignored, Response response) {
                try (response) {
                    if (response.code() == 202) {
                        schedulePoll(current, listener);
                        return;
                    }
                    if (response.code() != 200 || response.body() == null) {
                        fail(current, listener);
                        return;
                    }
                    String raw = response.body().string();
                    if (raw.length() > MAX_RESPONSE_CHARS) {
                        fail(current, listener);
                        return;
                    }
                    String sealed = new JSONObject(raw).getString("ciphertext");
                    deliver(current, sealed, listener);
                } catch (Throwable error) {
                    fail(current, listener);
                }
            }
        });
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

    private void unavailable(int current, Listener listener) {
        synchronized (this) {
            if (current != generation) {
                return;
            }
            cancelInternal(false);
        }
        post(current, listener::onUnavailable);
    }

    private void fail(int current, Listener listener) {
        synchronized (this) {
            if (current != generation) {
                return;
            }
            cancelInternal(false);
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

    private synchronized void cancelInternal(boolean advanceGeneration) {
        if (advanceGeneration) {
            generation++;
        }
        if (activeCall != null) {
            activeCall.cancel();
            activeCall = null;
        }
        destroyIdentity();
        requestId = null;
        nonce = null;
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
}
