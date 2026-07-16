import { createClient } from "@supabase/supabase-js";

type Announcement = {
  announcement_id: string;
  community_id: string;
  target_role: "admin" | "user" | "all";
  title: string;
  body: string;
};

Deno.serve(async (request) => {
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);
  const payload = await request.json().catch(() => ({})) as { announcement_id?: string };
  if (!payload.announcement_id) return json({ error: "Missing announcement_id" }, 400);

  const url = requiredEnv("SUPABASE_URL");
  const anonKey = requiredEnv("SUPABASE_ANON_KEY");
  const authorization = request.headers.get("authorization") ?? "";
  const authenticated = createClient(url, anonKey, { global: { headers: { authorization } } });
  const { data, error } = await authenticated.rpc("super_admin_get_announcement_for_delivery", {
    target_announcement_id: payload.announcement_id,
  });
  if (error) return json({ error: error.message }, 403);
  const announcement = (data?.[0] ?? null) as Announcement | null;
  if (!announcement) return json({ error: "Announcement unavailable" }, 404);

  const service = createClient(url, requiredEnv("SUPABASE_SERVICE_ROLE_KEY"));
  const { data: accounts, error: accountError } = await service
    .from("accounts")
    .select("id")
    .eq("community_id", announcement.community_id);
  if (accountError) return json({ error: accountError.message }, 500);
  const accountIds = (accounts ?? []).map((item) => item.id as string);
  if (accountIds.length === 0) return json({ sent: 0, failed: 0 });

  let tokenQuery = service
    .from("device_push_tokens")
    .select("fcm_token")
    .in("account_id", accountIds)
    .is("deleted_at", null);
  if (announcement.target_role !== "all") tokenQuery = tokenQuery.eq("app_role", announcement.target_role);
  const { data: tokens, error: tokenError } = await tokenQuery;
  if (tokenError) return json({ error: tokenError.message }, 500);
  const uniqueTokens = Array.from(new Set((tokens ?? []).map((item) => item.fcm_token as string).filter(Boolean)));
  if (uniqueTokens.length === 0) return json({ sent: 0, failed: 0 });

  try {
    const accessToken = await fcmAccessToken();
    const projectId = requiredEnv("FCM_PROJECT_ID");
    const results = await Promise.all(uniqueTokens.map((token) => sendFcm(token, announcement, projectId, accessToken)));
    return json({ sent: results.filter(Boolean).length, failed: results.filter((ok) => !ok).length });
  } catch (error) {
    console.error("Announcement push unavailable", error instanceof Error ? error.message : "unknown");
    return json({ error: "Push delivery unavailable" }, 500);
  }
});

async function sendFcm(token: string, announcement: Announcement, projectId: string, accessToken: string) {
  const response = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
    method: "POST",
    headers: { authorization: `Bearer ${accessToken}`, "content-type": "application/json" },
    body: JSON.stringify({
      message: {
        token,
        android: { priority: "normal" },
        data: {
          type: "announcement",
          announcement_id: announcement.announcement_id,
          title: announcement.title,
          body: announcement.body,
        },
      },
    }),
  });
  if (!response.ok) console.error("Announcement FCM rejected", response.status);
  return response.ok;
}

async function fcmAccessToken(): Promise<string> {
  const account = JSON.parse(requiredEnv("FCM_SERVICE_ACCOUNT_JSON")) as {
    client_email?: string;
    private_key?: string;
  };
  if (!account.client_email || !account.private_key) throw new Error("Invalid FCM service account");
  const now = Math.floor(Date.now() / 1000);
  const header = base64Url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claim = base64Url(JSON.stringify({
    iss: account.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }));
  const unsigned = `${header}.${claim}`;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemBytes(account.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned));
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: `${unsigned}.${base64Url(signature)}`,
    }),
  });
  const body = await response.json();
  if (!response.ok || !body.access_token) throw new Error("FCM authorization failed");
  return body.access_token as string;
}

function pemBytes(pem: string): ArrayBuffer {
  const value = pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replace(/\s/g, "");
  return Uint8Array.from(atob(value), (character) => character.charCodeAt(0)).buffer;
}

function base64Url(value: string | ArrayBuffer): string {
  const bytes = typeof value === "string" ? new TextEncoder().encode(value) : new Uint8Array(value);
  let binary = "";
  bytes.forEach((byte) => binary += String.fromCharCode(byte));
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function requiredEnv(name: string): string {
  const value = Deno.env.get(name);
  if (!value) throw new Error(`Missing ${name}`);
  return value;
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}
