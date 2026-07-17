"use client";

import { BrainCircuit, Loader2, RotateCcw, Trash2 } from "lucide-react";
import { useActionState } from "react";
import { activateDagCalibrationAction, clearDagCalibrationReviewsAction, prepareDagCalibrationAction } from "@/lib/actions";
import { emptyState } from "@/lib/action-state";

export function PrepareDagCalibrationButton() {
  const [state, action, pending] = useActionState(prepareDagCalibrationAction, emptyState);
  return (
    <form action={action} className="grid gap-1">
      <button className="button button-primary" disabled={pending}>
        {pending ? <Loader2 className="h-4 w-4 animate-spin" /> : <BrainCircuit className="h-4 w-4" />}
        {pending ? "Calculando" : "Calibrar DAG"}
      </button>
      {state.message ? <p className={state.ok ? "text-xs text-teal-700" : "text-xs text-danger"}>{state.message}</p> : null}
    </form>
  );
}

export function ClearDagCalibrationButton({ disabled }: { disabled: boolean }) {
  const [state, action, pending] = useActionState(clearDagCalibrationReviewsAction, emptyState);
  return (
    <form
      action={action}
      className="grid gap-1"
      onSubmit={(event) => {
        if (!window.confirm("¿Borrar todas las fotos pendientes y revisadas de Calibración DAG? Esta acción no se puede deshacer.")) {
          event.preventDefault();
        }
      }}
    >
      <button className="button border border-red-200 bg-red-50 text-danger hover:bg-red-100" disabled={disabled || pending}>
        {pending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}
        {pending ? "Borrando" : "Borrar todas"}
      </button>
      {state.message ? <p className={state.ok ? "text-xs text-teal-700" : "text-xs text-danger"}>{state.message}</p> : null}
    </form>
  );
}

export function ActivateDagCalibrationButton({ calibrationId, active }: { calibrationId: string; active: boolean }) {
  const [state, action, pending] = useActionState(activateDagCalibrationAction, emptyState);
  if (active) return <span className="rounded-full bg-teal-50 px-3 py-1 text-xs font-bold text-teal-700">Activa</span>;
  return (
    <form action={action} className="grid justify-items-end gap-1">
      <input type="hidden" name="calibrationId" value={calibrationId} />
      <button className="button button-secondary" disabled={pending}>
        {pending ? <Loader2 className="h-4 w-4 animate-spin" /> : <RotateCcw className="h-4 w-4" />} Activar
      </button>
      {state.message ? <p className={state.ok ? "text-xs text-teal-700" : "text-xs text-danger"}>{state.message}</p> : null}
    </form>
  );
}
