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
