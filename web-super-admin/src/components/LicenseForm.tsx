"use client";

import { Save } from "lucide-react";
import { useActionState } from "react";
import { updateLicenseAction } from "@/lib/actions";
import { emptyState } from "@/lib/action-state";
import type { CommunityDetail } from "@/lib/types";
import { formatDateInput } from "@/lib/utils";

export function LicenseForm({ detail, compact = false }: { detail: CommunityDetail; compact?: boolean }) {
  const [state, action, pending] = useActionState(updateLicenseAction, emptyState);
  const storedStatus = detail.license_status === "scheduled" ? "active" : detail.license_status;
  const formClassName = compact
    ? "grid gap-4 rounded-md border border-line bg-white p-4 shadow-soft"
    : "grid gap-4 rounded-md border border-line bg-white p-4 shadow-soft lg:grid-cols-4";

  return (
    <form action={action} className={formClassName}>
      <input type="hidden" name="communityId" value={detail.community_id} />
      <div className={compact ? "rounded-xl bg-slate-50 p-3 text-sm text-slate-600" : "rounded-xl bg-slate-50 p-3 text-sm text-slate-600 lg:col-span-4"}>
        Estado visible actual: <strong className="text-ink">{statusLabel(detail.license_status)}</strong>. Una licencia activa con inicio futuro se muestra automáticamente como Programada.
      </div>
      <label className="field">
        Estado
        <select className="input" name="status" defaultValue={storedStatus}>
          <option value="active">Activa</option>
          <option value="suspended">Suspendida</option>
          <option value="expired">Vencida</option>
        </select>
      </label>
      <label className="field">
        Plan
        <input className="input" name="planName" defaultValue={detail.plan_name} required />
      </label>
      <label className="field">
        Inicio
        <input className="input" name="startsAt" type="date" defaultValue={formatDateInput(detail.starts_at)} />
      </label>
      <label className="field">
        Vencimiento (opcional)
        <input className="input" name="expiresAt" type="date" defaultValue={formatDateInput(detail.expires_at)} />
      </label>
      <label className="field">
        Máximo de administradores
        <input className="input" name="maxAdmins" type="number" min="1" defaultValue={detail.max_admins ?? 10} required />
      </label>
      <label className="field">
        Máximo de usuarios
        <input className="input" name="maxUserDevices" type="number" min="1" defaultValue={detail.max_user_devices ?? 250} required />
      </label>
      <label className="field">
        Máximo de dispositivos Admin
        <input className="input" name="maxAdminDevices" type="number" min="1" defaultValue={detail.max_admin_devices ?? 10} required />
      </label>
      <div className="flex items-end">
        <button className="button button-primary w-full" type="submit" disabled={pending}>
          <Save className="h-4 w-4" />
          {pending ? "Guardando…" : "Guardar licencia"}
        </button>
      </div>
      <label className={compact ? "field" : "field lg:col-span-4"}>
        Notas internas (opcional)
        <textarea className="textarea" name="internalNotes" defaultValue={detail.internal_notes ?? ""} placeholder="Información operativa que solo ve el Super Admin" />
      </label>
      {state.message ? (
        <p className={state.ok ? "text-sm font-medium text-teal-700" : "text-sm font-medium text-danger"}>{state.message}</p>
      ) : null}
    </form>
  );
}

function statusLabel(status: CommunityDetail["license_status"]) {
  if (status === "active") return "Activa";
  if (status === "scheduled") return "Programada";
  if (status === "suspended") return "Suspendida";
  return "Vencida";
}
