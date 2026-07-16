import { createClient } from "@supabase/supabase-js";

type AlertPayload = {
  device_id?: string;
  alert_type?: string;
};

type AlertEvent = {
  event_id: string;
  account_id: string;
  device_name: string;
  title: string;
  body: string;
  should_send: boolean;
};

type PushToken = {
  fcm_token: string;
};

Deno.serve(async (request) => {
  if (request.method !== "POST") {
    return json({ error: "Method not allowed" }, 405);
  }

  const supabaseUrl = requiredEnv("SUPABASE_URL");
  const anonKey = requiredEnv("SUPABASE_ANON_KEY");
  const serviceRoleKey = requiredEnv("SUPABASE_SERVICE_ROLE_KEY");
  const deviceToken = request.headers.get("x-device-token") ?? "";
  const authHeader = request.headers.get("authorization") ?? `Bearer ${anonKey}`;
  const payload = await request.json().catch(() => ({})) as AlertPayload;

  if (!payload.device_id || !payload.alert_type) {
    return json({ error: "Missing device_id or alert_type" }, 400);
  }

  const anonClient = createClient(supabaseUrl, anonKey, {
    global: {
      headers: {
        authorization: authHeader,
        "x-device-token": deviceToken,
      },
    },
  });

  const { data: eventRows, error: eventError } = await anonClient.rpc("create_reinforced_protection_alert_event", {
    p_device_id: payload.device_id,
    p_alert_type: payload.alert_type,
  });
  if (eventError) {
    return json({ error: eventError.message }, 403);
  }

  const event = (eventRows?.[0] ?? null) as AlertEvent | null;
  if (!event) {
    return json({ error: "Event not created" }, 500);
  }
  if (!event.should_send) {
    return json({ event_id: event.event_id, sent: 0, failed: 0, deduplicated: true });
  }

  // Blocked attempts remain in the Super Admin audit feed. Only confirmed
  // degradation or explicit maintenance requests are routed to App Admin.
  if (payload.alert_type === "tamper_attempt") {
    return json({ event_id: event.event_id, sent: 0, failed: 0, super_admin_only: true });
  }

  const serviceClient = createClient(supabaseUrl, serviceRoleKey);
  const { data: tokens, error: tokenError } = await serviceClient
    .from("device_push_tokens")
    .select("fcm_token")
    .eq("account_id", event.account_id)
    .eq("app_role", "admin")
    .is("deleted_at", null);
  if (tokenError) {
    return json({ error: tokenError.message }, 500);
  }

  const uniqueTokens = Array.from(new Set((tokens ?? []).map((item: PushToken) => item.fcm_token).filter(Boolean)));
  if (uniqueTokens.length === 0) {
    return json({ event_id: event.event_id, sent: 0, failed: 0 });
  }
  let results: Array<{ ok: boolean; status: number; body: string }>;
  try {
    const projectId = requiredEnv("FCM_PROJECT_ID");
    const accessToken = await fcmAccessToken();
    results = await Promise.all(uniqueTokens.map((token) =>
      sendFcm(token, event, payload.device_id!, payload.alert_type!, projectId, accessToken)
    ));
  } catch (error) {
    const diagnostic = safeErrorMessage(error);
    console.error("FCM delivery setup failed", diagnostic);
    return json({ error: "Push delivery unavailable", diagnostic }, 500);
  }

  const failures = results.filter((result) => !result.ok);
  if (failures.length > 0) {
    console.error(
      "FCM delivery rejected",
      failures.map((result) => ({ status: result.status, error: safeFcmError(result.body) })),
    );
  }

  return json({
    event_id: event.event_id,
    sent: results.filter((result) => result.ok).length,
    failed: failures.length,
  });
});

async function sendFcm(
  token: string,
  event: AlertEvent,
  deviceId: string,
  alertType: string,
  projectId: string,
  accessToken: string,
): Promise<{ ok: boolean; status: number; body: string }> {
  const response = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${accessToken}`,
      "content-type": "application/json",
    },
    body: JSON.stringify({
      message: {
        token,
        android: {
          priority: "high",
        },
        data: {
          type: "protection_alert",
          event_id: event.event_id,
          device_id: deviceId,
          device_name: event.device_name,
          alert_type: alertType,
          title: event.title,
          body: event.body,
        },
      },
    }),
  });
  return { ok: response.ok, status: response.status, body: await response.text() };
}

async function fcmAccessToken(): Promise<string> {
  let serviceAccount: { client_email?: string; private_key?: string };
  try {
    serviceAccount = JSON.parse(requiredEnv("FCM_SERVICE_ACCOUNT_JSON"));
  } catch {
    throw new Error("FCM service account JSON is invalid");
  }
  if (!serviceAccount.client_email || !serviceAccount.private_key) {
    throw new Error("FCM service account is missing required fields");
  }
  const now = Math.floor(Date.now() / 1000);
  const jwtHeader = base64Url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const jwtClaim = base64Url(JSON.stringify({
    iss: serviceAccount.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }));
  const unsignedJwt = `${jwtHeader}.${jwtClaim}`;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToArrayBuffer(serviceAccount.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsignedJwt));
  const jwt = `${unsignedJwt}.${base64Url(signature)}`;
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });
  const body = await response.json();
  if (!response.ok || !body.access_token) {
    const reason = typeof body?.error === "string" ? body.error : `HTTP ${response.status}`;
    throw new Error(`Could not obtain FCM access token: ${reason}`);
  }
  return body.access_token;
}

function safeErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "Unknown error";
}

function safeFcmError(body: string): string {
  try {
    const parsed = JSON.parse(body);
    return parsed?.error?.status ?? parsed?.error?.message ?? "Unknown FCM error";
  } catch {
    return "Unreadable FCM response";
  }
}

function requiredEnv(name: string): string {
  const value = Deno.env.get(name);
  if (!value) throw new Error(`Missing ${name}`);
  return value;
}

function pemToArrayBuffer(pem: string): ArrayBuffer {
  const base64 = pem
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s/g, "");
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  return bytes.buffer;
}

function base64Url(value: string | ArrayBuffer): string {
  const bytes = typeof value === "string" ? new TextEncoder().encode(value) : new Uint8Array(value);
  let binary = "";
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return btoa(binary)
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}
