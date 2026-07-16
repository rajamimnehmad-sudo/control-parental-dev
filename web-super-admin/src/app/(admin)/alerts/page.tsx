import { AlertTriangle, CheckCircle2, ShieldAlert } from "lucide-react";
import { EmptyState } from "@/components/EmptyState";
import { listProtectionAlerts } from "@/lib/data";
import { formatDate } from "@/lib/utils";

export default async function AlertsPage() {
  const alerts = await listProtectionAlerts();
  const blockedAttempts = alerts.filter((alert) => alert.alert_type === "tamper_attempt").length;
  const confirmed = alerts.length - blockedAttempts;

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
          {alerts.map((alert) => (
            <article key={alert.event_id} className="rounded-md border border-line bg-white p-4 shadow-soft">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="font-bold text-ink">{alert.title}</p>
                  <p className="mt-1 text-sm text-muted">{alert.body}</p>
                </div>
                <span className={alert.alert_type === "tamper_attempt" ? "rounded-full bg-amber-50 px-2.5 py-1 text-xs font-bold text-amber-800" : "rounded-full bg-red-50 px-2.5 py-1 text-xs font-bold text-danger"}>
                  {alert.alert_type === "tamper_attempt" ? "Bloqueado" : "Confirmado"}
                </span>
              </div>
              <div className="mt-3 grid gap-1 text-xs text-slate-500 sm:grid-cols-3">
                <span>{alert.community_name}</span>
                <span>{alert.device_name}</span>
                <span>{formatDate(alert.created_at)}</span>
              </div>
            </article>
          ))}
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
