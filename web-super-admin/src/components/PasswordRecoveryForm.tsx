"use client";

import { Mail } from "lucide-react";
import { useActionState } from "react";
import { emptyState } from "@/lib/action-state";
import { requestPasswordRecoveryAction } from "@/lib/password-actions";

export function PasswordRecoveryForm() {
  const [state, action, pending] = useActionState(requestPasswordRecoveryAction, emptyState);

  return (
    <form action={action} className="grid gap-4">
      <label className="field">
        Email de Super Admin
        <input className="input" name="email" type="email" autoComplete="email" required />
      </label>
      {state.message ? (
        <p
          className={`rounded-md px-3 py-2 text-sm font-medium ${
            state.ok ? "bg-emerald-50 text-emerald-800" : "bg-red-50 text-danger"
          }`}
        >
          {state.message}
        </p>
      ) : null}
      <button className="button button-primary" type="submit" disabled={pending || state.ok}>
        <Mail className="h-4 w-4" />
        {pending ? "Enviando" : state.ok ? "Enlace enviado" : "Enviar enlace"}
      </button>
    </form>
  );
}
