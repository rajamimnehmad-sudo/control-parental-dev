"use server";

import { redirect } from "next/navigation";
import { z } from "zod";
import type { ActionState } from "@/lib/action-state";
import { superAdminSiteUrl } from "@/lib/environment";
import { createClient } from "@/lib/supabase/server";

const genericRecoveryMessage =
  "Si el email corresponde a un Super Admin, vas a recibir un enlace para cambiar la contraseña.";

const emailSchema = z.string().trim().email();
const passwordSchema = z
  .string()
  .min(12, "La contraseña debe tener al menos 12 caracteres.")
  .max(128, "La contraseña es demasiado larga.");

function formValue(formData: FormData, key: string) {
  const value = formData.get(key);
  return typeof value === "string" ? value : "";
}

export async function requestPasswordRecoveryAction(
  _previousState: ActionState,
  formData: FormData,
): Promise<ActionState> {
  const parsedEmail = emailSchema.safeParse(formValue(formData, "email"));
  if (!parsedEmail.success) {
    return { ok: false, message: "Ingresá un email válido." };
  }

  const supabase = await createClient();
  await supabase.auth.resetPasswordForEmail(parsedEmail.data, {
    redirectTo: `${superAdminSiteUrl}/auth/callback?next=/actualizar-password`,
  });

  // The same response is returned whether or not the account exists.
  return { ok: true, message: genericRecoveryMessage };
}

export async function updatePasswordAction(
  _previousState: ActionState,
  formData: FormData,
): Promise<ActionState> {
  const password = formValue(formData, "password");
  const confirmation = formValue(formData, "confirmation");
  const parsedPassword = passwordSchema.safeParse(password);

  if (!parsedPassword.success) {
    return { ok: false, message: parsedPassword.error.issues[0]?.message ?? "Contraseña inválida." };
  }
  if (password !== confirmation) {
    return { ok: false, message: "Las contraseñas no coinciden." };
  }

  const supabase = await createClient();
  const { data: userData, error: userError } = await supabase.auth.getUser();
  if (userError || !userData.user) {
    return { ok: false, message: "El enlace venció. Solicitá uno nuevo." };
  }

  const { data: allowed, error: allowedError } = await supabase.rpc("is_super_admin");
  if (allowedError || allowed !== true) {
    await supabase.auth.signOut();
    return { ok: false, message: "Este usuario no está habilitado como Super Admin." };
  }

  const { error: updateError } = await supabase.auth.updateUser({ password: parsedPassword.data });
  if (updateError) {
    return { ok: false, message: "No se pudo actualizar la contraseña. Solicitá un enlace nuevo." };
  }

  await supabase.auth.signOut({ scope: "global" });
  redirect("/login?passwordUpdated=1");
}
