"use client";

import { LogIn } from "lucide-react";
import Link from "next/link";
import { useActionState } from "react";
import { signInAction } from "@/lib/actions";
import { emptyState } from "@/lib/action-state";

export function LoginForm({
  passwordUpdated = false,
  recoveryError,
}: {
  passwordUpdated?: boolean;
  recoveryError?: string;
}) {
  const [state, action, pending] = useActionState(signInAction, emptyState);

  return (
    <form action={action} className="grid gap-4">
      <label className="field">
        Email
        <input className="input" name="email" type="email" autoComplete="email" required />
      </label>
      <label className="field">
        Password
        <input className="input" name="password" type="password" autoComplete="current-password" required />
      </label>
      <div className="-mt-2 text-right">
        <Link className="text-sm font-medium text-accent hover:underline" href="/recuperar-password">
          Olvidé mi contraseña
        </Link>
      </div>
      {passwordUpdated ? (
        <p className="rounded-md bg-emerald-50 px-3 py-2 text-sm font-medium text-emerald-800">
          Contraseña actualizada. Ingresá nuevamente.
        </p>
      ) : null}
      {recoveryError ? (
        <p className="rounded-md bg-red-50 px-3 py-2 text-sm font-medium text-danger">
          {recoveryError === "service"
            ? "Supabase DEV está temporalmente restringido por su cuota. La contraseña no fue rechazada."
            : "El enlace no es válido o venció. Solicitá uno nuevo."}
        </p>
      ) : null}
      {state.message ? <p className="rounded-md bg-red-50 px-3 py-2 text-sm font-medium text-danger">{state.message}</p> : null}
      <button className="button button-primary" type="submit" disabled={pending}>
        <LogIn className="h-4 w-4" />
        {pending ? "Entrando" : "Entrar"}
      </button>
    </form>
  );
}
