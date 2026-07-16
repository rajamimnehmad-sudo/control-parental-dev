"use client";

import { KeyRound, Loader2 } from "lucide-react";
import { useState } from "react";

export function RevokeAdminTokenButton({ communityId, adminId }: { communityId: string; adminId: string }) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function revoke() {
    if (!window.confirm("Revocar el token pendiente? Ya no podrá usarse para activar App Admin.")) return;
    setLoading(true);
    setError("");
    const response = await fetch(`/api/communities/${communityId}/admins/${adminId}`, { method: "PATCH" });
    const payload = await response.json().catch(() => ({}));
    setLoading(false);
    if (!response.ok) {
      setError(payload.error ?? "No se pudo revocar el token");
      return;
    }
    window.location.reload();
  }

  return (
    <div className="grid gap-1">
      <button className="button button-secondary" type="button" onClick={revoke} disabled={loading}>
        {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <KeyRound className="h-4 w-4" />}
        {loading ? "Revocando" : "Revocar token"}
      </button>
      {error ? <span className="text-xs font-medium text-danger">{error}</span> : null}
    </div>
  );
}
