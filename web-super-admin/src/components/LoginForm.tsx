"use client";

import { LogIn } from "lucide-react";
import { useActionState } from "react";
import { signInAction } from "@/lib/actions";
import { emptyState } from "@/lib/action-state";

export function LoginForm() {
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
      {state.message ? <p className="rounded-md bg-red-50 px-3 py-2 text-sm font-medium text-danger">{state.message}</p> : null}
      <button className="button button-primary" type="submit" disabled={pending}>
        <LogIn className="h-4 w-4" />
        {pending ? "Entrando" : "Entrar"}
      </button>
    </form>
  );
}
