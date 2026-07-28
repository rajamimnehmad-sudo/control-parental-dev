"use client";

import { Copy, RefreshCw } from "lucide-react";
import { useMemo, useState } from "react";

type Sample = { domain: string; category: string };

export function DomainTestSamples({ domains }: { domains: Record<string, string[]> }) {
  const all = useMemo(
    () => Object.entries(domains).flatMap(([category, values]) => values.map((domain) => ({ domain, category }))),
    [domains],
  );
  const [selected, setSelected] = useState(() => shuffled(all).slice(0, 3));

  if (all.length === 0) {
    return <section className="panel"><h2 className="section-title">Páginas de prueba</h2><p className="muted">La próxima actualización de la base preparará ejemplos verificables.</p></section>;
  }
  return (
    <section className="panel">
      <div className="flex items-start justify-between gap-3">
        <div><h2 className="section-title">Páginas de prueba</h2><p className="muted mt-1">Tres dominios elegidos de la base activa. Copiá y probá uno por vez.</p></div>
        <button className="button button-secondary" type="button" onClick={() => setSelected(shuffled(all).slice(0, 3))}><RefreshCw className="h-4 w-4" />Otras 3</button>
      </div>
      <div className="mt-4 grid gap-3">
        {selected.map((sample) => <SampleRow key={sample.domain} sample={sample} />)}
      </div>
    </section>
  );
}

function SampleRow({ sample }: { sample: Sample }) {
  const [copied, setCopied] = useState(false);
  return (
    <div className="flex flex-col gap-3 rounded-xl border border-line bg-slate-50 p-3 sm:flex-row sm:items-center sm:justify-between">
      <div className="min-w-0"><p className="truncate font-semibold text-ink">{sample.domain}</p><p className="text-xs text-slate-500">{categoryLabel(sample.category)}</p></div>
      <button className="button button-secondary" type="button" onClick={async () => { await navigator.clipboard.writeText(sample.domain); setCopied(true); }}>
        <Copy className="h-4 w-4" />{copied ? "Copiado" : "Copiar"}
      </button>
    </div>
  );
}

function shuffled(values: Sample[]) {
  const copy = [...values];
  for (let index = copy.length - 1; index > 0; index -= 1) {
    const other = Math.floor(Math.random() * (index + 1));
    [copy[index], copy[other]] = [copy[other], copy[index]];
  }
  return copy;
}

function categoryLabel(value: string) {
  return ({ adult: "Contenido adulto", mixed_adult: "Contenido mixto", gambling: "Apuestas", drugs: "Drogas", piracy_torrents: "Piratería" } as Record<string, string>)[value] ?? value;
}
