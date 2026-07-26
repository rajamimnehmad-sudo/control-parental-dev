import { createClient } from "@supabase/supabase-js";
import {
  completeRegisteredSample,
  containsForbiddenPayloadFields,
  jpegDimensions,
  MaximumNormalizedBytes,
  parseFields,
  sha256Hex,
  storagePath,
} from "./logic.ts";

Deno.serve(async (request) => {
  if (request.method !== "POST") {
    return json({ error: "Método no permitido." }, 405);
  }
  const contentType = request.headers.get("content-type") ?? "";
  if (!contentType.toLowerCase().startsWith("multipart/form-data")) {
    return json({ error: "Formato inválido." }, 415);
  }
  const form = await request.formData().catch(() => null);
  if (!form || containsForbiddenPayloadFields(form)) {
    return json({ error: "Solicitud inválida." }, 400);
  }
  const fields = parseFields(form);
  if (!fields) return json({ error: "Evidencia inválida." }, 400);

  const supabaseUrl = requiredEnv("SUPABASE_URL");
  const serviceRoleKey = requiredEnv("SUPABASE_SERVICE_ROLE_KEY");
  const deviceToken = request.headers.get("x-device-token")?.trim() ?? "";
  if (!deviceToken) return json({ error: "Dispositivo no autorizado." }, 403);
  const serviceClient = createClient(supabaseUrl, serviceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false },
    global: { headers: { "x-device-token": deviceToken } },
  });
  const reviewerKey = await sha256Hex(
    new TextEncoder().encode(
      `dag-v2-reviewer:${fields.deviceId}:${fields.policyVersion}`,
    ),
  );
  const { data: authorizationRows, error: authorizationError } =
    await serviceClient.rpc("dag_v2_calibration_authorize_and_consume", {
      p_device_id: fields.deviceId,
      p_reviewer_key: reviewerKey,
    });
  const authorization = Array.isArray(authorizationRows)
    ? authorizationRows[0]?.authorization
    : undefined;
  if (authorizationError || authorization === "unauthorized") {
    return json({ error: "Calibración no autorizada." }, 403);
  }
  if (authorization !== "allowed") {
    return json({ error: "Límite temporal alcanzado." }, 429);
  }

  const fileValue = form.get("sample");
  const sampleFile = fileValue instanceof File ? fileValue : null;
  if (fields.existingContentSha256 !== null && sampleFile !== null) {
    return json({ error: "Evidencia ambigua." }, 400);
  }
  if (fields.existingContentSha256 === null && sampleFile === null) {
    return json({ error: "Falta la muestra normalizada." }, 400);
  }

  let sampleId = "";
  let deduplicated = false;
  if (fields.existingContentSha256 !== null) {
    const { data: existing, error } = await serviceClient
      .from("dag_v2_calibration_samples")
      .select("sample_id,status")
      .eq("content_sha256", fields.existingContentSha256)
      .eq("status", "ready")
      .maybeSingle();
    if (error) return json({ error: "No se pudo deduplicar la muestra." }, 503);
    if (!existing) {
      return json({ error: "La muestra equivalente no está disponible." }, 409);
    }
    sampleId = existing.sample_id;
    deduplicated = true;
    await serviceClient.from("dag_v2_calibration_audit").insert({
      sample_id: sampleId,
      reviewer_key: reviewerKey,
      action: "sample_deduplicated",
      details: { kind: "local_reference" },
    });
  } else {
    if (
      sampleFile!.type !== "image/jpeg" ||
      sampleFile!.size < 1 ||
      sampleFile!.size > MaximumNormalizedBytes ||
      sampleFile!.size !== fields.sizeBytes
    ) {
      return json({ error: "Archivo normalizado inválido." }, 400);
    }
    const bytes = new Uint8Array(await sampleFile!.arrayBuffer());
    const dimensions = jpegDimensions(bytes);
    const actualSha = await sha256Hex(bytes);
    if (
      !dimensions ||
      dimensions.width !== fields.width ||
      dimensions.height !== fields.height ||
      actualSha !== fields.contentSha256
    ) {
      bytes.fill(0);
      return json({ error: "Integridad de muestra inválida." }, 400);
    }
    const path = storagePath(fields.contentSha256);
    const { data: registeredRows, error: registerError } = await serviceClient
      .rpc("dag_v2_calibration_register_sample", {
        p_content_sha256: fields.contentSha256,
        p_perceptual_hash: fields.perceptualHash,
        p_storage_path: path,
        p_width: fields.width,
        p_height: fields.height,
        p_mime_type: fields.mimeType,
        p_size_bytes: fields.sizeBytes,
        p_source_kind: fields.sourceKind,
        p_source_host: fields.sourceHost,
        p_document_host: fields.documentHost,
        p_source_url_hash: fields.sourceUrlHash,
        p_policy_version: fields.policyVersion,
        p_collector_version: fields.collectorVersion,
        p_reviewer_key: reviewerKey,
      });
    const registered = Array.isArray(registeredRows)
      ? registeredRows[0]
      : undefined;
    if (registerError || !registered?.sample_id) {
      bytes.fill(0);
      return json({ error: "No se pudo registrar la muestra." }, 503);
    }
    sampleId = registered.sample_id;
    const completion = await completeRegisteredSample(
      String(registered.match_kind ?? ""),
      async () => {
        const { error } = await serviceClient.storage
          .from("dag-v2-calibration")
          .upload(registered.canonical_storage_path, bytes, {
            contentType: "image/jpeg",
            upsert: false,
          });
        return error;
      },
      async () => {
        const { error } = await serviceClient.rpc(
          "dag_v2_calibration_mark_sample",
          {
            p_sample_id: sampleId,
            p_status: "ready",
            p_reviewer_key: reviewerKey,
          },
        );
        return !error;
      },
    );
    bytes.fill(0);
    if (!completion.accepted) {
      const message = completion.stage === "storage"
        ? "No se pudo guardar la muestra privada."
        : completion.stage === "ready"
        ? "No se pudo finalizar la muestra."
        : "Estado de muestra inválido.";
      return json({ error: message }, 503);
    }
    deduplicated = completion.deduplicated;
    if (deduplicated) {
      const { data: existing, error } = await serviceClient
        .from("dag_v2_calibration_samples")
        .select("status")
        .eq("sample_id", sampleId)
        .single();
      if (error || existing?.status !== "ready") {
        return json(
          { error: "La muestra equivalente todavía no está lista." },
          409,
        );
      }
    }
  }

  const { data: labelRows, error: labelError } = await serviceClient.rpc(
    "dag_v2_calibration_upsert_label",
    {
      p_sample_id: sampleId,
      p_review_decision: fields.reviewDecision,
      p_reviewer_key: reviewerKey,
      p_policy_version: fields.policyVersion,
    },
  );
  if (labelError) {
    return json({ error: "No se pudo registrar la etiqueta." }, 503);
  }
  return json({
    accepted: true,
    sample_id: sampleId,
    deduplicated,
    audit_recorded: Array.isArray(labelRows) &&
      labelRows[0]?.audit_recorded === true,
  }, 202);
});

function requiredEnv(name: string): string {
  const value = Deno.env.get(name)?.trim() ?? "";
  if (!value) throw new Error(`Missing ${name}`);
  return value;
}

function json(payload: Record<string, unknown>, status: number): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      "x-content-type-options": "nosniff",
    },
  });
}
