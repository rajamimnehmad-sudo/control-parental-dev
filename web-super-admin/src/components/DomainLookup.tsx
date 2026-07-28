"use client";

import { Search, ShieldPlus } from "lucide-react";
import { FormEvent, useState } from "react";

type LookupResult = {
  normalized: string;
  matchedDomain: string | null;
  category: string | null;
  source: "manual" | "canary" | "compiled" | null;
  version: number | null;
};

const categories = [
  ["adult", "Contenido adulto"],
  ["gambling", "Apuestas"],
  ["drugs", "Drogas"],
  ["piracy_torrents", "Piratería y torrents"],
] as const;

export function DomainLookup() {
  const [domain, setDomain] = useState("");
  const [category, setCategory] = useState("adult");
  const [result, setResult] = useState<LookupResult | null>(null);
  const [pending, setPending] = useState<"lookup" | "add" | null>(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  async function lookup(event?: FormEvent) {
    event?.preventDefault();
    setPending("lookup"); setError(""); setMessage("");
    const response = await fetch(`/api/web-protection/domain-list?domain=${encodeURIComponent(domain)}`);
    const payload = await response.json().catch(() => null);
    setPending(null);
    if (!response.ok) { setError(payload?.error ?? "No se pudo consultar."); return; }
    setResult(payload);
  }

  async function addDomain() {
    if (!window.confirm(`Agregar ${domain} a la base DEV y solicitar una actualización?`)) return;
    setPending("add"); setError(""); setMessage("");
    const response = await fetch("/api/web-protection/domain-list", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ action: "add_domain", domain, category }),
    });
    const payload = await response.json().catch(() => null);
    setPending(null);
    if (!response.ok) { setError(payload?.error ?? "No se pudo agregar."); return; }
    setMessage("Dominio agregado a DEV. La actualización de la base quedó en cola.");
    await lookup();
  }

  return (
    <section className="rounded-2xl border border-line bg-white p-4 shadow-soft">
      <div className="flex items-center gap-2"><Search className="h-5 w-5 text-accent" /><h2 className="text-lg font-semibold text-ink">Consultar un dominio</h2></div>
      <p className="mt-1 text-sm text-slate-600">Busca coincidencias exactas y por dominio padre en la versión DEV activa.</p>
      <form className="mt-4 flex flex-col gap-3 sm:flex-row" onSubmit={lookup}>
        <input className="input flex-1" value={domain} onChange={(event) => setDomain(event.target.value)} placeholder="example.com" aria-label="Dominio" />
        <button className="button button-primary" disabled={pending !== null || !domain.trim()}><Search className="h-4 w-4" />{pending === "lookup" ? "Buscando…" : "Buscar"}</button>
      </form>
      {result ? <div className={`mt-4 rounded-xl border p-3 text-sm ${result.matchedDomain ? "border-amber-200 bg-amber-50 text-amber-950" : "border-emerald-200 bg-emerald-50 text-emerald-950"}`}>
        <p className="font-semibold">{result.matchedDomain ? "Está bloqueado" : "No está en la base"}</p>
        <p>Normalizado: {result.normalized}</p>
        {result.matchedDomain ? <p>Coincidencia: {result.matchedDomain} · categoría {result.category} · fuente {result.source} · versión {result.version}</p> : null}
      </div> : null}
      {result && !result.matchedDomain ? <div className="mt-4 grid gap-3 sm:grid-cols-[1fr_auto]">
        <select className="input" value={category} onChange={(event) => setCategory(event.target.value)} aria-label="Categoría">
          {categories.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
        <button type="button" className="button button-secondary" disabled={pending !== null} onClick={addDomain}><ShieldPlus className="h-4 w-4" />{pending === "add" ? "Agregando…" : "Agregar a DEV"}</button>
      </div> : null}
      {message ? <p className="mt-3 text-sm font-medium text-emerald-700">{message}</p> : null}
      {error ? <p className="mt-3 text-sm font-medium text-red-700">{error}</p> : null}
    </section>
  );
}
