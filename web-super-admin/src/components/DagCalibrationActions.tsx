"use client";

import { BrainCircuit, Loader2, RotateCcw, Trash2 } from "lucide-react";
import { useActionState } from "react";
import {
  activateDagCalibrationAction,
  clearDagCalibrationReviewsAction,
  createManualDagCalibrationAction,
  prepareDagCalibrationAction,
} from "@/lib/actions";
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

const editableThresholds = [
  ["professional_safe", "NSFW permitido", 0.05, 0.75, 0.15],
  ["professional_block", "NSFW prohibido", 0.35, 0.90, 0.65],
  ["female_face", "Contexto femenino", 0.12, 0.65, 0.30],
  ["male_face", "Contexto masculino", 0.12, 0.65, 0.30],
  ["male_breast_exposed", "Torso masculino", 0.25, 0.90, 0.55],
  ["female_breast_covered", "Pecho o escote", 0.08, 0.65, 0.18],
  ["female_genitalia_covered", "Zona íntima cubierta", 0.08, 0.65, 0.18],
  ["buttocks_covered", "Glúteos cubiertos", 0.08, 0.65, 0.18],
  ["armpits_exposed", "Hombros expuestos", 0.08, 0.65, 0.20],
  ["belly_exposed", "Abdomen expuesto", 0.08, 0.65, 0.20],
  ["explicit_region", "Región explícita", 0.08, 0.65, 0.20],
  ["sleeves_above_elbow", "Manga sobre el codo", 0.45, 0.95, 0.72],
  ["hem_above_knee", "Largo sobre la rodilla", 0.45, 0.95, 0.72],
] as const;

export function ManualDagCalibrationForm({
  modelVersion,
  thresholds,
}: {
  modelVersion: string;
  thresholds: Record<string, number>;
}) {
  const [state, action, pending] = useActionState(createManualDagCalibrationAction, emptyState);
  return (
    <form action={action} className="grid gap-4">
      <input type="hidden" name="modelVersion" value={modelVersion} />
      <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
        {editableThresholds.map(([key, label, minimum, maximum, fallback]) => (
          <label key={key} className="grid gap-1 rounded-md border border-line bg-slate-50 p-3 text-sm font-semibold text-ink">
            <span>{label}</span>
            <input
              className="input"
              name={key}
              type="number"
              min={minimum}
              max={maximum}
              step="0.01"
              defaultValue={thresholds[key] ?? fallback}
              required
            />
            <span className="text-xs font-normal text-slate-500">Rango seguro: {minimum}–{maximum}</span>
          </label>
        ))}
      </div>
      <div className="flex flex-wrap items-center gap-3">
        <button className="button button-secondary" disabled={pending}>
          {pending ? <Loader2 className="h-4 w-4 animate-spin" /> : <BrainCircuit className="h-4 w-4" />}
          {pending ? "Calculando impacto" : "Guardar propuesta manual"}
        </button>
        <p className="text-xs text-slate-500">Guardar no activa los valores en los teléfonos.</p>
      </div>
      {state.message ? <p className={state.ok ? "text-xs text-teal-700" : "text-xs text-danger"}>{state.message}</p> : null}
    </form>
  );
}
