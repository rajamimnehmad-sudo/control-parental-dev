import { AlertTriangle, CheckCircle2, Database, ShieldAlert } from "lucide-react";
import { DomainListActions } from "@/components/DomainListActions";
import { getDomainListStatus, protectionState } from "@/lib/domain-list";
import { compactNumber, formatDate } from "@/lib/utils";

export default async function DomainListPage() {
  const status = await getDomainListStatus();
  const state = protectionState(status);
  const data = status.payload;
  const stateLabel = state === "active" ? "Activa" : state === "stale" ? "Desactualizada" : state === "error-active" ? "Error con base activa" : "Sin base valida";
  const StateIcon = state === "active" ? CheckCircle2 : state === "critical" ? ShieldAlert : AlertTriangle;
  const ageDays = data ? Math.max(0, Math.floor((Date.now() - new Date(data.generatedAt).getTime()) / 86_400_000)) : null;
  return <main className="mx-auto flex max-w-7xl flex-col gap-6 px-4 py-6 lg:px-6">
    <section className="flex flex-col gap-4 border-b border-line pb-6 sm:flex-row sm:items-start sm:justify-between">
      <div><p className="text-sm font-semibold text-accent">Proteccion Web</p><h1 className="mt-1 text-2xl font-bold text-ink">Base de dominios</h1><p className="mt-2 max-w-2xl text-sm text-slate-600">Lista UT1 firmada usada localmente por los dispositivos DEV.</p></div>
      <div className="flex items-center gap-3 rounded-md border border-line bg-white px-4 py-3"><StateIcon className={`h-5 w-5 ${state === "active" ? "text-emerald-600" : "text-amber-600"}`} /><div><p className="text-xs text-slate-500">Estado</p><p className="font-semibold text-ink">{stateLabel}</p></div></div>
    </section>
    {state === "error-active" && data ? <div className="rounded-md border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">Actualizacion fallida. La proteccion sigue activa con la version {data.version} del {formatDate(data.generatedAt)}.</div> : null}
    {state === "critical" ? <div className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm font-semibold text-red-800">No existe una base valida firmada. Estado critico.</div> : null}
    <section className="grid gap-px overflow-hidden rounded-md border border-line bg-line sm:grid-cols-2 lg:grid-cols-4">
      <Metric label="Fuente" value={data?.source ?? "UT1"} /><Metric label="Ambiente" value={data?.environment ?? "DEV"} />
      <Metric label="Version publicada" value={data ? String(data.version) : "-"} /><Metric label="Firma" value={status.signatureValid ? "Valida" : "Invalida"} />
      <Metric label="Fecha de la fuente" value={data ? formatDate(data.sourceDate) : "-"} /><Metric label="Ultima actualizacion" value={data ? formatDate(data.lastSuccessfulRun) : "-"} />
      <Metric label="Proxima programada" value={data ? formatDate(data.nextScheduledAt) : "-"} /><Metric label="Antiguedad" value={ageDays === null ? "-" : `${ageDays} dias`} />
      <Metric label="Cantidad total UT1" value={data ? compactNumber(data.totalCount) : "-"} /><Metric label="adult" value={data ? compactNumber(data.countByCategory.adult) : "-"} />
      <Metric label="mixed_adult" value={data ? compactNumber(data.countByCategory.mixed_adult) : "-"} /><Metric label="Excepciones educativas" value={data ? compactNumber(data.educationalExceptionCount) : "-"} />
      <Metric label="Tamano descargable" value={data ? formatBytes(data.sizeBytes) : "-"} /><Metric label="SHA-256" value={data ? `${data.sha256.slice(0, 12)}...` : "-"} mono />
      <Metric label="Dominio canario" value={data?.devCanary ?? "coca.com"} mono /><Metric label="Estado del canario" value={data?.canaryIncluded ? "Incluido" : "Ausente"} />
    </section>
    <section className="flex flex-col gap-4 border-t border-line pt-6"><div className="flex items-center gap-2"><Database className="h-5 w-5 text-accent" /><h2 className="text-lg font-semibold text-ink">Operaciones DEV</h2></div><DomainListActions /><p className="text-sm text-slate-600">Ultimo error: {status.operational?.lastError ?? data?.lastError ?? "Ninguno"}</p></section>
  </main>;
}

function Metric({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return <div className="bg-white p-4"><p className="text-xs font-medium text-slate-500">{label}</p><p className={`mt-1 text-sm font-semibold text-ink ${mono ? "font-mono" : ""}`}>{value}</p></div>;
}

function formatBytes(bytes: number) { return bytes >= 1_048_576 ? `${(bytes / 1_048_576).toFixed(1)} MB` : `${Math.round(bytes / 1024)} KB`; }
