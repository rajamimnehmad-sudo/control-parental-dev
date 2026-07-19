"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { z } from "zod";
import type { ActionState } from "@/lib/action-state";
import { createClient } from "@/lib/supabase/server";

const communitySchema = z.object({
  name: z.string().trim().min(2, "Ingresa un nombre de comunidad"),
  guideLabel: z.string().trim().default("Equipo de guias"),
  planName: z.string().trim().min(1, "Ingresa un plan"),
  expiresAt: z.string().trim().optional(),
  maxAdmins: z.coerce.number().int().min(1),
  maxUserDevices: z.coerce.number().int().min(1),
  maxAdminDevices: z.coerce.number().int().min(1),
  internalNotes: z.string().trim().optional(),
}).refine(
  (value) => !value.expiresAt || new Date(`${value.expiresAt}T23:59:59.000Z`) > new Date(),
  { message: "La fecha de vencimiento debe ser futura", path: ["expiresAt"] },
);

const licenseSchema = z.object({
  communityId: z.string().uuid(),
  status: z.enum(["active", "suspended", "expired"]),
  planName: z.string().trim().min(1),
  startsAt: z.string().trim().optional(),
  expiresAt: z.string().trim().optional(),
  maxAdmins: z.coerce.number().int().min(1),
  maxUserDevices: z.coerce.number().int().min(1),
  maxAdminDevices: z.coerce.number().int().min(1),
  internalNotes: z.string().trim().optional(),
}).refine(
  (value) => !value.expiresAt || !value.startsAt || value.expiresAt >= value.startsAt,
  { message: "El vencimiento no puede ser anterior al inicio", path: ["expiresAt"] },
);

const dagLimitSchema = z.object({
  communityId: z.string().uuid(),
  monthlyLimit: z.coerce.number().int().min(1).max(100000),
});

const dagEntitlementSchema = z.object({
  communityId: z.string().uuid(),
  enabled: z.enum(["true", "false"]).transform((value) => value === "true"),
});

const deviceDagSchema = z.object({
  communityId: z.string().uuid(),
  deviceId: z.string().uuid(),
  enabled: z.enum(["true", "false"]).transform((value) => value === "true"),
});

const announcementSchema = z.object({
  communityId: z.string().uuid(),
  targetRole: z.enum(["admin", "user", "all"]),
  title: z.string().trim().min(2, "El título debe tener al menos 2 caracteres").max(80),
  body: z.string().trim().min(2, "El mensaje debe tener al menos 2 caracteres").max(500),
  expiresAt: z.string().trim().optional(),
}).refine(
  (value) => !value.expiresAt || new Date(value.expiresAt) > new Date(),
  { message: "El vencimiento debe ser futuro", path: ["expiresAt"] },
);

const archiveSchema = z.object({ id: z.string().uuid() });
const dagReviewSchema = z.object({
  reviewId: z.string().uuid(),
  decision: z.enum(["allow", "block"]),
  reason: z.enum([
    "acceptable_clothing", "product_without_person", "mannequin", "object_misclassified",
    "false_positive", "allow_other", "pronounced_neckline", "exposed_shoulders",
    "sleeves_above_elbow", "hem_above_knee", "tight_or_transparent", "swimwear",
    "lingerie", "nudity", "block_other",
  ]),
  note: z.string().trim().max(500).optional(),
});
const dagCalibrationIdSchema = z.object({ calibrationId: z.string().uuid() });

function formValue(formData: FormData, key: string) {
  const value = formData.get(key);
  return typeof value === "string" ? value : "";
}

function endOfDayOrNull(value?: string) {
  if (!value) return null;
  return `${value}T23:59:59.000Z`;
}

function startOfDayOrNow(value?: string) {
  if (!value) return new Date().toISOString();
  return `${value}T00:00:00.000Z`;
}

function errorState(error: unknown): ActionState {
  const message = error instanceof Error ? error.message : "No se pudo completar la operacion";
  return { ok: false, message };
}

export async function signInAction(_prevState: ActionState, formData: FormData): Promise<ActionState> {
  const email = formValue(formData, "email");
  const password = formValue(formData, "password");
  const supabase = await createClient();

  const { error } = await supabase.auth.signInWithPassword({ email, password });
  if (error?.status === 402 || error?.message.includes("project is restricted")) {
    return {
      ok: false,
      message: "Supabase DEV está temporalmente restringido por su cuota. La contraseña no fue rechazada.",
    };
  }
  if (error) return { ok: false, message: "Email o password invalidos" };

  redirect("/communities");
}

export async function signOutAction() {
  const supabase = await createClient();
  await supabase.auth.signOut();
  redirect("/login");
}

export async function createCommunityAction(_prevState: ActionState, formData: FormData): Promise<ActionState> {
  const parsed = communitySchema.safeParse({
    name: formValue(formData, "name"),
    guideLabel: formValue(formData, "guideLabel"),
    planName: formValue(formData, "planName"),
    expiresAt: formValue(formData, "expiresAt"),
    maxAdmins: formValue(formData, "maxAdmins"),
    maxUserDevices: formValue(formData, "maxUserDevices"),
    maxAdminDevices: formValue(formData, "maxAdminDevices"),
    internalNotes: formValue(formData, "internalNotes"),
  });

  if (!parsed.success) return { ok: false, message: parsed.error.issues[0]?.message ?? "Datos invalidos" };

  const supabase = await createClient();
  const { data, error } = await supabase.rpc("super_admin_create_community", {
    community_name: parsed.data.name,
    community_guide_label: parsed.data.guideLabel || "Equipo de guias",
    license_plan_name: parsed.data.planName,
    license_expires_at: endOfDayOrNull(parsed.data.expiresAt),
    license_max_admins: parsed.data.maxAdmins,
    license_max_user_devices: parsed.data.maxUserDevices,
    license_max_admin_devices: parsed.data.maxAdminDevices,
    license_internal_notes: parsed.data.internalNotes || null,
  });

  if (error) return errorState(error);

  const created = (data ?? [])[0] as { community_id?: string } | undefined;
  revalidatePath("/communities");
  if (created?.community_id) redirect(`/communities/${created.community_id}`);
  return { ok: true, message: "Comunidad creada" };
}

export async function updateLicenseAction(_prevState: ActionState, formData: FormData): Promise<ActionState> {
  const parsed = licenseSchema.safeParse({
    communityId: formValue(formData, "communityId"),
    status: formValue(formData, "status"),
    planName: formValue(formData, "planName"),
    startsAt: formValue(formData, "startsAt"),
    expiresAt: formValue(formData, "expiresAt"),
    maxAdmins: formValue(formData, "maxAdmins"),
    maxUserDevices: formValue(formData, "maxUserDevices"),
    maxAdminDevices: formValue(formData, "maxAdminDevices"),
    internalNotes: formValue(formData, "internalNotes"),
  });

  if (!parsed.success) return { ok: false, message: parsed.error.issues[0]?.message ?? "Datos invalidos" };

  const supabase = await createClient();
  const { error } = await supabase.rpc("super_admin_upsert_license", {
    target_community_id: parsed.data.communityId,
    license_status: parsed.data.status,
    license_plan_name: parsed.data.planName,
    license_starts_at: startOfDayOrNow(parsed.data.startsAt),
    license_expires_at: endOfDayOrNull(parsed.data.expiresAt),
    license_max_admins: parsed.data.maxAdmins,
    license_max_user_devices: parsed.data.maxUserDevices,
    license_max_admin_devices: parsed.data.maxAdminDevices,
    license_internal_notes: parsed.data.internalNotes || null,
  });

  if (error) return errorState(error);
  revalidatePath(`/communities/${parsed.data.communityId}`);
  revalidatePath("/communities");
  return { ok: true, message: "Licencia actualizada" };
}

export async function updateDagLimitAction(_prevState: ActionState, formData: FormData): Promise<ActionState> {
  const parsed = dagLimitSchema.safeParse({
    communityId: formValue(formData, "communityId"),
    monthlyLimit: formValue(formData, "monthlyLimit"),
  });

  if (!parsed.success) {
    return { ok: false, message: "El cupo debe estar entre 1 y 100.000 búsquedas" };
  }

  const supabase = await createClient();
  const { error } = await supabase.rpc("super_admin_set_dag_search_monthly_limit", {
    target_community_id: parsed.data.communityId,
    new_monthly_limit: parsed.data.monthlyLimit,
  });

  if (error) return errorState(error);
  revalidatePath("/dag-usage");
  return { ok: true, message: "Cupo actualizado" };
}

export async function updateDagEntitlementAction(_prevState: ActionState, formData: FormData): Promise<ActionState> {
  const parsed = dagEntitlementSchema.safeParse({
    communityId: formValue(formData, "communityId"),
    enabled: formValue(formData, "enabled"),
  });

  if (!parsed.success) return { ok: false, message: "Estado DAG inválido" };

  const supabase = await createClient();
  const { error } = await supabase.rpc("super_admin_set_dag_entitlement", {
    target_community_id: parsed.data.communityId,
    new_dag_entitled: parsed.data.enabled,
  });

  if (error) return errorState(error);
  revalidatePath(`/communities/${parsed.data.communityId}`);
  revalidatePath("/dag-usage");
  return { ok: true, message: parsed.data.enabled ? "DAG habilitado para la comunidad" : "DAG deshabilitado para la comunidad" };
}

export async function updateDeviceDagAction(_prevState: ActionState, formData: FormData): Promise<ActionState> {
  const parsed = deviceDagSchema.safeParse({
    communityId: formValue(formData, "communityId"),
    deviceId: formValue(formData, "deviceId"),
    enabled: formValue(formData, "enabled"),
  });

  if (!parsed.success) return { ok: false, message: "Usuario DAG inválido" };

  const supabase = await createClient();
  const { error } = await supabase.rpc("super_admin_set_device_dag_enabled", {
    target_community_id: parsed.data.communityId,
    target_device_id: parsed.data.deviceId,
    new_dag_enabled: parsed.data.enabled,
  });

  if (error) return errorState(error);
  revalidatePath(`/communities/${parsed.data.communityId}`);
  revalidatePath("/dag-usage");
  return { ok: true, message: parsed.data.enabled ? "DAG habilitado para este usuario" : "DAG deshabilitado para este usuario" };
}

export async function createAnnouncementAction(_prevState: ActionState, formData: FormData): Promise<ActionState> {
  const parsed = announcementSchema.safeParse({
    communityId: formValue(formData, "communityId"),
    targetRole: formValue(formData, "targetRole"),
    title: formValue(formData, "title"),
    body: formValue(formData, "body"),
    expiresAt: formValue(formData, "expiresAt"),
  });
  if (!parsed.success) return { ok: false, message: parsed.error.issues[0]?.message ?? "Datos inválidos" };

  const supabase = await createClient();
  const { data, error } = await supabase.rpc("super_admin_create_announcement", {
    target_community_id: parsed.data.communityId,
    target_app_role: parsed.data.targetRole,
    announcement_title: parsed.data.title,
    announcement_body: parsed.data.body,
    announcement_expires_at: parsed.data.expiresAt ? new Date(parsed.data.expiresAt).toISOString() : null,
  });
  if (error) return errorState(error);
  const announcementId = (data ?? [])[0]?.announcement_id as string | undefined;
  if (!announcementId) return { ok: false, message: "El aviso no pudo guardarse" };

  const { data: delivery, error: deliveryError } = await supabase.functions.invoke("send-announcement", {
    body: { announcement_id: announcementId },
  });
  revalidatePath("/announcements");
  if (deliveryError || delivery?.error) {
    return { ok: true, message: "Aviso guardado. El push no estuvo disponible; seguirá visible en la bandeja." };
  }
  const sent = Number(delivery?.sent ?? 0);
  return { ok: true, message: sent > 0 ? `Aviso guardado y enviado a ${sent} dispositivo(s)` : "Aviso guardado; no había dispositivos con push registrado" };
}

export async function archiveAlertGroupAction(_prevState: ActionState, formData: FormData): Promise<ActionState> {
  const parsed = archiveSchema.safeParse({ id: formValue(formData, "id") });
  if (!parsed.success) return { ok: false, message: "Alerta inválida" };
  const supabase = await createClient();
  const { error } = await supabase.rpc("super_admin_archive_protection_alerts", { target_device_id: parsed.data.id });
  if (error) return errorState(error);
  revalidatePath("/alerts");
  return { ok: true, message: "Alertas borradas de la bandeja" };
}

export async function archiveAnnouncementAction(_prevState: ActionState, formData: FormData): Promise<ActionState> {
  const parsed = archiveSchema.safeParse({ id: formValue(formData, "id") });
  if (!parsed.success) return { ok: false, message: "Aviso inválido" };
  const supabase = await createClient();
  const { error } = await supabase.rpc("super_admin_archive_announcement", { target_announcement_id: parsed.data.id });
  if (error) return errorState(error);
  revalidatePath("/announcements");
  return { ok: true, message: "Aviso borrado del historial" };
}

export async function labelDagCalibrationReviewAction(_prevState: ActionState, formData: FormData): Promise<ActionState> {
  const parsed = dagReviewSchema.safeParse({
    reviewId: formValue(formData, "reviewId"),
    decision: formValue(formData, "decision"),
    reason: formValue(formData, "reason"),
    note: formValue(formData, "note"),
  });
  if (!parsed.success) return { ok: false, message: "Completá la decisión y el motivo." };
  const supabase = await createClient();
  const { error } = await supabase.rpc("super_admin_label_dag_calibration_review", {
    target_review_id: parsed.data.reviewId,
    new_decision: parsed.data.decision,
    new_reason: parsed.data.reason,
    new_note: parsed.data.note || null,
  });
  if (error) return errorState(error);
  revalidatePath("/dag-calibration");
  return { ok: true, message: "Respuesta registrada para Calibración DAG." };
}

export async function clearDagCalibrationReviewsAction(_prevState: ActionState): Promise<ActionState> {
  void _prevState;
  const supabase = await createClient();
  const { data, error } = await supabase.functions.invoke("dag-calibration", {
    body: { action: "clear" },
  });
  if (error) {
    const context = "context" in error ? error.context : null;
    if (context instanceof Response) {
      const payload = await context.clone().json().catch(() => null) as { error?: unknown } | null;
      if (typeof payload?.error === "string") return { ok: false, message: payload.error };
    }
    return errorState(error);
  }
  if (data?.error) return errorState(new Error(String(data.error)));
  const cleared = Number(data?.cleared ?? 0);
  revalidatePath("/dag-calibration");
  return {
    ok: true,
    message: cleared > 0 ? `Se borraron ${cleared} fotos de Calibración DAG.` : "No había fotos para borrar.",
  };
}

export async function prepareDagCalibrationAction(_prevState: ActionState): Promise<ActionState> {
  void _prevState;
  const supabase = await createClient();
  const { data, error } = await supabase.rpc("super_admin_list_dag_calibration_reviews", {
    requested_status: "reviewed",
    max_rows: 500,
  });
  if (error) return errorState(error);
  const allReviews = (data ?? []) as Array<{
    review_id: string;
    review_decision: "allow" | "block";
    review_reason: string;
    scores: Record<string, number>;
    model_version: string;
  }>;
  const modelVersion = allReviews[0]?.model_version ?? "unknown";
  const reviews = allReviews.filter((review) => review.model_version === modelVersion);
  const allowCount = reviews.filter((review) => review.review_decision === "allow").length;
  const blockCount = reviews.length - allowCount;
  if (reviews.length < 12 || allowCount < 3 || blockCount < 3) {
    return { ok: false, message: `Faltan ejemplos: hay ${reviews.length}/12, con ${allowCount} permitidos y ${blockCount} bloqueados (mínimo 3 de cada uno).` };
  }
  const { training, validation } = calibrationSplit(reviews);
  const professional = bestThreshold(training, "professional", 0.65);
  const professionalBlock = clampThreshold(professional.threshold, 0.35, 0.90);
  const thresholds = {
    professional_safe: round4(professionalBlock - 0.15),
    professional_block: professionalBlock,
    female_face: boundedBestThreshold(training, "female_face", 0.30, 0.12, 0.65),
    female_breast_covered: boundedBestThreshold(training, "female_breast_covered", 0.18, 0.08, 0.65, ["pronounced_neckline", "tight_or_transparent", "swimwear", "lingerie", "nudity", "block_other"]),
    female_genitalia_covered: boundedBestThreshold(training, "female_genitalia_covered", 0.18, 0.08, 0.65, ["tight_or_transparent", "swimwear", "lingerie", "nudity", "block_other"]),
    buttocks_covered: boundedBestThreshold(training, "buttocks_covered", 0.18, 0.08, 0.65, ["tight_or_transparent", "swimwear", "lingerie", "nudity", "block_other"]),
    armpits_exposed: boundedBestThreshold(training, "armpits_exposed", 0.20, 0.08, 0.65, ["exposed_shoulders", "sleeves_above_elbow", "swimwear", "lingerie", "block_other"]),
    belly_exposed: boundedBestThreshold(training, "belly_exposed", 0.20, 0.08, 0.65, ["tight_or_transparent", "swimwear", "lingerie", "nudity", "block_other"]),
    explicit_region: boundedBestThreshold(training, "explicit_region", 0.20, 0.08, 0.65, ["swimwear", "lingerie", "nudity", "block_other"]),
    sleeves_above_elbow: boundedBestThreshold(training, "sleeves_above_elbow", 0.72, 0.45, 0.95, ["sleeves_above_elbow", "block_other"]),
    hem_above_knee: boundedBestThreshold(training, "hem_above_knee", 0.72, 0.45, 0.95, ["hem_above_knee", "block_other"]),
  };
  const trainingMetrics = evaluateCalibration(training, thresholds);
  const validationMetrics = evaluateCalibration(validation, thresholds);
  const metrics = {
    reviewed: reviews.length,
    allowed: allowCount,
    blocked: blockCount,
    false_positive: trainingMetrics.falsePositive,
    false_negative: trainingMetrics.falseNegative,
    weighted_error: trainingMetrics.weightedError,
    training_examples: training.length,
    validation_examples: validation.length,
    validation_false_positive: validationMetrics.falsePositive,
    validation_false_negative: validationMetrics.falseNegative,
    validation_weighted_error: validationMetrics.weightedError,
  };
  const { error: createError } = await supabase.rpc("super_admin_create_dag_calibration", {
    new_thresholds: thresholds,
    new_metrics: metrics,
    calibration_model_version: modelVersion,
    calibration_explanation: "Umbrales candidatos calculados con ejemplos de ajuste y comprobados contra un grupo separado. Los falsos negativos pesan 3×. Incluye decisiones exactas, requiere activación manual y admite reversión.",
  });
  if (createError) return errorState(createError);
  revalidatePath("/dag-calibration");
  return { ok: true, message: "Calibración candidata creada. Revisá las métricas antes de activarla." };
}

type CalibrationReview = {
  review_id: string;
  review_decision: "allow" | "block";
  review_reason: string;
  scores: Record<string, number>;
};

function calibrationSplit<T extends CalibrationReview>(reviews: T[]): { training: T[]; validation: T[] } {
  const validationIds = new Set<string>();
  for (const decision of ["allow", "block"] as const) {
    reviews.filter((review) => review.review_decision === decision).slice(0, Math.max(1, Math.floor(reviews.filter((review) => review.review_decision === decision).length * 0.2))).forEach((review) => validationIds.add(review.review_id));
  }
  return {
    training: reviews.filter((review) => !validationIds.has(review.review_id)),
    validation: reviews.filter((review) => validationIds.has(review.review_id)),
  };
}

function evaluateCalibration(
  reviews: CalibrationReview[],
  thresholds: Record<string, number>,
): { falsePositive: number; falseNegative: number; weightedError: number } {
  const predictedBlock = (review: CalibrationReview) => imageScoresRequireBlur(review.scores, thresholds);
  const falsePositive = reviews.filter((review) => review.review_decision === "allow" && predictedBlock(review)).length;
  const falseNegative = reviews.filter((review) => review.review_decision === "block" && !predictedBlock(review)).length;
  return { falsePositive, falseNegative, weightedError: falsePositive + falseNegative * 3 };
}

function imageScoresRequireBlur(scores: Record<string, number>, thresholds: Record<string, number>): boolean {
  if ((scores.professional ?? 0) >= thresholds.professional_block) return true;
  if ((scores.legacy ?? 0) >= thresholds.professional_block) return true;
  if ((scores.female_breast_covered ?? 0) >= thresholds.female_breast_covered) return true;
  if ((scores.female_genitalia_covered ?? 0) >= thresholds.female_genitalia_covered) return true;
  if ((scores.buttocks_covered ?? 0) >= thresholds.buttocks_covered) return true;
  if ((scores.explicit_region ?? 0) >= thresholds.explicit_region) return true;
  const femaleContext = (scores.female_face ?? 0) >= thresholds.female_face;
  return femaleContext && ["armpits_exposed", "belly_exposed", "sleeves_above_elbow", "hem_above_knee"]
    .some((key) => (scores[key] ?? 0) >= thresholds[key]);
}

export async function activateDagCalibrationAction(_prevState: ActionState, formData: FormData): Promise<ActionState> {
  const parsed = dagCalibrationIdSchema.safeParse({ calibrationId: formValue(formData, "calibrationId") });
  if (!parsed.success) return { ok: false, message: "Calibración inválida." };
  const supabase = await createClient();
  const { error } = await supabase.rpc("super_admin_activate_dag_calibration", { target_calibration_id: parsed.data.calibrationId });
  if (error) return errorState(error);
  revalidatePath("/dag-calibration");
  return { ok: true, message: "Calibración activada. Los dispositivos la recibirán en su próxima comprobación." };
}

function bestThreshold(
  reviews: Array<{ review_decision: "allow" | "block"; review_reason: string; scores: Record<string, number> }>,
  key: string,
  fallback: number,
  relevantBlockReasons?: string[],
) {
  // Every permitted example is useful for avoiding false positives (including phones
  // and other objects). A blocked example only trains the detector that matches the
  // human reason, so a neckline label cannot accidentally lower the sleeve threshold.
  const relevantReviews = relevantBlockReasons
    ? reviews.filter((review) => review.review_decision === "allow" || relevantBlockReasons.includes(review.review_reason))
    : reviews;
  const samples = relevantReviews.flatMap((review) => {
    const score = review.scores?.[key];
    return typeof score === "number" && Number.isFinite(score) ? [{ score, decision: review.review_decision }] : [];
  });
  if (samples.length < 6) return { threshold: fallback, falsePositive: 0, falseNegative: 0, weightedError: 0 };
  const candidates = [...new Set([0.05, 0.95, ...samples.map((sample) => round4(sample.score))])].sort((a, b) => a - b);
  return candidates.map((threshold) => {
    const falsePositive = samples.filter((sample) => sample.decision === "allow" && sample.score >= threshold).length;
    const falseNegative = samples.filter((sample) => sample.decision === "block" && sample.score < threshold).length;
    return { threshold, falsePositive, falseNegative, weightedError: falsePositive + falseNegative * 3 };
  }).sort((left, right) => left.weightedError - right.weightedError || left.falseNegative - right.falseNegative || left.threshold - right.threshold)[0];
}

function boundedBestThreshold(
  reviews: Array<{ review_decision: "allow" | "block"; review_reason: string; scores: Record<string, number> }>,
  key: string,
  fallback: number,
  minimum: number,
  maximum: number,
  relevantBlockReasons?: string[],
): number {
  return clampThreshold(bestThreshold(reviews, key, fallback, relevantBlockReasons).threshold, minimum, maximum);
}

function clampThreshold(value: number, minimum: number, maximum: number): number {
  return round4(Math.max(minimum, Math.min(maximum, value)));
}

function round4(value: number) {
  return Math.round(Math.max(0, Math.min(1, value)) * 10000) / 10000;
}
