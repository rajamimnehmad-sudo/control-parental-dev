"use client";

import { Plus } from "lucide-react";
import { useActionState } from "react";
import { createCommunityAction } from "@/lib/actions";
import { emptyState } from "@/lib/action-state";

export function CreateCommunityForm({ compact = false }: { compact?: boolean }) {
  const [state, action, pending] = useActionState(createCommunityAction, emptyState);
  const gridClassName = compact
    ? "grid gap-4 rounded-md border border-line bg-white p-4 shadow-soft"
    : "grid gap-4 rounded-md border border-line bg-white p-4 shadow-soft lg:grid-cols-4";

  return (
    <form action={action} className={gridClassName}>
      <div className={compact ? "rounded-xl bg-teal-50 p-3 text-sm text-teal-900" : "rounded-xl bg-teal-50 p-3 text-sm text-teal-900 lg:col-span-4"}>
        La licencia inicial comienza al crear la comunidad. Podés dejar el vencimiento vacío para que no expire.
      </div>
      <label className={compact ? "field" : "field lg:col-span-2"}>
        Comunidad
        <input className="input" name="name" placeholder="Nombre de la comunidad" required />
      </label>
      <label className="field">
        Responsable visible
        <input className="input" name="guideLabel" defaultValue="Equipo de guias" required />
      </label>
      <label className="field">
        Plan
        <input className="input" name="planName" defaultValue="Produccion" required />
      </label>
      <label className="field">
        Vencimiento (opcional)
        <input className="input" name="expiresAt" type="date" />
      </label>
      <label className="field">
        Máximo de administradores
        <input className="input" name="maxAdmins" type="number" min="1" defaultValue="10" required />
      </label>
      <label className="field">
        Máximo de usuarios
        <input className="input" name="maxUserDevices" type="number" min="1" defaultValue="250" required />
      </label>
      <label className="field">
        Máximo de dispositivos Admin
        <input className="input" name="maxAdminDevices" type="number" min="1" defaultValue="10" required />
      </label>
      <label className={compact ? "field" : "field lg:col-span-3"}>
        Notas internas
        <input className="input" name="internalNotes" placeholder="Opcional" />
      </label>
      <div className="flex items-end">
        <button className="button button-primary w-full" type="submit" disabled={pending}>
          <Plus className="h-4 w-4" />
          {pending ? "Creando…" : "Crear comunidad"}
        </button>
      </div>
      {state.message ? (
        <p className={state.ok ? "text-sm font-medium text-teal-700" : "text-sm font-medium text-danger"}>{state.message}</p>
      ) : null}
    </form>
  );
}
