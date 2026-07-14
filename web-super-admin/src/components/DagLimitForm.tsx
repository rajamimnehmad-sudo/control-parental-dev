"use client";

import { Save } from "lucide-react";
import { useActionState } from "react";
import { emptyState } from "@/lib/action-state";
import { updateDagLimitAction } from "@/lib/actions";

export function DagLimitForm({ communityId, monthlyLimit }: { communityId: string; monthlyLimit: number }) {
  const [state, action, pending] = useActionState(updateDagLimitAction, emptyState);

  return (
    <form action={action} className="flex flex-wrap items-end gap-2">
      <input type="hidden" name="communityId" value={communityId} />
      <label className="field min-w-40">
        Cupo por dispositivo
        <input
          className="input w-40"
          name="monthlyLimit"
          type="number"
          min="1"
          max="100000"
          defaultValue={monthlyLimit}
          required
        />
      </label>
      <button className="button button-secondary" type="submit" disabled={pending}>
        <Save className="h-4 w-4" />
        {pending ? "Guardando" : "Guardar"}
      </button>
      {state.message ? (
        <p className={state.ok ? "w-full text-xs font-medium text-teal-700" : "w-full text-xs font-medium text-danger"}>
          {state.message}
        </p>
      ) : null}
    </form>
  );
}
