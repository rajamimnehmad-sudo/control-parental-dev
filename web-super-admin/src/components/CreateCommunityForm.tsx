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
        Vence
        <input className="input" name="expiresAt" type="date" />
      </label>
      <label className="field">
        Max admins
        <input className="input" name="maxAdmins" type="number" min="1" defaultValue="10" required />
      </label>
      <label className="field">
        Max usuarios
        <input className="input" name="maxUserDevices" type="number" min="1" defaultValue="250" required />
      </label>
      <label className="field">
        Max admin devices
        <input className="input" name="maxAdminDevices" type="number" min="1" defaultValue="10" required />
      </label>
      <label className={compact ? "field" : "field lg:col-span-3"}>
        Notas internas
        <input className="input" name="internalNotes" placeholder="Opcional" />
      </label>
      <div className="flex items-end">
        <button className="button button-primary w-full" type="submit" disabled={pending}>
          <Plus className="h-4 w-4" />
          {pending ? "Creando" : "Crear"}
        </button>
      </div>
      {state.message ? (
        <p className={state.ok ? "text-sm font-medium text-teal-700" : "text-sm font-medium text-danger"}>{state.message}</p>
      ) : null}
    </form>
  );
}
