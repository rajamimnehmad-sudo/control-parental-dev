"use client";

import { Save } from "lucide-react";
import { useActionState } from "react";
import { updateLicenseAction } from "@/lib/actions";
import { emptyState } from "@/lib/action-state";
import type { CommunityDetail } from "@/lib/types";
import { formatDateInput } from "@/lib/utils";

export function LicenseForm({ detail, compact = false }: { detail: CommunityDetail; compact?: boolean }) {
  const [state, action, pending] = useActionState(updateLicenseAction, emptyState);
  const formClassName = compact
    ? "grid gap-4 rounded-md border border-line bg-white p-4 shadow-soft"
    : "grid gap-4 rounded-md border border-line bg-white p-4 shadow-soft lg:grid-cols-4";

  return (
    <form action={action} className={formClassName}>
      <input type="hidden" name="communityId" value={detail.community_id} />
      <label className="field">
        Estado
        <select className="input" name="status" defaultValue={detail.license_status}>
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
        Vence
        <input className="input" name="expiresAt" type="date" defaultValue={formatDateInput(detail.expires_at)} />
      </label>
      <label className="field">
        Max admins
        <input className="input" name="maxAdmins" type="number" min="1" defaultValue={detail.max_admins ?? 10} required />
      </label>
      <label className="field">
        Max usuarios
        <input className="input" name="maxUserDevices" type="number" min="1" defaultValue={detail.max_user_devices ?? 250} required />
      </label>
      <label className="field">
        Max admin devices
        <input className="input" name="maxAdminDevices" type="number" min="1" defaultValue={detail.max_admin_devices ?? 10} required />
      </label>
      <div className="flex items-end">
        <button className="button button-primary w-full" type="submit" disabled={pending}>
          <Save className="h-4 w-4" />
          {pending ? "Guardando" : "Guardar"}
        </button>
      </div>
      <label className={compact ? "field" : "field lg:col-span-4"}>
        Notas internas
        <textarea className="textarea" name="internalNotes" defaultValue={detail.internal_notes ?? ""} />
      </label>
      {state.message ? (
        <p className={state.ok ? "text-sm font-medium text-teal-700" : "text-sm font-medium text-danger"}>{state.message}</p>
      ) : null}
    </form>
  );
}
