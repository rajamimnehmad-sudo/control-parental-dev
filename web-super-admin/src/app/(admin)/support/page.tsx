import { CircleAlert, MessageCircleWarning, Smartphone } from "lucide-react";
import { listSupportReports } from "@/lib/data";
import { formatDate } from "@/lib/utils";

const categoryLabels: Record<string, string> = {
  "dag-images": "DAG · imágenes",
  "dag-navigation": "DAG · navegación",
  "web-protection": "Protección web",
  "app-protection": "Protección de apps",
  accessibility: "Accesibilidad",
  updates: "Actualizaciones",
  activation: "Activación",
  "uninstall-protection": "Desinstalación",
  sync: "Sincronización",
  unclassified: "Sin clasificar",
};

export default async function SupportPage() {
  const reports = await listSupportReports();
  const openReports = reports.filter((report) => report.status === "open");

  return (
    <main className="page-shell">
      <section className="page-heading">
        <div>
          <p className="eyebrow">GloshIA Ayuda</p>
          <h1>Reportes técnicos</h1>
          <p>Problemas sanitizados enviados automáticamente desde las aplicaciones.</p>
        </div>
      </section>

      <section className="grid gap-4 sm:grid-cols-3">
        <ReportMetric label="Abiertos" value={openReports.length} detail="requieren revisión" />
        <ReportMetric label="Total" value={reports.length} detail="últimos 500 reportes" />
        <ReportMetric
          label="Dispositivos"
          value={new Set(reports.map((report) => report.device_id)).size}
          detail="con diagnóstico"
        />
      </section>

      <section className="panel">
        <div className="panel-header">
          <div className="flex items-center gap-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-rose-50 text-rose-700">
              <MessageCircleWarning className="h-5 w-5" />
            </span>
            <div>
              <h2 className="section-title">Actividad reciente</h2>
              <p className="muted mt-0.5">No incluye conversaciones, búsquedas, fotos ni credenciales.</p>
            </div>
          </div>
          <span className="rounded-full bg-slate-100 px-3 py-1.5 text-xs font-bold text-slate-600">
            {reports.length} reportes
          </span>
        </div>

        {reports.length === 0 ? (
          <div className="grid place-items-center py-16 text-center">
            <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-slate-100 text-slate-400">
              <CircleAlert className="h-5 w-5" />
            </span>
            <p className="mt-4 font-semibold text-ink">No hay problemas reportados</p>
            <p className="mt-1 text-sm text-slate-500">Los diagnósticos aparecerán aquí automáticamente.</p>
          </div>
        ) : (
          <div className="mt-1 divide-y divide-line">
            {reports.map((report) => (
              <article key={report.report_id} className="grid gap-3 py-5 lg:grid-cols-[minmax(0,1fr)_auto]">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="font-bold text-ink">{report.device_name}</p>
                    <span className="rounded-full bg-rose-50 px-2.5 py-1 text-[11px] font-bold text-rose-700">
                      {categoryLabels[report.category] ?? report.category}
                    </span>
                    <span className="rounded-full bg-sky-50 px-2.5 py-1 text-[11px] font-bold text-sky-700">
                      App {report.app_role === "user" ? "Usuario" : "Administrador"}
                    </span>
                  </div>
                  <p className="mt-1 text-xs text-slate-500">
                    {report.community_name ?? "Sin comunidad"} · versión {report.app_version_code}
                  </p>
                  <p className="mt-3 max-w-3xl text-sm leading-6 text-slate-700">{report.safe_summary}</p>
                  {report.diagnostic_codes.length > 0 ? (
                    <div className="mt-3 flex flex-wrap gap-2">
                      {report.diagnostic_codes.map((code) => (
                        <span key={code} className="rounded-lg bg-slate-100 px-2 py-1 text-[11px] font-semibold text-slate-600">
                          {code}
                        </span>
                      ))}
                    </div>
                  ) : null}
                  <p className="mt-3 flex items-center gap-1.5 text-xs text-slate-500">
                    <Smartphone className="h-3.5 w-3.5" />
                    {[report.manufacturer, report.model, report.android_version ? `Android ${report.android_version}` : null]
                      .filter(Boolean)
                      .join(" · ") || "Dispositivo sin sincronizar"}{" "}
                    · {formatDate(report.created_at)}
                  </p>
                </div>
                <span className="h-fit rounded-full bg-amber-50 px-3 py-1.5 text-xs font-bold text-amber-700">
                  {report.status === "open" ? "Abierto" : report.status === "reviewing" ? "En revisión" : "Resuelto"}
                </span>
              </article>
            ))}
          </div>
        )}
      </section>
    </main>
  );
}

function ReportMetric({ label, value, detail }: { label: string; value: number; detail: string }) {
  return (
    <article className="metric-card">
      <p className="metric-label">{label}</p>
      <p className="mt-3 text-3xl font-bold tracking-tight text-ink">{value}</p>
      <p className="mt-1 text-xs text-slate-500">{detail}</p>
    </article>
  );
}
