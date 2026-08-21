"use client";

import { Plus } from "lucide-react";
import { useActionState } from "react";
import { createCommunityAction } from "@/lib/actions";
import { emptyState } from "@/lib/action-state";

export function CreateCommunityForm({ compact = false }: { compact?: boolean }) {
  const [state, action, pending] = useActionState(createCommunityAction, emptyState);
  const gridClassName = compact
    ? "grid gap-5 sm:grid-cols-2"
    : "grid gap-5 border-y border-line bg-white py-5 lg:grid-cols-4";

  return (
    <form action={action} className={gridClassName}>
      <div className={compact ? "rounded-xl bg-teal-50 p-3 text-sm leading-6 text-teal-900 sm:col-span-2" : "rounded-xl bg-teal-50 p-3 text-sm leading-6 text-teal-900 lg:col-span-4"}>
        La licencia inicial comienza al crear la comunidad. Podés dejar el vencimiento vacío para que no expire.
      </div>
      <p className={compact ? "border-b border-line pb-2 text-xs font-bold uppercase tracking-[0.14em] text-slate-500 sm:col-span-2" : "border-b border-line pb-2 text-xs font-bold uppercase tracking-[0.14em] text-slate-500 lg:col-span-4"}>Identidad y licencia</p>
      <label className={compact ? "field sm:col-span-2" : "field lg:col-span-2"}>
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
      <fieldset className={compact ? "grid gap-4 border-0 p-0 sm:col-span-2 sm:grid-cols-3" : "grid gap-4 border-0 p-0 lg:col-span-4 lg:grid-cols-3"}>
        <legend className={`mb-3 w-full border-b border-line pb-2 text-xs font-bold uppercase tracking-[0.14em] text-slate-500 ${compact ? "sm:col-span-3" : "lg:col-span-3"}`}>Capacidad inicial</legend>
        <label className="field">
          Administradores
          <input className="input" name="maxAdmins" type="number" min="1" defaultValue="10" required />
        </label>
        <label className="field">
          Usuarios
          <input className="input" name="maxUserDevices" type="number" min="1" defaultValue="250" required />
        </label>
        <label className="field">
          Dispositivos Admin
          <input className="input" name="maxAdminDevices" type="number" min="1" defaultValue="10" required />
        </label>
      </fieldset>
      <label className={compact ? "field sm:col-span-2" : "field lg:col-span-3"}>
        Notas internas
        <input className="input" name="internalNotes" placeholder="Opcional" />
      </label>
      <div className={compact ? "flex items-end sm:col-span-2" : "flex items-end"}>
        <button className="button button-primary w-full" type="submit" disabled={pending}>
          <Plus className="h-4 w-4" />
          {pending ? "Creando…" : "Crear comunidad"}
        </button>
      </div>
      {state.message ? (
        <p className={`${state.ok ? "text-teal-700" : "text-danger"} text-sm font-medium ${compact ? "sm:col-span-2" : "lg:col-span-4"}`}>{state.message}</p>
      ) : null}
    </form>
  );
}
