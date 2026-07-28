import Link from "next/link";
import {
  ArrowRight,
  BellRing,
  Building2,
  CheckCircle2,
  Database,
  MonitorSmartphone,
  ShieldCheck,
  Star,
  UsersRound,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { LicenseBadge } from "@/components/Badge";
import { listAppRatings, listCommunities, listProtectionAlerts } from "@/lib/data";
import { getDomainListStatus, protectionState } from "@/lib/domain-list";
import { compactNumber, formatDate } from "@/lib/utils";

export default async function DashboardPage() {
  const [communities, alerts, ratings, domains] = await Promise.all([
    listCommunities(),
    listProtectionAlerts(),
    listAppRatings(),
    getDomainListStatus(),
  ]);
  const activeCommunities = communities.filter((item) => item.license_status === "active").length;
  const users = communities.reduce((sum, item) => sum + Number(item.user_device_count), 0);
  const admins = communities.reduce((sum, item) => sum + Number(item.admin_count), 0);
  const attentionCommunities = communities.filter((item) => item.license_status === "expired" || item.license_status === "suspended");
  const domainState = protectionState(domains);
  const ratingAverage = ratings.length ? ratings.reduce((sum, item) => sum + item.stars, 0) / ratings.length : 0;
  const recentCommunities = [...communities].sort((a, b) => new Date(b.updated_at).getTime() - new Date(a.updated_at).getTime()).slice(0, 4);

  return (
    <main className="page-shell">
      <section className="page-heading">
        <div>
          <p className="eyebrow">Vista general</p>
          <h1>{greeting()}, Yejiel</h1>
          <p>Estado de la operación, protección y experiencia de todas las comunidades.</p>
        </div>
        <p className="rounded-xl border border-line bg-white px-3 py-2 text-xs font-semibold text-slate-500 shadow-panel">
          {argentinaDate()}
        </p>
      </section>

      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <DashboardMetric label="Comunidades activas" value={`${activeCommunities}`} detail={`${communities.length} totales`} icon={Building2} tone="teal" />
        <DashboardMetric label="Usuarios protegidos" value={compactNumber(users)} detail={`${compactNumber(admins)} administradores`} icon={MonitorSmartphone} tone="blue" />
        <DashboardMetric label="Alertas abiertas" value={compactNumber(alerts.length)} detail={alerts.length ? "Requieren revisión" : "Todo en orden"} icon={BellRing} tone={alerts.length ? "amber" : "green"} />
        <DashboardMetric label="Valoración general" value={ratingAverage ? ratingAverage.toFixed(1) : "—"} detail={`${ratings.length} respuestas`} icon={Star} tone="violet" />
      </section>

      <section className="grid gap-6 xl:grid-cols-[minmax(0,1.45fr)_minmax(320px,.75fr)]">
        <div className="panel">
          <div className="panel-header">
            <div>
              <h2 className="section-title">Operación por comunidad</h2>
              <p className="muted mt-1">Licencias, capacidad y actividad reciente.</p>
            </div>
            <Link className="button button-secondary" href="/communities">Ver todas <ArrowRight className="h-4 w-4" /></Link>
          </div>
          <div className="mt-2 divide-y divide-line">
            {recentCommunities.map((community) => (
              <Link key={community.community_id} href={`/communities/${community.community_id}`} className="grid gap-3 py-4 transition hover:bg-slate-50/70 sm:grid-cols-[minmax(0,1fr)_auto_auto] sm:items-center sm:px-2">
                <div className="min-w-0">
                  <p className="truncate font-semibold text-ink">{community.name}</p>
                  <p className="mt-1 text-xs text-slate-500">Actualizada {formatDate(community.updated_at)}</p>
                </div>
                <div className="flex gap-5 text-sm text-slate-600">
                  <span><strong className="text-ink">{community.user_device_count}</strong> usuarios</span>
                  <span><strong className="text-ink">{community.admin_count}</strong> admins</span>
                </div>
                <LicenseBadge status={community.license_status} />
              </Link>
            ))}
            {recentCommunities.length === 0 ? <p className="py-8 text-center text-sm text-slate-500">Todavía no hay comunidades.</p> : null}
          </div>
        </div>

        <div className="grid content-start gap-4">
          <article className="accent-card">
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.16em] text-teal-200">Protección web</p>
                <p className="mt-3 text-3xl font-bold tracking-tight">{domains.payload ? compactNumber(domains.payload.totalCount) : "—"}</p>
                <p className="mt-1 text-sm text-slate-300">dominios protegidos</p>
              </div>
              <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-white/10 text-teal-200"><Database className="h-5 w-5" /></span>
            </div>
            <div className="mt-5 flex items-center justify-between border-t border-white/10 pt-4">
              <span className="inline-flex items-center gap-2 text-sm font-semibold text-emerald-300">
                <CheckCircle2 className="h-4 w-4" />
                {domainState === "active" ? "Base activa" : "Revisar estado"}
              </span>
              <Link className="text-sm font-semibold text-white hover:text-teal-200" href="/web-protection/domain-list">Administrar →</Link>
            </div>
          </article>

          <article className="panel">
            <div className="flex items-center gap-3">
              <span className={`flex h-10 w-10 items-center justify-center rounded-xl ${attentionCommunities.length ? "bg-amber-50 text-amber-700" : "bg-emerald-50 text-emerald-700"}`}>
                <ShieldCheck className="h-5 w-5" />
              </span>
              <div>
                <h2 className="font-bold text-ink">Estado comercial</h2>
                <p className="text-sm text-slate-500">{attentionCommunities.length ? `${attentionCommunities.length} licencias para revisar` : "Todas las licencias están al día"}</p>
              </div>
            </div>
            {attentionCommunities.length ? (
              <div className="mt-4 grid gap-2">
                {attentionCommunities.slice(0, 3).map((community) => (
                  <Link className="flex items-center justify-between rounded-xl bg-slate-50 px-3 py-2.5 text-sm font-semibold text-ink" href={`/communities/${community.community_id}`} key={community.community_id}>
                    <span className="truncate">{community.name}</span><ArrowRight className="h-4 w-4 text-slate-400" />
                  </Link>
                ))}
              </div>
            ) : null}
          </article>
        </div>
      </section>

      <section className="grid gap-4 md:grid-cols-3">
        <QuickLink href="/communities" title="Gestionar comunidades" detail="Usuarios, administradores y licencias" icon={UsersRound} />
        <QuickLink href="/alerts" title="Revisar seguridad" detail="Intentos bloqueados y protección" icon={ShieldCheck} />
        <QuickLink href="/ratings" title="Escuchar usuarios" detail="Calificaciones y comentarios" icon={Star} />
      </section>
    </main>
  );
}

function DashboardMetric({ label, value, detail, icon: Icon, tone }: { label: string; value: string; detail: string; icon: LucideIcon; tone: "teal" | "blue" | "amber" | "green" | "violet" }) {
  const tones = {
    teal: "bg-teal-50 text-teal-700",
    blue: "bg-sky-50 text-sky-700",
    amber: "bg-amber-50 text-amber-700",
    green: "bg-emerald-50 text-emerald-700",
    violet: "bg-violet-50 text-violet-700",
  };
  return (
    <article className="metric-card">
      <div className="flex items-start justify-between gap-3">
        <div><p className="metric-label">{label}</p><p className="metric-value">{value}</p><p className="mt-1 text-xs font-medium text-slate-500">{detail}</p></div>
        <span className={`flex h-10 w-10 items-center justify-center rounded-xl ${tones[tone]}`}><Icon className="h-5 w-5" /></span>
      </div>
    </article>
  );
}

function QuickLink({ href, title, detail, icon: Icon }: { href: string; title: string; detail: string; icon: LucideIcon }) {
  return (
    <Link className="group flex items-center gap-4 rounded-2xl border border-line bg-white p-4 shadow-panel transition hover:-translate-y-0.5 hover:border-teal-200 hover:shadow-soft" href={href}>
      <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-slate-100 text-slate-700 transition group-hover:bg-teal-50 group-hover:text-accent"><Icon className="h-5 w-5" /></span>
      <span className="min-w-0 flex-1"><span className="block font-semibold text-ink">{title}</span><span className="mt-0.5 block truncate text-xs text-slate-500">{detail}</span></span>
      <ArrowRight className="h-4 w-4 text-slate-400 transition group-hover:translate-x-0.5 group-hover:text-accent" />
    </Link>
  );
}

function greeting() {
  const hour = Number(new Intl.DateTimeFormat("es-AR", { timeZone: "America/Argentina/Buenos_Aires", hour: "numeric", hour12: false }).format(new Date()));
  return hour < 12 ? "Buen día" : hour < 19 ? "Buenas tardes" : "Buenas noches";
}

function argentinaDate() {
  return new Intl.DateTimeFormat("es-AR", { timeZone: "America/Argentina/Buenos_Aires", weekday: "long", day: "numeric", month: "long" }).format(new Date());
}
