"use client";

import { Loader2, Trash2 } from "lucide-react";
import { useState } from "react";

type DeleteCommunityButtonProps = {
  communityId: string;
  communityName: string;
};

export function DeleteCommunityButton({ communityId, communityName }: DeleteCommunityButtonProps) {
  const [confirmName, setConfirmName] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function onDelete() {
    if (confirmName.trim() !== communityName) {
      setError("Escribí el nombre exacto de la comunidad para confirmar.");
      return;
    }

    setLoading(true);
    setError("");
    const response = await fetch(`/api/communities/${communityId}`, { method: "DELETE" });
    const payload = await response.json().catch(() => ({}));
    setLoading(false);

    if (!response.ok) {
      setError(payload.error ?? "No se pudo borrar la comunidad");
      return;
    }

    window.location.href = "/communities";
  }

  return (
    <div className="grid gap-3 rounded-md border border-red-200 bg-red-50 p-4">
      <div>
        <h3 className="text-base font-bold text-danger">Borrar comunidad</h3>
        <p className="mt-1 text-sm text-red-900">
          Oculta la comunidad, sus licencias, admins, tokens, dispositivos y reglas. Esta acción es para limpiar comunidades que ya no se usan.
        </p>
      </div>
      <label className="field text-red-950">
        Para confirmar, escribí: {communityName}
        <input className="input border-red-200" value={confirmName} onChange={(event) => setConfirmName(event.target.value)} />
      </label>
      <button className="button bg-danger text-white hover:bg-red-800" type="button" onClick={onDelete} disabled={loading}>
        {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}
        {loading ? "Borrando" : "Borrar comunidad"}
      </button>
      {error ? <p className="text-sm font-semibold text-danger">{error}</p> : null}
    </div>
  );
}
