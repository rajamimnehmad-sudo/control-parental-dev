"use client";

import { AlertTriangle, CheckCircle2, KeyRound, Loader2, X } from "lucide-react";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { CopyButton } from "@/components/CopyButton";

type TokenResult = {
  communityAdminId: string;
  activationCode: string;
  expiresAt: string;
};

export function AdminTokenForm({ communityId, initialToken }: { communityId: string; initialToken?: TokenResult | null }) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [token, setToken] = useState<TokenResult | null>(initialToken ?? null);
  const storageKey = useMemo(() => `last-admin-token:${communityId}`, [communityId]);
  const cookieName = useMemo(() => `last-admin-token-${communityId}`, [communityId]);

  useEffect(() => {
    if (initialToken?.activationCode) {
      window.localStorage.setItem(storageKey, JSON.stringify(initialToken));
      setToken(initialToken);
      return;
    }
    const saved = window.localStorage.getItem(storageKey);
    if (!saved) return;

    try {
      const parsed = JSON.parse(saved) as TokenResult;
      if (parsed.activationCode && parsed.expiresAt && new Date(parsed.expiresAt).getTime() > Date.now()) {
        setToken(parsed);
      }
    } catch {
      window.localStorage.removeItem(storageKey);
    }
  }, [initialToken, storageKey]);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoading(true);
    setError("");
    setToken(null);

    const form = new FormData(event.currentTarget);
    const payload = await fetch(`/api/communities/${communityId}/admins`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ displayName: form.get("displayName"), ttlMinutes: form.get("ttlMinutes") }),
    }).then(async (response) => {
      const body = await response.json().catch(() => null);
      if (!response.ok) {
        throw new Error(body?.error ?? "No se pudo crear el administrador");
      }
      return body;
    }).catch((requestError: unknown) => {
      const message = requestError instanceof Error ? requestError.message : "No se pudo crear el administrador";
      setError(message);
      return null;
    });

    setLoading(false);

    if (!payload) {
      return;
    }

    if (!payload.activationCode) {
      setError("El servidor creo el administrador, pero no devolvio el token. Borralo y genera uno nuevo.");
      return;
    }

    const nextToken = payload as TokenResult;
    window.localStorage.setItem(storageKey, JSON.stringify(nextToken));
    event.currentTarget.reset();
    setToken(nextToken);
    window.requestAnimationFrame(() => window.scrollTo({ top: 0, behavior: "smooth" }));
  }

  function clearToken() {
    window.localStorage.removeItem(storageKey);
    document.cookie = `${cookieName}=; Max-Age=0; path=/communities/${communityId}; SameSite=Lax; Secure`;
    setToken(null);
  }

  return (
    <div className="grid gap-4">
      {token ? <TokenBanner token={token} onClear={clearToken} /> : null}
      <div className="grid gap-4 rounded-md border border-line bg-white p-4 shadow-soft">
        <form className="grid gap-4 lg:grid-cols-3" action={`/api/communities/${communityId}/admins`} method="post" onSubmit={onSubmit}>
          <label className="field">
            Referencia del admin
            <input className="input" name="displayName" placeholder="Admin principal" required />
          </label>
          <label className="field">
            Validez token
            <select className="input" name="ttlMinutes" defaultValue="1440">
              <option value="60">1 hora</option>
              <option value="1440">24 horas</option>
              <option value="10080">7 dias</option>
            </select>
          </label>
          <div className="flex items-end">
            <button className="button button-primary" type="submit" disabled={loading}>
              {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <KeyRound className="h-4 w-4" />}
              {loading ? "Generando" : "Generar y mostrar token"}
            </button>
          </div>
        </form>
        {error ? <p className="rounded-md bg-red-50 px-3 py-2 text-sm font-medium text-danger">{error}</p> : null}
        {token ? (
          <TokenPanel token={token} onClear={clearToken} />
        ) : (
          <div className="rounded-md border border-amber-200 bg-amber-50 p-4 text-sm font-semibold text-amber-950">
            Cuando generes un admin, el token aparecerá aca y arriba de la pantalla.
          </div>
        )}
      </div>
    </div>
  );
}

function TokenBanner({ token, onClear }: { token: TokenResult; onClear: () => void }) {
  return (
    <div className="sticky top-3 z-20 rounded-md border-2 border-teal-600 bg-white p-4 shadow-lg">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex items-start gap-3">
          <CheckCircle2 className="mt-1 h-5 w-5 shrink-0 text-teal-700" />
          <div>
            <p className="text-sm font-bold uppercase text-teal-800">Token de admin generado</p>
            <code className="mt-2 block rounded-md bg-teal-950 px-4 py-3 text-center text-3xl font-black tracking-widest text-white">
              {token.activationCode}
            </code>
            <p className="mt-2 text-sm font-semibold text-slate-700">Vence: {new Date(token.expiresAt).toLocaleString("es-AR")}</p>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <CopyButton value={token.activationCode} />
          <button className="button button-secondary" type="button" onClick={onClear}>
            <X className="h-4 w-4" />
            Ocultar
          </button>
        </div>
      </div>
    </div>
  );
}

function TokenPanel({ token, onClear }: { token: TokenResult; onClear: () => void }) {
  return (
    <div className="rounded-md border-2 border-teal-500 bg-teal-50 p-5">
      <div className="flex items-center gap-2 text-teal-900">
        <CheckCircle2 className="h-5 w-5" />
        <p className="text-base font-bold">Token creado para App Admin</p>
      </div>
      <div className="mt-4 grid gap-3 md:grid-cols-[1fr_auto] md:items-center">
        <code className="block rounded-md border border-teal-200 bg-white px-4 py-4 text-center text-3xl font-black tracking-widest text-ink">
          {token.activationCode}
        </code>
        <CopyButton value={token.activationCode} />
      </div>
      <div className="mt-3 flex items-start gap-2 rounded-md bg-white p-3 text-sm font-semibold text-teal-950">
        <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-amber-600" />
        <p>Copiá este token ahora. También queda visible en este navegador hasta que toques Ocultar o hasta que venza.</p>
      </div>
      <div className="mt-3 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <p className="text-sm font-semibold text-teal-950">Vence: {new Date(token.expiresAt).toLocaleString("es-AR")}</p>
        <button className="button button-secondary" type="button" onClick={onClear}>
          Ocultar token
        </button>
      </div>
    </div>
  );
}
