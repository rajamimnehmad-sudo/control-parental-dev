"use client";

import { SearchCheck, SearchX } from "lucide-react";
import { useActionState } from "react";
import { updateDagEntitlementAction } from "@/lib/actions";
import { emptyState } from "@/lib/action-state";
import type { CommunityDetail } from "@/lib/types";

export function DagEntitlementForm({ detail }: { detail: CommunityDetail }) {
  const [state, action, pending] = useActionState(updateDagEntitlementAction, emptyState);
  const nextEnabled = !detail.dag_entitled;

  return (
    <form action={action} className="grid gap-3 rounded-md border border-line bg-white p-4 shadow-soft">
      <input type="hidden" name="communityId" value={detail.community_id} />
      <input type="hidden" name="enabled" value={String(nextEnabled)} />
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="font-bold text-ink">DAG premium</p>
          <p className="mt-1 text-sm text-muted">
            {detail.dag_entitled
              ? "La comunidad puede habilitar DAG por dispositivo desde App Admin."
              : "DAG permanece cerrado aunque exista una regla anterior por dispositivo."}
          </p>
        </div>
        <span className={detail.dag_entitled ? "rounded-full bg-teal-50 px-2.5 py-1 text-xs font-bold text-teal-700" : "rounded-full bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-600"}>
          {detail.dag_entitled ? "Habilitado" : "Deshabilitado"}
        </span>
      </div>
      <button className={detail.dag_entitled ? "button button-secondary" : "button button-primary"} type="submit" disabled={pending}>
        {detail.dag_entitled ? <SearchX className="h-4 w-4" /> : <SearchCheck className="h-4 w-4" />}
        {pending ? "Guardando" : detail.dag_entitled ? "Deshabilitar DAG" : "Habilitar DAG"}
      </button>
      <p className="text-xs text-muted">El cambio conserva reglas, historial y configuración para una futura reactivación.</p>
      {state.message ? (
        <p className={state.ok ? "text-sm font-medium text-teal-700" : "text-sm font-medium text-danger"}>{state.message}</p>
      ) : null}
    </form>
  );
}
