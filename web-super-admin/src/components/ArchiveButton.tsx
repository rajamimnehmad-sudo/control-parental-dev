"use client";

import { Loader2, Trash2 } from "lucide-react";
import { useActionState } from "react";
import type { ActionState } from "@/lib/action-state";
import { emptyState } from "@/lib/action-state";

export function ArchiveButton({ id, action, label }: { id: string; action: (state: ActionState, data: FormData) => Promise<ActionState>; label: string }) {
  const [state, formAction, pending] = useActionState(action, emptyState);
  return (
    <form action={formAction} className="grid justify-items-end gap-1">
      <input type="hidden" name="id" value={id} />
      <button className="button border border-red-200 bg-red-50 text-danger hover:bg-red-100" type="submit" disabled={pending}>
        {pending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}{label}
      </button>
      {state.message ? <span className={state.ok ? "text-xs text-teal-700" : "text-xs text-danger"}>{state.message}</span> : null}
    </form>
  );
}
