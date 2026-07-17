"use client";

import { Check, Loader2, ShieldX } from "lucide-react";
import { useActionState } from "react";
import { labelDagCalibrationReviewAction } from "@/lib/actions";
import { emptyState } from "@/lib/action-state";

const reasons = [
  ["acceptable_clothing", "Vestimenta aceptable"],
  ["pronounced_neckline", "Escote pronunciado"],
  ["exposed_shoulders", "Hombros expuestos"],
  ["tight_or_transparent", "Ropa ajustada o transparente"],
  ["swimwear", "Traje de baño"],
  ["lingerie", "Lencería"],
  ["nudity", "Desnudez"],
  ["product_without_person", "Producto sin persona"],
  ["mannequin", "Maniquí"],
  ["false_positive", "Falso positivo"],
  ["ambiguous", "Caso ambiguo"],
  ["other", "Otro"],
] as const;

export function DagCalibrationReviewForm({ reviewId }: { reviewId: string }) {
  const [state, action, pending] = useActionState(labelDagCalibrationReviewAction, emptyState);
  return (
    <form action={action} className="grid gap-3">
      <input type="hidden" name="reviewId" value={reviewId} />
      <label className="grid gap-1 text-sm font-semibold text-ink">
        Motivo
        <select className="input" name="reason" required defaultValue="">
          <option value="" disabled>Seleccionar motivo</option>
          {reasons.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
      </label>
      <label className="grid gap-1 text-sm font-semibold text-ink">
        Nota opcional
        <input className="input" name="note" maxLength={500} placeholder="Detalle que ayude a entender el criterio" />
      </label>
      <div className="grid grid-cols-2 gap-2">
        <button className="button border border-teal-200 bg-teal-50 text-teal-800" name="decision" value="allow" disabled={pending}>
          {pending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check className="h-4 w-4" />} Permitir
        </button>
        <button className="button border border-red-200 bg-red-50 text-red-800" name="decision" value="block" disabled={pending}>
          {pending ? <Loader2 className="h-4 w-4 animate-spin" /> : <ShieldX className="h-4 w-4" />} Difuminar
        </button>
      </div>
      {state.message ? <p className={state.ok ? "text-xs text-teal-700" : "text-xs text-danger"}>{state.message}</p> : null}
    </form>
  );
}
