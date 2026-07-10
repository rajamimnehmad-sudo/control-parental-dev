"use client";

import { Loader2, Trash2 } from "lucide-react";
import { useState } from "react";

type DeleteProtectedUserButtonProps = {
  communityId: string;
  protectedUserId: string;
  userName: string;
};

export function DeleteProtectedUserButton({ communityId, protectedUserId, userName }: DeleteProtectedUserButtonProps) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function onDelete() {
    if (!window.confirm(`Borrar definitivamente el usuario "${userName}"?`)) return;

    setLoading(true);
    setError("");

    const response = await fetch(`/api/communities/${communityId}/users/${protectedUserId}`, {
      method: "DELETE",
    });
    const payload = await response.json().catch(() => ({}));
    setLoading(false);

    if (!response.ok) {
      setError(payload.error ?? "No se pudo borrar");
      return;
    }

    window.location.reload();
  }

  return (
    <div className="grid gap-1">
      <button className="button border border-red-200 bg-red-50 text-danger hover:bg-red-100" type="button" onClick={onDelete} disabled={loading}>
        {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}
        Borrar
      </button>
      {error ? <span className="text-xs font-semibold text-danger">{error}</span> : null}
    </div>
  );
}
