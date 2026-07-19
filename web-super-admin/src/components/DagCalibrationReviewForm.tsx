"use client";

import { Check, Clock3, Loader2, Save, ShieldX } from "lucide-react";
import { useActionState, useState } from "react";
import { labelDagCalibrationReviewAction } from "@/lib/actions";
import { emptyState } from "@/lib/action-state";

const allowReasons = [
  ["acceptable_clothing", "Vestimenta aceptable"],
  ["product_without_person", "Producto sin persona"],
  ["mannequin", "Maniquí"],
  ["object_misclassified", "Objeto confundido con el cuerpo"],
  ["false_positive", "Falso positivo del clasificador"],
  ["allow_other", "Otro motivo para permitir"],
] as const;

const blockReasons = [
  ["pronounced_neckline", "Escote pronunciado"],
  ["exposed_shoulders", "Hombros expuestos"],
  ["sleeves_above_elbow", "Manga por encima del codo"],
  ["hem_above_knee", "Falda o pantalón por encima de la rodilla"],
  ["tight_or_transparent", "Ropa ajustada o transparente"],
  ["swimwear", "Traje de baño"],
  ["lingerie", "Lencería"],
  ["nudity", "Desnudez"],
  ["block_other", "Otro motivo para difuminar"],
] as const;

type ReviewDecision = "allow" | "block" | null;

export function DagCalibrationReviewForm({ reviewId }: { reviewId: string }) {
  const [state, action, pending] = useActionState(labelDagCalibrationReviewAction, emptyState);
  const [decision, setDecision] = useState<ReviewDecision>(null);
  const reasons = decision === "allow" ? allowReasons : blockReasons;
  return (
    <form action={action} className="grid gap-3">
      <input type="hidden" name="reviewId" value={reviewId} />
      <input type="hidden" name="decision" value={decision ?? ""} />
      <fieldset className="grid gap-2" disabled={pending}>
        <legend className="mb-1 text-sm font-semibold text-ink">¿Qué debe hacer DAG?</legend>
        <div className="grid grid-cols-2 gap-2">
          <button
            type="button"
            aria-pressed={decision === "allow"}
            className={`button border ${decision === "allow" ? "border-teal-500 bg-teal-100 text-teal-900" : "border-teal-200 bg-teal-50 text-teal-800"}`}
            onClick={() => setDecision("allow")}
          >
            <Check className="h-4 w-4" /> Permitir
          </button>
          <button
            type="button"
            aria-pressed={decision === "block"}
            className={`button border ${decision === "block" ? "border-red-500 bg-red-100 text-red-900" : "border-red-200 bg-red-50 text-red-800"}`}
            onClick={() => setDecision("block")}
          >
            <ShieldX className="h-4 w-4" /> Difuminar
          </button>
        </div>
      </fieldset>
      {decision ? (
        <>
          <label className="grid gap-1 text-sm font-semibold text-ink">
            {decision === "allow" ? "Motivo para permitir" : "Motivo para difuminar"}
            <select key={decision} className="input" name="reason" required defaultValue="">
              <option value="" disabled>Seleccionar motivo</option>
              {reasons.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select>
          </label>
          <label className="grid gap-1 text-sm font-semibold text-ink">
            Nota opcional
            <input className="input" name="note" maxLength={500} placeholder="Detalle que ayude a entender el criterio" />
          </label>
          <button className="button button-primary" disabled={pending}>
            {pending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
            {pending ? "Guardando" : "Guardar decisión"}
          </button>
        </>
      ) : (
        <p className="flex items-center gap-2 text-xs text-slate-500"><Clock3 className="h-4 w-4" /> Podés dejarla pendiente y revisarla después.</p>
      )}
      {state.message ? <p className={state.ok ? "text-xs text-teal-700" : "text-xs text-danger"}>{state.message}</p> : null}
    </form>
  );
}
