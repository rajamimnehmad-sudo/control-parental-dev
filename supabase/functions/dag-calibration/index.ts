import { createClient } from "@supabase/supabase-js";

type Payload = {
  action?:
    | "submit"
    | "submit_manual_block"
    | "submit_manual_blur_review"
    | "submit_binary_label"
    | "current"
    | "clear";
  device_id?: string;
  image_base64?: string;
  image_hash?: string;
  model_version?: string;
  initial_decision?: "allowed" | "blocked" | "uncertain";
  scores?: Record<string, number>;
  signals?: string[];
  calibration_version?: number;
  review_decision?: "allow" | "block";
};

const MaximumThumbnailBytes = 128 * 1024;
const MaximumDailyCasesPerDevice = 100;
const MaximumPendingCasesPerDevice = 250;
const StrongDecisionMargin = 0.15;
const ClearPageSize = 500;
const StorageDeleteBatchSize = 1000;
const DatabaseUpdateBatchSize = 500;
const AllowedScoreKeys = new Set([
  "professional",
  "legacy",
  "female_face",
  "male_face",
  "male_breast_exposed",
  "female_breast_covered",
  "female_genitalia_covered",
  "buttocks_covered",
  "armpits_exposed",
  "belly_exposed",
  "explicit_region",
  "sleeves_above_elbow",
  "hem_above_knee",
]);
const AllowedSignals = new Set([
  "professional_content",
  "legacy_content",
  "female_breast_covered",
  "female_genitalia_covered",
  "buttocks_covered",
  "armpits_exposed",
  "belly_exposed",
  "explicit_region",
  "sleeves_above_elbow",
  "hem_above_knee",
  "uncertain_content",
]);

Deno.serve(async (request) => {
  if (request.method !== "POST") {
    return json({ error: "Método no permitido." }, 405);
  }
  const payload = await request.json().catch(() => ({})) as Payload;
  const supabaseUrl = requiredEnv("SUPABASE_URL");
  const serviceClient = createClient(
    supabaseUrl,
    requiredEnv("SUPABASE_SERVICE_ROLE_KEY"),
    {
      auth: { persistSession: false, autoRefreshToken: false },
    },
  );
  if (payload.action === "clear") {
    return clearCalibrationQueue(request, serviceClient);
  }

  const deviceId = payload.device_id?.trim() ?? "";
  if (!isUuid(deviceId)) return json({ error: "Dispositivo inválido." }, 400);
  const deviceToken = request.headers.get("x-device-token") ?? "";
  const deviceClient = createClient(
    supabaseUrl,
    requiredEnv("SUPABASE_SERVICE_ROLE_KEY"),
    {
      auth: { persistSession: false, autoRefreshToken: false },
      global: { headers: { "x-device-token": deviceToken } },
    },
  );
  const { data: authorization, error: authorizationError } = await deviceClient
    .rpc(
      "dag_calibration_submission_authorized",
      { p_device_id: deviceId },
    );
  const communityId = Array.isArray(authorization)
    ? authorization[0]?.community_id
    : undefined;
  if (authorizationError || !isUuid(communityId ?? "")) {
    return json({
      error: "Calibración DAG no está habilitada para este dispositivo.",
    }, 403);
  }

  if (payload.action === "current") {
    const modelVersion = payload.model_version?.trim() ?? "";
    if (modelVersion.length < 1 || modelVersion.length > 120) {
      return json({ error: "Modelo inválido." }, 400);
    }
    const { data, error } = await serviceClient
      .from("dag_calibration_versions")
      .select("id,version_number,thresholds,model_version,activated_at")
      .eq("status", "active")
      .eq("model_version", modelVersion)
      .maybeSingle();
    if (error) {
      return json({ error: "No se pudo consultar la calibración." }, 503);
    }
    if (!data) return json({ calibration: null }, 200);
    return json({
      calibration: {
        version_number: data.version_number,
        thresholds: data.thresholds,
        model_version: data.model_version,
        activated_at: data.activated_at,
      },
    }, 200);
  }

  const manualBlockSubmission = payload.action === "submit_manual_block";
  const manualBlurReviewSubmission =
    payload.action === "submit_manual_blur_review";
  const binarySubmission = payload.action === "submit_binary_label";
  const manualSubmission = manualBlockSubmission ||
    manualBlurReviewSubmission || binarySubmission;
  const manualSubmissionSource = binarySubmission
    ? "manual_dag_binary"
    : manualBlurReviewSubmission
    ? "manual_dag_false_positive"
    : "manual_dag";
  if (payload.action !== "submit" && !manualSubmission) {
    return json({ error: "Acción inválida." }, 400);
  }
  const modelVersion = payload.model_version?.trim() ?? "";
  const imageHash = payload.image_hash?.trim().toLowerCase() ?? "";
  const imageBytes = decodeBase64(payload.image_base64 ?? "");
  const scores = cleanScores(payload.scores);
  const signals = cleanSignals(payload.signals);
  const calibrationVersion =
    Number.isSafeInteger(payload.calibration_version) &&
      (payload.calibration_version ?? 0) >= 0
      ? payload.calibration_version ?? 0
      : -1;
  if (
    modelVersion.length < 1 || modelVersion.length > 120 ||
    !/^[0-9a-f]{64}$/.test(imageHash) ||
    !imageBytes || imageBytes.length > MaximumThumbnailBytes ||
    !isJpeg(imageBytes) ||
    (!manualSubmission && payload.initial_decision !== "uncertain") ||
    (manualSubmission &&
      !["allowed", "blocked", "uncertain"].includes(
        payload.initial_decision ?? "",
      )) ||
    (binarySubmission &&
      !["allow", "block"].includes(payload.review_decision ?? "")) ||
    !scores || !signals || calibrationVersion < 0
  ) {
    return json({ error: "Caso de calibración inválido." }, 400);
  }
  if (await sha256(imageBytes) !== imageHash) {
    return json({ error: "Miniatura inválida." }, 400);
  }

  const { data: activeCalibration, error: calibrationError } =
    await serviceClient
      .from("dag_calibration_versions")
      .select("thresholds")
      .eq("status", "active")
      .eq("model_version", modelVersion)
      .maybeSingle();
  if (calibrationError) {
    return json({ error: "No se pudo comprobar la calibración activa." }, 503);
  }
  if (
    !manualSubmission && isClearlyBlocked(scores, activeCalibration?.thresholds)
  ) {
    return json({ accepted: false, reason: "clear_block" }, 202);
  }

  const oneDayAgo = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();
  const now = new Date().toISOString();
  const [dailyResult, pendingResult] = await Promise.all([
    serviceClient.from("dag_calibration_reviews").select("id", {
      count: "exact",
      head: true,
    })
      .eq("device_id", deviceId).gte("created_at", oneDayAgo),
    serviceClient.from("dag_calibration_reviews").select("id", {
      count: "exact",
      head: true,
    })
      .eq("device_id", deviceId).eq("status", "pending").is("archived_at", null)
      .gt("expires_at", now),
  ]);
  if (dailyResult.error || pendingResult.error) {
    return json(
      { error: "No se pudo comprobar la capacidad de calibración." },
      503,
    );
  }
  if (
    (dailyResult.count ?? 0) >= MaximumDailyCasesPerDevice ||
    (pendingResult.count ?? 0) >= MaximumPendingCasesPerDevice
  ) {
    return json({
      error: "La cola de Calibración DAG alcanzó su capacidad temporal.",
    }, 429);
  }

  const modelKey = (await sha256(new TextEncoder().encode(modelVersion))).slice(
    0,
    12,
  );
  const storagePath = `${communityId}/${deviceId}/${imageHash}-${modelKey}.jpg`;
  const { error: uploadError } = await serviceClient.storage
    .from("dag-calibration")
    .upload(storagePath, imageBytes, {
      contentType: "image/jpeg",
      upsert: false,
    });
  if (
    uploadError && !uploadError.message.toLowerCase().includes("already exists")
  ) {
    return json({ error: "No se pudo guardar la miniatura privada." }, 503);
  }

  const { error: insertError } = await serviceClient.from(
    "dag_calibration_reviews",
  ).upsert({
    community_id: communityId,
    device_id: deviceId,
    image_hash: imageHash,
    storage_path: storagePath,
    model_version: modelVersion,
    initial_decision: payload.initial_decision,
    scores,
    signals,
    classification_calibration_version: calibrationVersion,
    submission_source: manualSubmission
      ? manualSubmissionSource
      : "automatic_uncertainty",
  }, {
    onConflict: "device_id,image_hash,model_version",
    ignoreDuplicates: true,
  });
  if (insertError) {
    return json({ error: "No se pudo registrar el caso dudoso." }, 503);
  }

  const { data: existingReview, error: existingReviewError } =
    await serviceClient
      .from("dag_calibration_reviews")
      .select("id,archived_at")
      .eq("device_id", deviceId)
      .eq("image_hash", imageHash)
      .eq("model_version", modelVersion)
      .single();
  if (existingReviewError) {
    return json({ error: "No se pudo comprobar el caso dudoso." }, 503);
  }
  if (manualSubmission) {
    const binaryReview = binarySubmission
      ? {
        status: "reviewed",
        review_decision: payload.review_decision,
        review_reason: null,
        reviewed_at: now,
      }
      : {
        status: "pending",
        review_decision: null,
        review_reason: null,
        reviewed_at: null,
      };
    const { error: markError } = await serviceClient
      .from("dag_calibration_reviews")
      .update({
        community_id: communityId,
        storage_path: storagePath,
        initial_decision: payload.initial_decision,
        scores,
        signals,
        classification_calibration_version: calibrationVersion,
        submission_source: manualSubmissionSource,
        ...binaryReview,
        review_note: null,
        reviewed_by: null,
        created_at: now,
        expires_at: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000)
          .toISOString(),
        archived_at: null,
        archived_by: null,
      })
      .eq("id", existingReview.id);
    if (markError) {
      return json({ error: "No se pudo registrar la marcación manual." }, 503);
    }
    const { error: auditError } = await serviceClient.from(
      "dag_calibration_audit",
    ).insert({
      action: "manual_image_reported",
      review_id: existingReview.id,
      details: {
        device_id: deviceId,
        initial_decision: payload.initial_decision,
        model_version: modelVersion,
        calibration_version: calibrationVersion,
        signals,
        requested_outcome: binarySubmission
          ? payload.review_decision
          : manualBlockSubmission
          ? "block"
          : "review_false_positive",
        review_decision: binarySubmission ? payload.review_decision : null,
      },
    });
    if (auditError) {
      return json({
        error: "La foto se marcó, pero no se pudo registrar la auditoría.",
      }, 503);
    }
  } else if (existingReview.archived_at) {
    const { error: restoreError } = await serviceClient
      .from("dag_calibration_reviews")
      .update({
        community_id: communityId,
        storage_path: storagePath,
        initial_decision: "uncertain",
        scores,
        signals,
        classification_calibration_version: calibrationVersion,
        submission_source: "automatic_uncertainty",
        status: "pending",
        review_decision: null,
        review_reason: null,
        review_note: null,
        reviewed_by: null,
        reviewed_at: null,
        created_at: now,
        expires_at: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000)
          .toISOString(),
        archived_at: null,
        archived_by: null,
      })
      .eq("id", existingReview.id)
      .not("archived_at", "is", null);
    if (restoreError) {
      return json({ error: "No se pudo reabrir el caso dudoso." }, 503);
    }
  }
  if (binarySubmission) {
    await maybeCreateCalibrationCandidate(serviceClient, modelVersion);
  }
  return json({ accepted: true }, 202);
});

async function clearCalibrationQueue(
  request: Request,
  serviceClient: ReturnType<typeof createClient<any>>,
): Promise<Response> {
  const jwt = bearerToken(request.headers.get("authorization"));
  if (!jwt) {
    return json({ error: "La sesión de Super Admin no es válida." }, 401);
  }
  const { data: userData, error: userError } = await serviceClient.auth.getUser(
    jwt,
  );
  const actorId = userData.user?.id;
  if (userError || !actorId) {
    return json({ error: "La sesión de Super Admin expiró." }, 401);
  }

  const { data: superAdmin, error: roleError } = await serviceClient
    .from("super_admins")
    .select("user_id")
    .eq("user_id", actorId)
    .eq("enabled", true)
    .is("deleted_at", null)
    .maybeSingle();
  if (roleError) {
    return json(
      { error: "No se pudo comprobar el permiso de Super Admin." },
      503,
    );
  }
  if (!superAdmin) {
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
    if (error) {
      return json({
        error: "No se pudo preparar el borrado de Calibración DAG.",
      }, 503);
    }
    reviews.push(...(data ?? []));
    if ((data?.length ?? 0) < ClearPageSize) break;
    cursor = data?.at(-1)?.id ?? null;
    if (!cursor) break;
  }
  if (reviews.length === 0) return json({ cleared: 0 }, 200);

  const storagePaths = [
    ...new Set(reviews.map((review) => review.storage_path)),
  ];
  for (
    let offset = 0;
    offset < storagePaths.length;
    offset += StorageDeleteBatchSize
  ) {
    const { error } = await serviceClient.storage
      .from("dag-calibration")
      .remove(storagePaths.slice(offset, offset + StorageDeleteBatchSize));
    if (error) {
      return json({
        error: "No se pudieron borrar todas las miniaturas privadas.",
      }, 503);
    }
  }

  const archivedAt = new Date().toISOString();
  for (
    let offset = 0;
    offset < reviews.length;
    offset += DatabaseUpdateBatchSize
  ) {
    const ids = reviews.slice(offset, offset + DatabaseUpdateBatchSize).map((
      review,
    ) => review.id);
    const { error } = await serviceClient
      .from("dag_calibration_reviews")
      .update({ archived_at: archivedAt, archived_by: actorId })
      .in("id", ids)
      .is("archived_at", null);
    if (error) {
      return json({
        error: "Las fotos se borraron, pero no se pudo cerrar toda la cola.",
      }, 503);
    }
  }
  const { error: auditError } = await serviceClient.from(
    "dag_calibration_audit",
  ).insert({
    actor_user_id: actorId,
    action: "reviews_cleared",
    details: {
      review_count: reviews.length,
      storage_object_count: storagePaths.length,
    },
  });
  if (auditError) {
    return json({
      error: "La cola se vació, pero no se pudo registrar la acción.",
    }, 503);
  }
  return json({ cleared: reviews.length }, 200);
}

type CalibrationReview = {
  id: string;
  review_decision: "allow" | "block";
  scores: Record<string, number>;
  signals: string[];
};

async function maybeCreateCalibrationCandidate(
  serviceClient: ReturnType<typeof createClient<any>>,
  modelVersion: string,
): Promise<void> {
  const [
    { data: reviewsData, error: reviewsError },
    { data: activeData, error: activeError },
  ] = await Promise.all([
    serviceClient
      .from("dag_calibration_reviews")
      .select("id,review_decision,scores,signals")
      .eq("status", "reviewed")
      .eq("model_version", modelVersion)
      .is("archived_at", null)
      .order("created_at", { ascending: true })
      .limit(500),
    serviceClient
      .from("dag_calibration_versions")
      .select("thresholds")
      .eq("status", "active")
      .eq("model_version", modelVersion)
      .maybeSingle(),
  ]);
  if (reviewsError || activeError) return;
  const reviews = (reviewsData ?? []).filter((
    review,
  ): review is CalibrationReview =>
    (review.review_decision === "allow" ||
      review.review_decision === "block") &&
    review.scores !== null && typeof review.scores === "object" &&
    Array.isArray(review.signals)
  );
  const allowCount =
    reviews.filter((review) => review.review_decision === "allow").length;
  const blockCount = reviews.length - allowCount;
  if (reviews.length < 40 || allowCount < 10 || blockCount < 10) return;

  const fallback = scoreThresholds(activeData?.thresholds);
  const { training, validation } = calibrationSplit(reviews);
  const professionalBlock = boundedBestThreshold(
    training,
    "professional",
    fallback.professional_block,
    0.35,
    0.90,
  );
  const thresholds = {
    professional_safe: round4(Math.max(0.05, professionalBlock - 0.15)),
    professional_block: professionalBlock,
    female_face: boundedBestThreshold(
      training,
      "female_face",
      fallback.female_face,
      0.12,
      0.65,
    ),
    male_face: fallback.male_face,
    male_breast_exposed: fallback.male_breast_exposed,
    female_breast_covered: boundedSignalThreshold(
      training,
      "female_breast_covered",
      fallback.female_breast_covered,
      0.08,
      0.65,
    ),
    female_genitalia_covered: boundedSignalThreshold(
      training,
      "female_genitalia_covered",
      fallback.female_genitalia_covered,
      0.08,
      0.65,
    ),
    buttocks_covered: boundedSignalThreshold(
      training,
      "buttocks_covered",
      fallback.buttocks_covered,
      0.08,
      0.65,
    ),
    armpits_exposed: boundedSignalThreshold(
      training,
      "armpits_exposed",
      fallback.armpits_exposed,
      0.08,
      0.65,
    ),
    belly_exposed: boundedSignalThreshold(
      training,
      "belly_exposed",
      fallback.belly_exposed,
      0.08,
      0.65,
    ),
    explicit_region: boundedSignalThreshold(
      training,
      "explicit_region",
      fallback.explicit_region,
      0.08,
      0.65,
    ),
    sleeves_above_elbow: boundedSignalThreshold(
      training,
      "sleeves_above_elbow",
      fallback.sleeves_above_elbow,
      0.45,
      0.95,
    ),
    hem_above_knee: boundedSignalThreshold(
      training,
      "hem_above_knee",
      fallback.hem_above_knee,
      0.45,
      0.95,
    ),
  };
  const trainingMetrics = evaluateCalibration(training, thresholds);
  const validationMetrics = evaluateCalibration(validation, thresholds);
  await serviceClient.rpc("dag_create_automatic_calibration_candidate", {
    new_thresholds: thresholds,
    new_metrics: {
      reviewed: reviews.length,
      allowed: allowCount,
      blocked: blockCount,
      training_examples: training.length,
      validation_examples: validation.length,
      false_positive: trainingMetrics.falsePositive,
      false_negative: trainingMetrics.falseNegative,
      weighted_error: trainingMetrics.weightedError,
      validation_false_positive: validationMetrics.falsePositive,
      validation_false_negative: validationMetrics.falseNegative,
      validation_weighted_error: validationMetrics.weightedError,
    },
    calibration_model_version: modelVersion,
    calibration_explanation:
      "Evaluación automática por etiquetas binarias. Esta propuesta no cambia los teléfonos hasta que un Super Admin la active manualmente.",
  });
}

function calibrationSplit(
  reviews: CalibrationReview[],
): { training: CalibrationReview[]; validation: CalibrationReview[] } {
  const validationIds = new Set<string>();
  for (const decision of ["allow", "block"] as const) {
    const group = reviews.filter((review) =>
      review.review_decision === decision
    ).sort((a, b) => a.id.localeCompare(b.id));
    const validationCount = Math.max(1, Math.floor(group.length * 0.2));
    group.slice(0, validationCount).forEach((review) =>
      validationIds.add(review.id)
    );
  }
  return {
    training: reviews.filter((review) => !validationIds.has(review.id)),
    validation: reviews.filter((review) => validationIds.has(review.id)),
  };
}

function boundedSignalThreshold(
  reviews: CalibrationReview[],
  key: string,
  fallback: number,
  minimum: number,
  maximum: number,
): number {
  const relevant = reviews.filter((review) =>
    review.review_decision === "allow" || review.signals.includes(key) ||
    review.signals.includes("uncertain_content")
  );
  return boundedBestThreshold(relevant, key, fallback, minimum, maximum);
}

function boundedBestThreshold(
  reviews: CalibrationReview[],
  key: string,
  fallback: number,
  minimum: number,
  maximum: number,
): number {
  const samples = reviews.flatMap((review) => {
    const score = review.scores[key];
    return typeof score === "number" && Number.isFinite(score)
      ? [{ score, decision: review.review_decision }]
      : [];
  });
  if (samples.length < 6) {
    return round4(Math.max(minimum, Math.min(maximum, fallback)));
  }
  const candidates = [
    ...new Set([
      minimum,
      maximum,
      ...samples.map((sample) => round4(sample.score)),
    ]),
  ]
    .filter((value) => value >= minimum && value <= maximum)
    .sort((a, b) => a - b);
  const best = candidates.map((threshold) => {
    const falsePositive = samples.filter((sample) =>
      sample.decision === "allow" && sample.score >= threshold
    ).length;
    const falseNegative = samples.filter((sample) =>
      sample.decision === "block" && sample.score < threshold
    ).length;
    return {
      threshold,
      falsePositive,
      falseNegative,
      weightedError: falsePositive + falseNegative * 3,
    };
  }).sort((left, right) =>
    left.weightedError - right.weightedError ||
    left.falseNegative - right.falseNegative ||
    left.threshold - right.threshold
  )[0];
  return round4(best?.threshold ?? fallback);
}

function evaluateCalibration(
  reviews: CalibrationReview[],
  thresholds: Record<string, number>,
): { falsePositive: number; falseNegative: number; weightedError: number } {
  const falsePositive =
    reviews.filter((review) =>
      review.review_decision === "allow" &&
      imageScoresRequireBlur(review.scores, thresholds)
    ).length;
  const falseNegative =
    reviews.filter((review) =>
      review.review_decision === "block" &&
      !imageScoresRequireBlur(review.scores, thresholds)
    ).length;
  return {
    falsePositive,
    falseNegative,
    weightedError: falsePositive + falseNegative * 3,
  };
}

function imageScoresRequireBlur(
  scores: Record<string, number>,
  thresholds: Record<string, number>,
): boolean {
  if ((scores.professional ?? 0) >= thresholds.professional_block) return true;
  if ((scores.legacy ?? 0) >= thresholds.professional_block) return true;
  for (
    const key of [
      "female_breast_covered",
      "female_genitalia_covered",
      "buttocks_covered",
      "explicit_region",
    ]
  ) {
    if ((scores[key] ?? 0) >= thresholds[key]) return true;
  }
  const femaleContext = (scores.female_face ?? 0) >= thresholds.female_face;
  return femaleContext &&
    [
      "armpits_exposed",
      "belly_exposed",
      "sleeves_above_elbow",
      "hem_above_knee",
    ]
      .some((key) => (scores[key] ?? 0) >= thresholds[key]);
}

function round4(value: number): number {
  return Math.round(Math.max(0, Math.min(1, value)) * 10000) / 10000;
}

function bearerToken(authorization: string | null): string | null {
  if (!authorization?.startsWith("Bearer ")) return null;
  const token = authorization.slice("Bearer ".length).trim();
  return token.length > 0 ? token : null;
}

function isClearlyBlocked(
  scores: Record<string, number>,
  rawThresholds: unknown,
): boolean {
  const thresholds = scoreThresholds(rawThresholds);
  if ((scores.professional ?? 0) >= thresholds.professional_block) return true;
  if ((scores.legacy ?? 0) >= thresholds.professional_block) return true;
  if ((scores.explicit_region ?? 0) >= strong(thresholds.explicit_region)) {
    return true;
  }
  if (
    (scores.female_breast_covered ?? 0) >=
      strong(thresholds.female_breast_covered)
  ) return true;
  if (
    (scores.female_genitalia_covered ?? 0) >=
      strong(thresholds.female_genitalia_covered)
  ) return true;
  if ((scores.buttocks_covered ?? 0) >= strong(thresholds.buttocks_covered)) {
    return true;
  }
  const femaleContext = (scores.female_face ?? 0) >= thresholds.female_face;
  return femaleContext && (
    (scores.armpits_exposed ?? 0) >= strong(thresholds.armpits_exposed) ||
    (scores.belly_exposed ?? 0) >= strong(thresholds.belly_exposed) ||
    (scores.sleeves_above_elbow ?? 0) >=
      strong(thresholds.sleeves_above_elbow) ||
    (scores.hem_above_knee ?? 0) >= strong(thresholds.hem_above_knee)
  );
}

function scoreThresholds(value: unknown): Record<string, number> {
  const defaults: Record<string, number> = {
    professional_safe: 0.15,
    professional_block: 0.65,
    female_face: 0.30,
    male_face: 0.30,
    male_breast_exposed: 0.55,
    female_breast_covered: 0.18,
    female_genitalia_covered: 0.18,
    buttocks_covered: 0.18,
    armpits_exposed: 0.20,
    belly_exposed: 0.20,
    explicit_region: 0.20,
    sleeves_above_elbow: 0.72,
    hem_above_knee: 0.72,
  };
  const bounds: Record<string, [number, number]> = {
    professional_safe: [0.05, 0.75],
    professional_block: [0.35, 0.90],
    female_face: [0.12, 0.65],
    male_face: [0.12, 0.65],
    male_breast_exposed: [0.25, 0.90],
    female_breast_covered: [0.08, 0.65],
    female_genitalia_covered: [0.08, 0.65],
    buttocks_covered: [0.08, 0.65],
    armpits_exposed: [0.08, 0.65],
    belly_exposed: [0.08, 0.65],
    explicit_region: [0.08, 0.65],
    sleeves_above_elbow: [0.45, 0.95],
    hem_above_knee: [0.45, 0.95],
  };
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return defaults;
  }
  for (const key of Object.keys(defaults)) {
    const candidate = (value as Record<string, unknown>)[key];
    if (
      typeof candidate === "number" && Number.isFinite(candidate) &&
      candidate >= 0 && candidate <= 1
    ) {
      const [minimum, maximum] = bounds[key];
      defaults[key] = Math.min(maximum, Math.max(minimum, candidate));
    }
  }
  return defaults;
}

function cleanSignals(value: unknown): string[] | null {
  if (value === undefined) return [];
  if (!Array.isArray(value) || value.length > 16) return null;
  const signals = [
    ...new Set(
      value.filter((item): item is string => typeof item === "string"),
    ),
  ];
  return signals.length === value.length &&
      signals.every((item) => AllowedSignals.has(item))
    ? signals
    : null;
}

function strong(threshold: number): number {
  return Math.min(1, threshold + StrongDecisionMargin);
}

function cleanScores(value: unknown): Record<string, number> | null {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const output: Record<string, number> = {};
  for (const [key, score] of Object.entries(value)) {
    if (
      !AllowedScoreKeys.has(key) || typeof score !== "number" ||
      !Number.isFinite(score) || score < 0 || score > 1
    ) {
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
  return bytes.length >= 4 && bytes[0] === 0xff && bytes[1] === 0xd8 &&
    bytes.at(-2) === 0xff && bytes.at(-1) === 0xd9;
}

async function sha256(bytes: Uint8Array): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return [...new Uint8Array(digest)].map((value) =>
    value.toString(16).padStart(2, "0")
  ).join("");
}

function isUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
    .test(value);
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
