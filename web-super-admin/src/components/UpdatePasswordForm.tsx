"use client";

import { KeyRound } from "lucide-react";
import { useActionState } from "react";
import { emptyState } from "@/lib/action-state";
import { updatePasswordAction } from "@/lib/password-actions";

export function UpdatePasswordForm() {
  const [state, action, pending] = useActionState(updatePasswordAction, emptyState);

  return (
    <form action={action} className="grid gap-4">
      <label className="field">
        Contraseña nueva
        <input
          className="input"
          name="password"
          type="password"
          autoComplete="new-password"
          minLength={12}
          maxLength={128}
          required
        />
      </label>
      <label className="field">
        Repetir contraseña
        <input
          className="input"
          name="confirmation"
          type="password"
          autoComplete="new-password"
          minLength={12}
          maxLength={128}
          required
        />
      </label>
      <p className="text-xs text-slate-500">Usá al menos 12 caracteres y una contraseña que no reutilices.</p>
      {state.message ? (
        <p className="rounded-md bg-red-50 px-3 py-2 text-sm font-medium text-danger">{state.message}</p>
      ) : null}
      <button className="button button-primary" type="submit" disabled={pending}>
        <KeyRound className="h-4 w-4" />
        {pending ? "Actualizando" : "Guardar contraseña"}
      </button>
    </form>
  );
}
