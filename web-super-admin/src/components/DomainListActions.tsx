"use client";

import { RefreshCw, ShieldCheck, ShieldMinus } from "lucide-react";
import { useState } from "react";

type ActionName = "refresh" | "status" | "publish_canary" | "remove_canary";
const labels: Record<ActionName, string> = { refresh: "Actualizar ahora", status: "Volver a comprobar estado", publish_canary: "Publicar canario DEV", remove_canary: "Retirar canario DEV" };

export function DomainListActions() {
  const [pending, setPending] = useState<ActionName | null>(null);
  const [error, setError] = useState("");
  async function execute(action: ActionName) {
    if (!window.confirm(`Confirmar: ${labels[action]}?`)) return;
    setPending(action); setError("");
    const response = await fetch("/api/web-protection/domain-list", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ action }) });
    if (!response.ok) {
      const payload = await response.json().catch(() => null);
      setError(payload?.error ?? "La operacion no pudo completarse."); setPending(null); return;
    }
    window.location.reload();
  }
  return <div className="flex flex-col gap-3"><div className="flex flex-wrap gap-2">
    <button className="button button-primary" disabled={pending !== null} onClick={() => execute("refresh")}><RefreshCw className="h-4 w-4" />{pending === "refresh" ? "Actualizando..." : labels.refresh}</button>
    <button className="button button-secondary" disabled={pending !== null} onClick={() => execute("status")}><RefreshCw className="h-4 w-4" />{labels.status}</button>
    <button className="button button-secondary" disabled={pending !== null} onClick={() => execute("publish_canary")}><ShieldCheck className="h-4 w-4" />{labels.publish_canary}</button>
    <button className="button button-secondary" disabled={pending !== null} onClick={() => execute("remove_canary")}><ShieldMinus className="h-4 w-4" />{labels.remove_canary}</button>
  </div>{error ? <p className="text-sm font-medium text-red-700">{error}</p> : null}</div>;
}
