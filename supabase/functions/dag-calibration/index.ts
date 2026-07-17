import { createClient } from "@supabase/supabase-js";

type Payload = {
  action?: "submit" | "current" | "clear";
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
const StrongDecisionMargin = 0.15;
const ClearPageSize = 500;
const StorageDeleteBatchSize = 1000;
const DatabaseUpdateBatchSize = 500;
const AllowedScoreKeys = new Set([
  "professional", "legacy", "female_face", "female_breast_covered",
  "female_genitalia_covered", "buttocks_covered", "armpits_exposed",
  "belly_exposed", "explicit_region",
]);

Deno.serve(async (request) => {
  if (request.method !== "POST") return json({ error: "Método no permitido." }, 405);
  const payload = await request.json().catch(() => ({})) as Payload;
  const supabaseUrl = requiredEnv("SUPABASE_URL");
  const serviceClient = createClient(supabaseUrl, requiredEnv("SUPABASE_SERVICE_ROLE_KEY"), {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  if (payload.action === "clear") return clearCalibrationQueue(request, supabaseUrl, serviceClient);

  const deviceId = payload.device_id?.trim() ?? "";
  if (!isUuid(deviceId)) return json({ error: "Dispositivo inválido." }, 400);
  const deviceToken = request.headers.get("x-device-token") ?? "";
  const deviceClient = createClient(supabaseUrl, requiredEnv("SUPABASE_SERVICE_ROLE_KEY"), {
    auth: { persistSession: false, autoRefreshToken: false },
    global: { headers: { "x-device-token": deviceToken } },
  });
  const { data: authorization, error: authorizationError } = await deviceClient.rpc(
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
    payload.initial_decision !== "uncertain" ||
    !scores
  ) {
    return json({ error: "Caso de calibración inválido." }, 400);
  }
  if (await sha256(imageBytes) !== imageHash) return json({ error: "Miniatura inválida." }, 400);

  const { data: activeCalibration, error: calibrationError } = await serviceClient
    .from("dag_calibration_versions")
    .select("thresholds")
    .eq("status", "active")
    .maybeSingle();
  if (calibrationError) return json({ error: "No se pudo comprobar la calibración activa." }, 503);
  if (isClearlyBlocked(scores, activeCalibration?.thresholds)) {
    return json({ accepted: false, reason: "clear_block" }, 202);
  }

  const oneDayAgo = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();
  const now = new Date().toISOString();
  const [dailyResult, pendingResult] = await Promise.all([
    serviceClient.from("dag_calibration_reviews").select("id", { count: "exact", head: true })
      .eq("device_id", deviceId).gte("created_at", oneDayAgo),
    serviceClient.from("dag_calibration_reviews").select("id", { count: "exact", head: true })
      .eq("device_id", deviceId).eq("status", "pending").is("archived_at", null).gt("expires_at", now),
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

  const { data: existingReview, error: existingReviewError } = await serviceClient
    .from("dag_calibration_reviews")
    .select("id,archived_at")
    .eq("device_id", deviceId)
    .eq("image_hash", imageHash)
    .eq("model_version", modelVersion)
    .single();
  if (existingReviewError) return json({ error: "No se pudo comprobar el caso dudoso." }, 503);
  if (existingReview.archived_at) {
    const { error: restoreError } = await serviceClient
      .from("dag_calibration_reviews")
      .update({
        community_id: communityId,
        storage_path: storagePath,
        initial_decision: "uncertain",
        scores,
        status: "pending",
        review_decision: null,
        review_reason: null,
        review_note: null,
        reviewed_by: null,
        reviewed_at: null,
        created_at: now,
        expires_at: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString(),
        archived_at: null,
        archived_by: null,
      })
      .eq("id", existingReview.id)
      .not("archived_at", "is", null);
    if (restoreError) return json({ error: "No se pudo reabrir el caso dudoso." }, 503);
  }
  return json({ accepted: true }, 202);
});

async function clearCalibrationQueue(
  request: Request,
  supabaseUrl: string,
  serviceClient: ReturnType<typeof createClient>,
): Promise<Response> {
  const authorization = request.headers.get("authorization") ?? "";
  const authenticated = createClient(supabaseUrl, requiredEnv("SUPABASE_ANON_KEY"), {
    auth: { persistSession: false, autoRefreshToken: false },
    global: { headers: { authorization } },
  });
  const [{ data: userData, error: userError }, { data: isSuperAdmin, error: roleError }] = await Promise.all([
    authenticated.auth.getUser(),
    authenticated.rpc("is_super_admin"),
  ]);
  const actorId = userData.user?.id;
  if (userError || roleError || !actorId || isSuperAdmin !== true) {
    return json({ error: "No autorizado para borrar Calibración DAG." }, 403);
  }

  const reviews: Array<{ id: string; storage_path: string }> = [];
  let cursor: string | null = null;
  for (;;) {
    let query = serviceClient
      .from("dag_calibration_reviews")
      .select("id,storage_path")
      .is("archived_at", null)
      .order("id", { ascending: true })
      .limit(ClearPageSize);
    if (cursor) query = query.gt("id", cursor);
    const { data, error } = await query;
    if (error) return json({ error: "No se pudo preparar el borrado de Calibración DAG." }, 503);
    reviews.push(...(data ?? []));
    if ((data?.length ?? 0) < ClearPageSize) break;
    cursor = data?.at(-1)?.id ?? null;
    if (!cursor) break;
  }
  if (reviews.length === 0) return json({ cleared: 0 }, 200);

  const storagePaths = [...new Set(reviews.map((review) => review.storage_path))];
  for (let offset = 0; offset < storagePaths.length; offset += StorageDeleteBatchSize) {
    const { error } = await serviceClient.storage
      .from("dag-calibration")
      .remove(storagePaths.slice(offset, offset + StorageDeleteBatchSize));
    if (error) return json({ error: "No se pudieron borrar todas las miniaturas privadas." }, 503);
  }

  const archivedAt = new Date().toISOString();
  for (let offset = 0; offset < reviews.length; offset += DatabaseUpdateBatchSize) {
    const ids = reviews.slice(offset, offset + DatabaseUpdateBatchSize).map((review) => review.id);
    const { error } = await serviceClient
      .from("dag_calibration_reviews")
      .update({ archived_at: archivedAt, archived_by: actorId })
      .in("id", ids)
      .is("archived_at", null);
    if (error) return json({ error: "Las fotos se borraron, pero no se pudo cerrar toda la cola." }, 503);
  }
  const { error: auditError } = await serviceClient.from("dag_calibration_audit").insert({
    actor_user_id: actorId,
    action: "reviews_cleared",
    details: { review_count: reviews.length, storage_object_count: storagePaths.length },
  });
  if (auditError) return json({ error: "La cola se vació, pero no se pudo registrar la acción." }, 503);
  return json({ cleared: reviews.length }, 200);
}

function isClearlyBlocked(
  scores: Record<string, number>,
  rawThresholds: unknown,
): boolean {
  const thresholds = scoreThresholds(rawThresholds);
  if ((scores.professional ?? 0) >= thresholds.professional_block) return true;
  if ((scores.legacy ?? 0) >= thresholds.professional_block) return true;
  if ((scores.explicit_region ?? 0) >= strong(thresholds.explicit_region)) return true;
  if ((scores.female_breast_covered ?? 0) >= strong(thresholds.female_breast_covered)) return true;
  if ((scores.female_genitalia_covered ?? 0) >= strong(thresholds.female_genitalia_covered)) return true;
  if ((scores.buttocks_covered ?? 0) >= strong(thresholds.buttocks_covered)) return true;
  const femaleContext = (scores.female_face ?? 0) >= thresholds.female_face;
  return femaleContext && (
    (scores.armpits_exposed ?? 0) >= strong(thresholds.armpits_exposed) ||
    (scores.belly_exposed ?? 0) >= strong(thresholds.belly_exposed)
  );
}

function scoreThresholds(value: unknown): Record<string, number> {
  const defaults: Record<string, number> = {
    professional_block: 0.65,
    female_face: 0.30,
    female_breast_covered: 0.18,
    female_genitalia_covered: 0.18,
    buttocks_covered: 0.18,
    armpits_exposed: 0.20,
    belly_exposed: 0.20,
    explicit_region: 0.20,
  };
  if (!value || typeof value !== "object" || Array.isArray(value)) return defaults;
  for (const key of Object.keys(defaults)) {
    const candidate = (value as Record<string, unknown>)[key];
    if (typeof candidate === "number" && Number.isFinite(candidate) && candidate >= 0 && candidate <= 1) {
      defaults[key] = candidate;
    }
  }
  return defaults;
}

function strong(threshold: number): number {
  return Math.min(1, threshold + StrongDecisionMargin);
}

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
