import { AlertTriangle, CheckCircle2, ShieldAlert } from "lucide-react";
import { EmptyState } from "@/components/EmptyState";
import { ArchiveButton } from "@/components/ArchiveButton";
import { archiveAlertGroupAction } from "@/lib/actions";
import { listProtectionAlerts } from "@/lib/data";
import { formatDate } from "@/lib/utils";
import type { ProtectionAlertEvent } from "@/lib/types";

export default async function AlertsPage() {
  const alerts = await listProtectionAlerts();
  const blockedAttempts = alerts.filter((alert) => alert.alert_type === "tamper_attempt").length;
  const confirmed = alerts.length - blockedAttempts;
  const grouped = alerts.reduce<Map<string, ProtectionAlertEvent[]>>((result, alert) => {
    result.set(alert.device_id, [...(result.get(alert.device_id) ?? []), alert]);
    return result;
  }, new Map());
  const groups = Array.from(grouped, ([deviceId, events]) => ({ deviceId, events }));

  return (
    <main className="mx-auto grid max-w-5xl gap-5 px-4 py-5 lg:px-6">
      <div>
        <h1 className="text-2xl font-semibold text-ink">Alertas de protección</h1>
        <p className="mt-1 text-sm text-muted">Intentos bloqueados y desactivaciones confirmadas, sin historial de navegación.</p>
      </div>
      <section className="grid gap-3 sm:grid-cols-2">
        <AlertMetric label="Intentos bloqueados" value={blockedAttempts} icon={ShieldAlert} />
        <AlertMetric label="Incidentes confirmados" value={confirmed} icon={AlertTriangle} />
      </section>
      {alerts.length === 0 ? (
        <EmptyState title="Sin alertas" body="Los eventos de protección aparecerán aquí cuando existan." />
      ) : (
        <section className="grid gap-3">
          {groups.map(({ deviceId, events }) => {
            const latest = events[0];
            const attempts = events.filter((event) => event.alert_type === "tamper_attempt").length;
            const degraded = events.some((event) => !["tamper_attempt", "maintenance_requested"].includes(event.alert_type));
            return <article key={deviceId} className={`rounded-2xl border bg-white p-4 shadow-soft ${degraded ? "border-red-200" : "border-line"}`}>
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="font-bold text-ink">{latest.device_name}</p>
                  <p className="mt-1 text-sm text-muted">{latest.community_name}</p>
                </div>
                <span className={degraded ? "rounded-full bg-red-100 px-2.5 py-1 text-xs font-bold text-danger" : "rounded-full bg-amber-50 px-2.5 py-1 text-xs font-bold text-amber-800"}>
                  {degraded ? "Protección desactivada" : "Intentos bloqueados"}
                </span>
              </div>
              <div className="mt-4 grid gap-2 text-sm">
                {attempts > 0 ? <p className="font-semibold text-amber-800">Intentó modificar la protección {attempts} {attempts === 1 ? "vez" : "veces"}.</p> : null}
                {degraded ? <p className="font-semibold text-danger">Se confirmó una desactivación o degradación de la protección.</p> : null}
                <p className="text-xs text-slate-500">{events.length} eventos · último {formatDate(latest.created_at)}</p>
              </div>
              <div className="mt-4"><ArchiveButton id={deviceId} action={archiveAlertGroupAction} label="Borrar alertas" /></div>
            </article>;
          })}
        </section>
      )}
    </main>
  );
}

function AlertMetric({ label, value, icon: Icon }: { label: string; value: number; icon: typeof CheckCircle2 }) {
  return (
    <div className="rounded-md border border-line bg-white p-4 shadow-soft">
      <div className="flex items-center justify-between gap-2">
        <p className="text-sm font-semibold text-muted">{label}</p>
        <Icon className="h-5 w-5 text-accent" />
      </div>
      <p className="mt-2 text-2xl font-bold text-ink">{value}</p>
    </div>
  );
}
