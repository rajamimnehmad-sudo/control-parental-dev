import { AlertTriangle, CheckCircle2, Database, LockKeyhole, RefreshCw, ShieldAlert, ShieldCheck } from "lucide-react";
import { DomainListActions } from "@/components/DomainListActions";
import { DomainLookup } from "@/components/DomainLookup";
import { DomainTestSamples } from "@/components/DomainTestSamples";
import { getDomainListStatus, protectionState } from "@/lib/domain-list";
import { compactNumber, formatDate } from "@/lib/utils";

export default async function DomainListPage() {
  const status = await getDomainListStatus();
  const state = protectionState(status);
  const data = status.payload;
  const stateLabel = state === "active" ? "Protección activa" : state === "stale" ? "Base desactualizada" : state === "error-active" ? "Activa con incidencia" : "Estado crítico";
  const StateIcon = state === "active" ? CheckCircle2 : state === "critical" ? ShieldAlert : AlertTriangle;
  const ageDays = data ? Math.max(0, Math.floor((Date.now() - new Date(data.generatedAt).getTime()) / 86_400_000)) : null;

  return (
    <main className="page-shell">
      <section className="page-heading">
        <div><p className="eyebrow">Seguridad de navegación</p><h1>Protección web</h1><p>Supervisá la base que bloquea contenidos sensibles en todos los dispositivos, incluso sin conexión.</p></div>
        <div className={`flex items-center gap-3 rounded-2xl border px-4 py-3 shadow-panel ${state === "active" ? "border-emerald-200 bg-emerald-50" : state === "critical" ? "border-red-200 bg-red-50" : "border-amber-200 bg-amber-50"}`}>
          <StateIcon className={`h-5 w-5 ${state === "active" ? "text-emerald-600" : state === "critical" ? "text-red-600" : "text-amber-600"}`} />
          <div><p className="text-[10px] font-bold uppercase tracking-wider text-slate-500">Estado global</p><p className="text-sm font-bold text-ink">{stateLabel}</p></div>
        </div>
      </section>

      {state === "error-active" && data ? <div className="rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">La última actualización falló, pero la protección continúa activa con la versión {data.version}.</div> : null}
      {state === "critical" ? <div className="rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-semibold text-red-800">No existe una base válida firmada. La protección requiere atención inmediata.</div> : null}

      <section className="grid gap-4 md:grid-cols-3">
        <Metric label="Dominios protegidos" value={data ? compactNumber(data.totalCount) : "—"} help="Cobertura activa en los teléfonos" icon={ShieldCheck} />
        <Metric label="Última actualización" value={data ? formatDate(data.lastSuccessfulRun) : "—"} help={ageDays === null ? "Sin información" : ageDays === 0 ? "Actualizada hoy" : `Hace ${ageDays} días`} icon={RefreshCw} />
        <Metric label="Tamaño de descarga" value={data ? formatBytes(data.sizeBytes) : "—"} help="Sólo se descarga al cambiar la versión" icon={Database} />
      </section>

      <section className="grid gap-6 xl:grid-cols-[minmax(0,1.1fr)_minmax(360px,.9fr)]">
        <DomainLookup />
        <DomainTestSamples domains={data?.testDomains ?? {}} />
      </section>

      <details className="group panel">
        <summary className="flex cursor-pointer list-none items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-slate-100 text-slate-600"><LockKeyhole className="h-5 w-5" /></span>
            <div><h2 className="section-title">Administración avanzada</h2><p className="muted mt-0.5">Actualizaciones manuales, canarios y detalles técnicos.</p></div>
          </div>
          <span className="text-sm font-semibold text-accent group-open:hidden">Abrir</span>
          <span className="hidden text-sm font-semibold text-accent group-open:inline">Cerrar</span>
        </summary>
        <div className="mt-5 grid gap-6 border-t border-line pt-5">
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Technical label="Fuente" value={data?.source ?? "UT1"} />
            <Technical label="Ambiente" value={data?.environment ?? "DEV"} />
            <Technical label="Versión" value={data ? String(data.version) : "—"} />
            <Technical label="Firma digital" value={status.signatureValid ? "Válida" : "Inválida"} />
            <Technical label="Contenido adulto" value={data ? compactNumber(data.countByCategory.adult) : "—"} />
            <Technical label="Contenido mixto" value={data ? compactNumber(data.countByCategory.mixed_adult) : "—"} />
            <Technical label="Excepciones educativas" value={data ? compactNumber(data.educationalExceptionCount) : "—"} />
            <Technical label="Prueba interna" value={data?.canaryIncluded ? "Correcta" : "No activa"} />
          </div>
          <div className="border-t border-line pt-5">
            <h3 className="font-bold text-ink">Acciones de mantenimiento</h3>
            <p className="muted mt-1">La protección actual permanece funcionando durante estas operaciones.</p>
            <div className="mt-4"><DomainListActions /></div>
            <p className="mt-4 text-xs text-slate-500">Último error: {status.operational?.lastError ?? data?.lastError ?? "Ninguno"}</p>
          </div>
        </div>
      </details>
    </main>
  );
}

function Metric({ label, value, help, icon: Icon }: { label: string; value: string; help: string; icon: typeof ShieldCheck }) {
  return <article className="metric-card"><div className="flex items-start justify-between gap-3"><div><p className="metric-label">{label}</p><p className="mt-3 text-2xl font-bold tracking-tight text-ink">{value}</p><p className="mt-1 text-xs text-slate-500">{help}</p></div><span className="flex h-10 w-10 items-center justify-center rounded-xl bg-teal-50 text-accent"><Icon className="h-5 w-5" /></span></div></article>;
}

function Technical({ label, value }: { label: string; value: string }) {
  return <div className="rounded-xl bg-slate-50 p-3"><p className="text-[10px] font-bold uppercase tracking-wider text-slate-400">{label}</p><p className="mt-1 text-sm font-bold text-ink">{value}</p></div>;
}

function formatBytes(bytes: number) {
  return bytes >= 1_048_576 ? `${(bytes / 1_048_576).toFixed(1)} MB` : `${Math.round(bytes / 1024)} KB`;
}
