import {
  Activity,
  AlertTriangle,
  BarChart3,
  Calculator,
  CheckCircle2,
  MonitorSmartphone,
  Search,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { DagLimitForm } from "@/components/DagLimitForm";
import { DagUsageAutoRefresh } from "@/components/DagUsageAutoRefresh";
import { getDagUsageBundle } from "@/lib/data";
import type { DagUsageDevice, DagUsageSummary } from "@/lib/types";
import { compactNumber, formatDate } from "@/lib/utils";

export const dynamic = "force-dynamic";

const BraveRequestCostUsd = 0.005;
const BraveMonthlyCreditUsd = 5;

export default async function DagUsagePage() {
  const { summaries, devices } = await getDagUsageBundle();
  const totalRequests = summaries.reduce((sum, item) => sum + item.request_count, 0);
  const activeDevices = summaries.reduce((sum, item) => sum + item.active_dag_devices, 0);
  const totalCapacity = summaries.reduce((sum, item) => sum + item.total_capacity, 0);
  const totalRemaining = summaries.reduce((sum, item) => sum + item.remaining_count, 0);
  const projectedRequests = projectMonthEnd(totalRequests);
  const billedCost = estimatedBilledCost(totalRequests);
  const projectedCost = estimatedBilledCost(projectedRequests);
  const usagePercent = percent(totalRequests, totalCapacity);

  return (
    <main className="mx-auto grid max-w-7xl gap-5 px-4 py-5 lg:px-6">
      <section className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <BarChart3 className="h-6 w-6 text-accent" />
            <h1 className="text-2xl font-semibold text-ink">Consumo mensual DAG</h1>
          </div>
          <p className="mt-1 text-sm text-slate-500">
            {monthLabel()} · conteo sin consultas, URLs, resultados ni historial.
          </p>
        </div>
        <DagUsageAutoRefresh />
      </section>

      <UsageAlert usagePercent={usagePercent} totalCapacity={totalCapacity} />

      <section className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <MetricCard label="Consultas usadas" value={compactNumber(totalRequests)} detail={`${compactNumber(totalRemaining)} restantes`} icon={Search} />
        <MetricCard label="Dispositivos DAG" value={compactNumber(activeDevices)} detail={`${compactNumber(totalCapacity)} consultas de capacidad`} icon={MonitorSmartphone} />
        <MetricCard label="Costo Brave estimado" value={formatUsd(billedCost)} detail={`${formatUsd(totalRequests * BraveRequestCostUsd)} antes del crédito`} icon={Calculator} />
        <MetricCard label="Proyección al cierre" value={compactNumber(projectedRequests)} detail={`${formatUsd(projectedCost)} estimados`} icon={Activity} />
      </section>

      <section className="rounded-md border border-line bg-white p-4 text-sm text-slate-600 shadow-soft">
        <p className="font-semibold text-ink">Cómo se calcula</p>
        <p className="mt-1">
          USD 0,005 por consulta menos el crédito global mensual de USD 5. La estimación contempla solo DAG; si la misma cuenta de Brave se usa en otro producto, el cobro real puede variar.
        </p>
      </section>

      <section className="grid gap-4">
        {summaries.map((summary) => (
          <CommunityUsageCard
            key={summary.community_id}
            summary={summary}
            devices={devices.filter((device) => device.community_id === summary.community_id)}
          />
        ))}
      </section>
    </main>
  );
}

function MetricCard({ label, value, detail, icon: Icon }: { label: string; value: string; detail: string; icon: LucideIcon }) {
  return (
    <div className="rounded-md border border-line bg-white p-4 shadow-soft">
      <div className="flex items-center justify-between gap-2">
        <p className="text-xs font-semibold text-slate-500">{label}</p>
        <Icon className="h-4 w-4 text-accent" />
      </div>
      <p className="mt-2 text-xl font-bold text-ink">{value}</p>
      <p className="mt-1 text-xs font-medium text-slate-500">{detail}</p>
    </div>
  );
}

function UsageAlert({ usagePercent, totalCapacity }: { usagePercent: number; totalCapacity: number }) {
  if (totalCapacity === 0) {
    return (
      <div className="flex items-start gap-3 rounded-md border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">
        <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0 text-slate-400" />
        <p>No hay dispositivos con DAG abierto. El panel seguirá mostrando cualquier consumo ya realizado durante el mes.</p>
      </div>
    );
  }

  if (usagePercent < 80) return null;
  const exhausted = usagePercent >= 100;
  return (
    <div className={`flex items-start gap-3 rounded-md border p-4 text-sm ${exhausted ? "border-red-200 bg-red-50 text-red-800" : "border-amber-200 bg-amber-50 text-amber-800"}`}>
      <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0" />
      <p className="font-semibold">
        {exhausted ? "El cupo mensual está agotado en al menos el total disponible." : `El consumo alcanzó ${Math.round(usagePercent)}% del cupo mensual.`}
      </p>
    </div>
  );
}

function CommunityUsageCard({ summary, devices }: { summary: DagUsageSummary; devices: DagUsageDevice[] }) {
  const usagePercent = percent(summary.request_count, summary.total_capacity);
  const tone = usagePercent >= 100 ? "bg-red-500" : usagePercent >= 80 ? "bg-amber-500" : "bg-accent";

  return (
    <article className="overflow-hidden rounded-md border border-line bg-white shadow-soft">
      <div className="flex flex-col gap-4 border-b border-line p-4 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <h2 className="text-lg font-semibold text-ink">{summary.community_name}</h2>
          <p className="mt-1 text-sm text-slate-500">
            {compactNumber(summary.request_count)} usadas · {compactNumber(summary.remaining_count)} restantes · {compactNumber(summary.active_dag_devices)} dispositivos activos
          </p>
        </div>
        <DagLimitForm communityId={summary.community_id} monthlyLimit={summary.monthly_limit} />
      </div>

      <div className="p-4">
        <div className="flex items-center justify-between gap-3 text-xs font-semibold text-slate-500">
          <span>{Math.round(usagePercent)}% utilizado</span>
          <span>{compactNumber(summary.request_count)} / {compactNumber(summary.total_capacity)}</span>
        </div>
        <div className="mt-2 h-2 overflow-hidden rounded-full bg-slate-100" aria-label={`${Math.round(usagePercent)} por ciento utilizado`}>
          <div className={`h-full rounded-full ${tone}`} style={{ width: `${Math.min(100, usagePercent)}%` }} />
        </div>
      </div>

      {devices.length === 0 ? (
        <p className="border-t border-line px-4 py-5 text-sm text-slate-500">No hay dispositivos DAG activos ni consumo registrado este mes.</p>
      ) : (
        <div className="grid gap-3 border-t border-line p-4 md:hidden">
          {devices.map((device) => <DeviceUsageCard key={device.device_id} device={device} />)}
        </div>
      )}
      {devices.length > 0 ? (
        <div className="hidden overflow-x-auto border-t border-line md:block">
          <table className="min-w-full">
            <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="table-cell">Dispositivo</th>
                <th className="table-cell">Estado</th>
                <th className="table-cell">Usadas</th>
                <th className="table-cell">Restantes</th>
                <th className="table-cell">Última consulta</th>
                <th className="table-cell">Última conexión</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {devices.map((device) => <DeviceUsageRow key={device.device_id} device={device} />)}
            </tbody>
          </table>
        </div>
      ) : null}
    </article>
  );
}

function DeviceUsageCard({ device }: { device: DagUsageDevice }) {
  const usagePercent = percent(device.request_count, device.monthly_limit);
  const warningClass = usagePercent >= 100 ? "text-red-700" : usagePercent >= 80 ? "text-amber-700" : "text-ink";

  return (
    <div className="rounded-2xl bg-slate-50 p-4 ring-1 ring-slate-100">
      <div className="flex items-start justify-between gap-3">
        <p className="font-bold text-ink">{device.display_name}</p>
        <span className={`shrink-0 rounded-full px-2.5 py-1 text-xs font-bold ${device.dag_enabled ? "bg-teal-100 text-teal-800" : "bg-slate-200 text-slate-600"}`}>
          {device.dag_enabled ? "DAG abierto" : "DAG cerrado"}
        </span>
      </div>
      <div className="mt-4 grid grid-cols-2 gap-3 text-sm">
        <div><p className="text-xs text-slate-500">Usadas</p><p className={`mt-1 font-bold ${warningClass}`}>{compactNumber(device.request_count)} / {compactNumber(device.monthly_limit)}</p></div>
        <div><p className="text-xs text-slate-500">Restantes</p><p className="mt-1 font-bold text-ink">{compactNumber(device.remaining_count)}</p></div>
        <div><p className="text-xs text-slate-500">Última consulta</p><p className="mt-1 text-slate-700">{formatDate(device.last_usage_at)}</p></div>
        <div><p className="text-xs text-slate-500">Última conexión</p><p className="mt-1 text-slate-700">{formatDate(device.last_seen_at)}</p></div>
      </div>
    </div>
  );
}

function DeviceUsageRow({ device }: { device: DagUsageDevice }) {
  const usagePercent = percent(device.request_count, device.monthly_limit);
  const warningClass = usagePercent >= 100 ? "text-red-700" : usagePercent >= 80 ? "text-amber-700" : "text-ink";

  return (
    <tr>
      <td className="table-cell font-semibold text-ink">{device.display_name}</td>
      <td className="table-cell">
        <span className={`rounded-full px-2.5 py-1 text-xs font-bold ${device.dag_enabled ? "bg-teal-50 text-teal-700" : "bg-slate-100 text-slate-600"}`}>
          {device.dag_enabled ? "DAG abierto" : "DAG cerrado"}
        </span>
      </td>
      <td className={`table-cell font-bold ${warningClass}`}>{compactNumber(device.request_count)} / {compactNumber(device.monthly_limit)}</td>
      <td className="table-cell text-slate-600">{compactNumber(device.remaining_count)}</td>
      <td className="table-cell text-slate-600">{formatDate(device.last_usage_at)}</td>
      <td className="table-cell text-slate-600">{formatDate(device.last_seen_at)}</td>
    </tr>
  );
}

function percent(value: number, total: number) {
  if (total <= 0) return 0;
  return Math.max(0, (value / total) * 100);
}

function estimatedBilledCost(requests: number) {
  return Math.max(0, requests * BraveRequestCostUsd - BraveMonthlyCreditUsd);
}

function projectMonthEnd(requests: number) {
  const now = new Date();
  const elapsedDays = Math.max(1, now.getUTCDate());
  const daysInMonth = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + 1, 0)).getUTCDate();
  return Math.ceil((requests / elapsedDays) * daysInMonth);
}

function monthLabel() {
  return new Intl.DateTimeFormat("es-AR", { month: "long", year: "numeric", timeZone: "UTC" }).format(new Date());
}

function formatUsd(value: number) {
  return new Intl.NumberFormat("es-AR", { style: "currency", currency: "USD", minimumFractionDigits: 2 }).format(value);
}
