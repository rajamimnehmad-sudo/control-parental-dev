"use client";

import { Check, Loader2, ShieldX } from "lucide-react";
import { useActionState } from "react";
import { labelDagCalibrationReviewAction } from "@/lib/actions";
import { emptyState } from "@/lib/action-state";

export function DagCalibrationReviewForm({ reviewId }: { reviewId: string }) {
  const [state, action, pending] = useActionState(labelDagCalibrationReviewAction, emptyState);
  return (
    <form action={action} className="grid gap-3">
      <input type="hidden" name="reviewId" value={reviewId} />
      <fieldset className="grid gap-2" disabled={pending}>
        <legend className="mb-1 text-sm font-semibold text-ink">¿Qué debe hacer DAG?</legend>
        <div className="grid grid-cols-2 gap-2">
          <button
            name="decision"
            value="allow"
            className="button border border-teal-200 bg-teal-50 text-teal-800"
          >
            {pending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check className="h-4 w-4" />} Permitida
          </button>
          <button
            name="decision"
            value="block"
            className="button border border-red-200 bg-red-50 text-red-800"
          >
            {pending ? <Loader2 className="h-4 w-4 animate-spin" /> : <ShieldX className="h-4 w-4" />} Prohibida
          </button>
        </div>
      </fieldset>
      <p className="text-xs text-slate-500">Un toque guarda la etiqueta; no hace falta elegir un motivo.</p>
      {state.message ? <p className={state.ok ? "text-xs text-teal-700" : "text-xs text-danger"}>{state.message}</p> : null}
    </form>
  );
}
