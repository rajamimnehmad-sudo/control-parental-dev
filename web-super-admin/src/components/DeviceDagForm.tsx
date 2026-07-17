"use client";

import { SearchCheck, SearchX } from "lucide-react";
import { useActionState } from "react";
import { updateDeviceDagAction } from "@/lib/actions";
import { emptyState } from "@/lib/action-state";

type Props = {
  communityId: string;
  deviceId: string | null;
  enabled: boolean;
  entitled: boolean;
};

export function DeviceDagForm({ communityId, deviceId, enabled, entitled }: Props) {
  const [state, action, pending] = useActionState(updateDeviceDagAction, emptyState);
  const nextEnabled = !enabled;
  const canSubmit = deviceId !== null && (enabled || entitled);

  return (
    <form action={action} className="mt-4 grid gap-2 rounded-xl bg-slate-50 p-3 ring-1 ring-slate-100">
      <input type="hidden" name="communityId" value={communityId} />
      <input type="hidden" name="deviceId" value={deviceId ?? ""} />
      <input type="hidden" name="enabled" value={String(nextEnabled)} />
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="text-sm font-bold text-ink">Buscador DAG</p>
          <p className="mt-0.5 text-xs text-muted">
            {!deviceId ? "Disponible después de activar el teléfono." : !entitled && !enabled ? "La comunidad no incluye DAG premium." : enabled ? "Habilitado para este usuario." : "Cerrado para este usuario."}
          </p>
        </div>
        <button className={enabled ? "button button-secondary" : "button button-primary"} type="submit" disabled={pending || !canSubmit}>
          {enabled ? <SearchX className="h-4 w-4" /> : <SearchCheck className="h-4 w-4" />}
          {pending ? "Guardando" : enabled ? "Deshabilitar" : "Habilitar"}
        </button>
      </div>
      {state.message ? <p className={state.ok ? "text-xs font-semibold text-teal-700" : "text-xs font-semibold text-danger"}>{state.message}</p> : null}
    </form>
  );
}
