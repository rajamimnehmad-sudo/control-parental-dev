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
import { EmptyState } from "@/components/EmptyState";
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
          <p>Lo importante de la operación en una sola vista.</p>
        </div>
        <p className="text-xs font-semibold capitalize text-slate-500">
          {argentinaDate()}
        </p>
      </section>

      <section aria-label="Resumen operativo" className="grid grid-cols-2 gap-px overflow-hidden border-y border-line bg-line sm:grid-cols-4 sm:rounded-2xl sm:border">
        <DashboardMetric label="Comunidades activas" value={`${activeCommunities}`} detail={`${communities.length} totales`} icon={Building2} />
        <DashboardMetric label="Usuarios protegidos" value={compactNumber(users)} detail={`${compactNumber(admins)} administradores`} icon={MonitorSmartphone} />
        <DashboardMetric label="Alertas abiertas" value={compactNumber(alerts.length)} detail={alerts.length ? "Requieren revisión" : "Todo en orden"} icon={BellRing} attention={alerts.length > 0} />
        <DashboardMetric label="Valoración general" value={ratingAverage ? ratingAverage.toFixed(1) : "Sin datos"} detail={ratings.length ? `${ratings.length} respuestas` : "Todavía sin respuestas"} icon={Star} />
      </section>

      <section className="grid gap-8 border-t border-line pt-6 xl:grid-cols-[minmax(0,1.45fr)_minmax(320px,.75fr)]">
        <div className="min-w-0">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <h2 className="section-title">Operación por comunidad</h2>
              <p className="muted mt-1">Licencias, capacidad y actividad reciente, sin ruido.</p>
            </div>
            <Link className="inline-flex min-h-11 items-center gap-2 text-sm font-semibold text-accent" href="/communities">Ver todas <ArrowRight className="h-4 w-4" /></Link>
          </div>
          {recentCommunities.length === 0 ? (
            <div className="mt-5">
              <EmptyState
                title="Empezá con la primera comunidad"
                body="Creá su licencia inicial y después sumá administradores, usuarios y dispositivos desde una operación ordenada."
                action={<Link className="button button-primary" href="/communities">Crear comunidad <ArrowRight className="h-4 w-4" /></Link>}
              />
            </div>
          ) : (
            <div className="mt-3 divide-y divide-line border-y border-line">
            {recentCommunities.map((community) => (
              <Link key={community.community_id} href={`/communities/${community.community_id}`} className="grid gap-3 py-4 transition hover:bg-white/70 sm:grid-cols-[minmax(0,1fr)_auto_auto] sm:items-center sm:px-2">
                <div className="min-w-0">
                  <p className="truncate font-semibold text-ink">{community.name}</p>
                  <p className="mt-1 text-xs text-slate-500">Actualizada {formatDate(community.updated_at, "sin fecha informada")}</p>
                </div>
                <div className="flex gap-5 text-sm text-slate-600">
                  <span><strong className="text-ink">{community.user_device_count}</strong> usuarios</span>
                  <span><strong className="text-ink">{community.admin_count}</strong> admins</span>
                </div>
                <LicenseBadge status={community.license_status} />
              </Link>
            ))}
            </div>
          )}
        </div>

        <div className="grid content-start gap-6">
          <article className="accent-card">
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.16em] text-teal-200">Protección web</p>
                <p className="mt-3 text-3xl font-bold tracking-tight">{domains.payload ? compactNumber(domains.payload.totalCount) : "No disponible"}</p>
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

          <article className="border-t border-line pt-5">
            <div className="flex items-center gap-3">
              <span className={`flex h-10 w-10 items-center justify-center rounded-full ${attentionCommunities.length ? "bg-amber-50 text-amber-700" : "bg-emerald-50 text-emerald-700"}`}>
                <ShieldCheck className="h-5 w-5" />
              </span>
              <div>
                <h2 className="font-bold text-ink">Estado comercial</h2>
                <p className="text-sm text-slate-500">{attentionCommunities.length ? `${attentionCommunities.length} licencias para revisar` : "Todas las licencias están al día"}</p>
              </div>
            </div>
            {attentionCommunities.length ? (
              <div className="mt-4 divide-y divide-line border-y border-line">
                {attentionCommunities.slice(0, 3).map((community) => (
                  <Link className="flex min-h-11 items-center justify-between gap-3 py-2.5 text-sm font-semibold text-ink" href={`/communities/${community.community_id}`} key={community.community_id}>
                    <span className="truncate">{community.name}</span><ArrowRight className="h-4 w-4 text-slate-400" />
                  </Link>
                ))}
              </div>
            ) : null}
          </article>
        </div>
      </section>

      <section className="divide-y divide-line border-y border-line md:grid md:grid-cols-3 md:divide-x md:divide-y-0">
        <QuickLink href="/communities" title="Gestionar comunidades" detail="Usuarios, administradores y licencias" icon={UsersRound} />
        <QuickLink href="/alerts" title="Revisar seguridad" detail="Intentos bloqueados y protección" icon={ShieldCheck} />
        <QuickLink href="/ratings" title="Escuchar usuarios" detail="Calificaciones y comentarios" icon={Star} />
      </section>
    </main>
  );
}

function DashboardMetric({ label, value, detail, icon: Icon, attention = false }: { label: string; value: string; detail: string; icon: LucideIcon; attention?: boolean }) {
  return (
    <article className="min-w-0 bg-canvas p-4 sm:bg-white sm:p-5">
      <div className="flex items-center gap-2 text-slate-500">
        <Icon className={`h-4 w-4 ${attention ? "text-amber-600" : "text-accent"}`} />
        <p className="truncate text-xs font-semibold">{label}</p>
      </div>
      <p className="mt-3 truncate text-2xl font-bold tracking-tight text-ink sm:text-3xl">{value}</p>
      <p className={`mt-1 truncate text-xs font-medium ${attention ? "text-amber-700" : "text-slate-500"}`}>{detail}</p>
    </article>
  );
}

function QuickLink({ href, title, detail, icon: Icon }: { href: string; title: string; detail: string; icon: LucideIcon }) {
  return (
    <Link className="group flex min-h-20 items-center gap-4 py-4 transition hover:bg-white/60 md:px-4" href={href}>
      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-white text-slate-700 shadow-sm ring-1 ring-line transition group-hover:text-accent"><Icon className="h-5 w-5" /></span>
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
