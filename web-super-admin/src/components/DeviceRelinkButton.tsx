"use client";

import { Check, Copy, KeyRound, Loader2 } from "lucide-react";
import { useState } from "react";

export function DeviceRelinkButton({ communityId, deviceId }: { communityId: string; deviceId: string }) {
  const [loading, setLoading] = useState(false);
  const [code, setCode] = useState("");
  const [expiresAt, setExpiresAt] = useState("");
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState("");

  async function createCode() {
    setLoading(true);
    setError("");
    setCopied(false);
    const response = await fetch(`/api/communities/${communityId}/devices/${deviceId}/relink`, { method: "POST" });
    const payload = await response.json().catch(() => ({}));
    setLoading(false);
    if (!response.ok) {
      setError(payload.error ?? "No se pudo generar el token");
      return;
    }
    setCode(payload.activationCode);
    setExpiresAt(payload.expiresAt);
  }

  async function copyCode() {
    await navigator.clipboard.writeText(code);
    setCopied(true);
  }

  if (code) {
    return (
      <div className="grid gap-2 rounded-md border border-amber-200 bg-amber-50 p-3">
        <p className="text-xs font-semibold text-amber-900">Token de un solo uso · vence en 30 minutos</p>
        <div className="flex items-center justify-between gap-3">
          <code className="text-lg font-bold tracking-[0.18em] text-ink">{code}</code>
          <button className="button button-secondary" type="button" onClick={copyCode}>
            {copied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
            {copied ? "Copiado" : "Copiar"}
          </button>
        </div>
        <p className="text-xs text-slate-600">
          El teléfono anterior conserva acceso hasta que el nuevo complete su primera sincronización. Vence: {new Date(expiresAt).toLocaleTimeString("es-AR", { hour: "2-digit", minute: "2-digit" })}.
        </p>
        <button className="text-left text-xs font-bold text-accent" type="button" onClick={() => setCode("")}>Ocultar token</button>
      </div>
    );
  }

  return (
    <div className="grid gap-1">
      <button className="button button-secondary" type="button" onClick={createCode} disabled={loading}>
        {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <KeyRound className="h-4 w-4" />}
        {loading ? "Generando" : "Volver a enlazar"}
      </button>
      {error ? <span className="text-xs font-medium text-danger">{error}</span> : null}
    </div>
  );
}
