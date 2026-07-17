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
      <div><p className="text-sm font-semibold text-accent">Protección Web</p><h1 className="mt-1 text-2xl font-bold text-ink">Base de sitios bloqueados</h1><p className="mt-2 max-w-2xl text-sm text-slate-600">Es la lista segura que descargan los teléfonos para bloquear sitios sensibles aun cuando no tienen conexión con el servidor.</p></div>
      <div className="flex items-center gap-3 rounded-md border border-line bg-white px-4 py-3"><StateIcon className={`h-5 w-5 ${state === "active" ? "text-emerald-600" : "text-amber-600"}`} /><div><p className="text-xs text-slate-500">Estado</p><p className="font-semibold text-ink">{stateLabel}</p></div></div>
    </section>
    {state === "error-active" && data ? <div className="rounded-md border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">Actualizacion fallida. La proteccion sigue activa con la version {data.version} del {formatDate(data.generatedAt)}.</div> : null}
    {state === "critical" ? <div className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm font-semibold text-red-800">No existe una base valida firmada. Estado critico.</div> : null}
    <section className="grid gap-3 sm:grid-cols-3">
      <Metric label="Sitios protegidos" value={data ? compactNumber(data.totalCount) : "-"} help="Cantidad de dominios sensibles reconocidos." />
      <Metric label="Última actualización" value={data ? formatDate(data.lastSuccessfulRun) : "-"} help={ageDays === null ? "Sin información" : `Hace ${ageDays} días`} />
      <Metric label="Descarga por teléfono" value={data ? formatBytes(data.sizeBytes) : "-"} help="Se descarga sólo cuando hay una versión nueva." />
    </section>
    <details className="rounded-2xl border border-line bg-white p-4 shadow-soft">
      <summary className="cursor-pointer font-semibold text-ink">Ver detalles técnicos</summary>
      <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Metric label="Fuente" value={data?.source ?? "UT1"} /><Metric label="Ambiente" value={data?.environment ?? "DEV"} />
        <Metric label="Versión" value={data ? String(data.version) : "-"} /><Metric label="Firma digital" value={status.signatureValid ? "Válida" : "Inválida"} />
        <Metric label="Contenido adulto" value={data ? compactNumber(data.countByCategory.adult) : "-"} /><Metric label="Contenido mixto" value={data ? compactNumber(data.countByCategory.mixed_adult) : "-"} />
        <Metric label="Excepciones educativas" value={data ? compactNumber(data.educationalExceptionCount) : "-"} /><Metric label="Prueba interna" value={data?.canaryIncluded ? "Correcta" : "Falló"} />
      </div>
    </details>
    <section className="flex flex-col gap-4 border-t border-line pt-6"><div className="flex items-center gap-2"><Database className="h-5 w-5 text-accent" /><h2 className="text-lg font-semibold text-ink">Actualizar la base</h2></div><p className="text-sm text-slate-600">Usá estas acciones sólo si querés buscar o publicar una versión nueva. La protección actual sigue funcionando mientras tanto.</p><DomainListActions /><p className="text-sm text-slate-600">Último error: {status.operational?.lastError ?? data?.lastError ?? "Ninguno"}</p></section>
  </main>;
}

function Metric({ label, value, help }: { label: string; value: string; help?: string }) {
  return <div className="rounded-2xl border border-line bg-white p-4 shadow-soft"><p className="text-xs font-medium text-slate-500">{label}</p><p className="mt-1 text-lg font-bold text-ink">{value}</p>{help ? <p className="mt-1 text-xs text-slate-500">{help}</p> : null}</div>;
}

function formatBytes(bytes: number) { return bytes >= 1_048_576 ? `${(bytes / 1_048_576).toFixed(1)} MB` : `${Math.round(bytes / 1024)} KB`; }
