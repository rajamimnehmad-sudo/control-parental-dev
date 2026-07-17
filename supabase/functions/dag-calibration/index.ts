import { createClient } from "@supabase/supabase-js";

type Payload = {
  action?: "submit" | "current";
  device_id?: string;
  image_base64?: string;
  image_hash?: string;
  model_version?: string;
  initial_decision?: "blocked" | "uncertain";
  scores?: Record<string, number>;
};

const MaximumThumbnailBytes = 128 * 1024;
const MaximumDailyCasesPerDevice = 100;
const MaximumPendingCasesPerDevice = 250;
const AllowedScoreKeys = new Set([
  "professional", "legacy", "female_face", "female_breast_covered",
  "female_genitalia_covered", "buttocks_covered", "armpits_exposed",
  "belly_exposed", "explicit_region",
]);

Deno.serve(async (request) => {
  if (request.method !== "POST") return json({ error: "Método no permitido." }, 405);
  const payload = await request.json().catch(() => ({})) as Payload;
  const deviceId = payload.device_id?.trim() ?? "";
  if (!isUuid(deviceId)) return json({ error: "Dispositivo inválido." }, 400);

  const supabaseUrl = requiredEnv("SUPABASE_URL");
  const deviceToken = request.headers.get("x-device-token") ?? "";
  const serviceClient = createClient(supabaseUrl, requiredEnv("SUPABASE_SERVICE_ROLE_KEY"), {
    auth: { persistSession: false, autoRefreshToken: false },
    global: { headers: { "x-device-token": deviceToken } },
  });
  const { data: authorization, error: authorizationError } = await serviceClient.rpc(
    "dag_calibration_submission_authorized",
    { p_device_id: deviceId },
  );
  const communityId = Array.isArray(authorization) ? authorization[0]?.community_id : undefined;
  if (authorizationError || !isUuid(communityId ?? "")) {
    return json({ error: "Calibración DAG no está habilitada para este dispositivo." }, 403);
  }

  if (payload.action === "current") {
    const { data, error } = await serviceClient
      .from("dag_calibration_versions")
      .select("version_number,thresholds,model_version,activated_at")
      .eq("status", "active")
      .maybeSingle();
    if (error) return json({ error: "No se pudo consultar la calibración." }, 503);
    return json({ calibration: data ?? null }, 200);
  }

  if (payload.action !== "submit") return json({ error: "Acción inválida." }, 400);
  const modelVersion = payload.model_version?.trim() ?? "";
  const imageHash = payload.image_hash?.trim().toLowerCase() ?? "";
  const imageBytes = decodeBase64(payload.image_base64 ?? "");
  const scores = cleanScores(payload.scores);
  if (
    modelVersion.length < 1 || modelVersion.length > 120 ||
    !/^[0-9a-f]{64}$/.test(imageHash) ||
    !imageBytes || imageBytes.length > MaximumThumbnailBytes || !isJpeg(imageBytes) ||
    (payload.initial_decision !== "blocked" && payload.initial_decision !== "uncertain") ||
    !scores
  ) {
    return json({ error: "Caso de calibración inválido." }, 400);
  }
  if (await sha256(imageBytes) !== imageHash) return json({ error: "Miniatura inválida." }, 400);

  const oneDayAgo = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();
  const now = new Date().toISOString();
  const [dailyResult, pendingResult] = await Promise.all([
    serviceClient.from("dag_calibration_reviews").select("id", { count: "exact", head: true })
      .eq("device_id", deviceId).gte("created_at", oneDayAgo),
    serviceClient.from("dag_calibration_reviews").select("id", { count: "exact", head: true })
      .eq("device_id", deviceId).eq("status", "pending").gt("expires_at", now),
  ]);
  if (dailyResult.error || pendingResult.error) return json({ error: "No se pudo comprobar la capacidad de calibración." }, 503);
  if ((dailyResult.count ?? 0) >= MaximumDailyCasesPerDevice || (pendingResult.count ?? 0) >= MaximumPendingCasesPerDevice) {
    return json({ error: "La cola de Calibración DAG alcanzó su capacidad temporal." }, 429);
  }

  const modelKey = (await sha256(new TextEncoder().encode(modelVersion))).slice(0, 12);
  const storagePath = `${communityId}/${deviceId}/${imageHash}-${modelKey}.jpg`;
  const { error: uploadError } = await serviceClient.storage
    .from("dag-calibration")
    .upload(storagePath, imageBytes, { contentType: "image/jpeg", upsert: false });
  if (uploadError && !uploadError.message.toLowerCase().includes("already exists")) {
    return json({ error: "No se pudo guardar la miniatura privada." }, 503);
  }

  const { error: insertError } = await serviceClient.from("dag_calibration_reviews").upsert({
    community_id: communityId,
    device_id: deviceId,
    image_hash: imageHash,
    storage_path: storagePath,
    model_version: modelVersion,
    initial_decision: payload.initial_decision,
    scores,
  }, { onConflict: "device_id,image_hash,model_version", ignoreDuplicates: true });
  if (insertError) return json({ error: "No se pudo registrar el caso dudoso." }, 503);
  return json({ accepted: true }, 202);
});

function cleanScores(value: unknown): Record<string, number> | null {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const output: Record<string, number> = {};
  for (const [key, score] of Object.entries(value)) {
    if (!AllowedScoreKeys.has(key) || typeof score !== "number" || !Number.isFinite(score) || score < 0 || score > 1) {
      return null;
    }
    output[key] = Math.round(score * 10000) / 10000;
  }
  return Object.keys(output).length > 0 ? output : null;
}

function decodeBase64(value: string): Uint8Array | null {
  if (value.length < 4 || value.length > 180_000) return null;
  try {
    return Uint8Array.from(atob(value), (character) => character.charCodeAt(0));
  } catch {
    return null;
  }
}

function isJpeg(bytes: Uint8Array): boolean {
  return bytes.length >= 4 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes.at(-2) === 0xff && bytes.at(-1) === 0xd9;
}

async function sha256(bytes: Uint8Array): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return [...new Uint8Array(digest)].map((value) => value.toString(16).padStart(2, "0")).join("");
}

function isUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}

function requiredEnv(name: string): string {
  const value = Deno.env.get(name)?.trim();
  if (!value) throw new Error(`Missing ${name}`);
  return value;
}

function json(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store, max-age=0",
      "referrer-policy": "no-referrer",
      "x-content-type-options": "nosniff",
    },
  });
}
