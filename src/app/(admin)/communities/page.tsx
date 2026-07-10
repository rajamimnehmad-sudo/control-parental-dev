import Link from "next/link";
import { ArrowRight, Building2, CalendarClock, MonitorSmartphone, Plus, UsersRound } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { CreateCommunityForm } from "@/components/CreateCommunityForm";
import { EmptyState } from "@/components/EmptyState";
import { LicenseBadge } from "@/components/Badge";
import { listCommunities } from "@/lib/data";
import type { CommunitySummary } from "@/lib/types";
import { compactNumber, formatDate } from "@/lib/utils";

export default async function CommunitiesPage() {
  const communities = await listCommunities();
  const protectedUsers = communities.reduce((sum, community) => sum + Number(community.user_device_count), 0);
  const admins = communities.reduce((sum, community) => sum + Number(community.admin_count), 0);

  return (
    <main className="mx-auto grid max-w-4xl gap-5 px-4 py-5 lg:px-6">
      <section className="flex items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-ink">Comunidades</h1>
          <p className="mt-1 text-sm text-slate-500">Elegí una comunidad para administrar licencias, admins y usuarios.</p>
        </div>
        <div className="rounded-md border border-line bg-white px-3 py-2 text-right shadow-soft">
          <p className="text-xl font-bold text-ink">{compactNumber(communities.length)}</p>
          <p className="text-xs font-semibold text-slate-500">total</p>
        </div>
      </section>

      <section className="grid grid-cols-2 gap-3">
        <MiniStat label="Admins" value={admins} icon={UsersRound} />
        <MiniStat label="Usuarios" value={protectedUsers} icon={MonitorSmartphone} />
      </section>

      <details className="group rounded-md border border-line bg-white shadow-soft">
        <summary className="flex cursor-pointer list-none items-center justify-between gap-3 p-4">
          <div className="flex items-center gap-2">
            <Plus className="h-5 w-5 text-accent" />
            <h2 className="text-base font-semibold text-ink">Crear comunidad</h2>
          </div>
          <span className="text-sm font-semibold text-accent group-open:hidden">Abrir</span>
          <span className="hidden text-sm font-semibold text-accent group-open:inline">Cerrar</span>
        </summary>
        <div className="border-t border-line p-4">
          <CreateCommunityForm compact />
        </div>
      </details>

      <section className="grid gap-3">
        {communities.length === 0 ? (
          <EmptyState title="Sin comunidades" body="Crea la primera comunidad para emitir admins y licencias." />
        ) : (
          communities.map((community) => <CommunityCard key={community.community_id} community={community} />)
        )}
      </section>
    </main>
  );
}

function MiniStat({ label, value, icon: Icon }: { label: string; value: number; icon: LucideIcon }) {
  return (
    <div className="rounded-md border border-line bg-white p-3 shadow-soft">
      <div className="flex items-center justify-between gap-2">
        <p className="text-xs font-semibold text-slate-500">{label}</p>
        <Icon className="h-4 w-4 text-accent" />
      </div>
      <p className="mt-2 text-xl font-bold text-ink">{compactNumber(value)}</p>
    </div>
  );
}

function CommunityCard({ community }: { community: CommunitySummary }) {
  return (
    <Link className="block rounded-md border border-line bg-white p-4 shadow-soft transition hover:border-teal-200 hover:bg-teal-50/40" href={`/communities/${community.community_id}`}>
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <Building2 className="h-5 w-5 shrink-0 text-accent" />
            <h2 className="truncate text-lg font-semibold text-ink">{community.name}</h2>
          </div>
          <p className="mt-1 text-sm text-slate-500">{community.guide_label}</p>
        </div>
        <ArrowRight className="mt-1 h-5 w-5 shrink-0 text-slate-400" />
      </div>
      <div className="mt-4 flex flex-wrap items-center gap-2">
        <LicenseBadge status={community.license_status} />
        <span className="text-sm font-medium text-slate-600">{community.plan_name}</span>
      </div>
      <div className="mt-4 grid grid-cols-3 gap-2">
        <CardMetric label="Admins" value={`${compactNumber(community.admin_count)} / ${community.max_admins ?? "-"}`} />
        <CardMetric label="Usuarios" value={`${compactNumber(community.user_device_count)} / ${community.max_user_devices ?? "-"}`} />
        <CardMetric label="Vence" value={community.expires_at ? new Date(community.expires_at).toLocaleDateString("es-AR") : "Sin fecha"} />
      </div>
      <div className="mt-3 flex items-center gap-2 text-xs font-medium text-slate-500">
        <CalendarClock className="h-4 w-4" />
        Actualizada {formatDate(community.updated_at)}
      </div>
    </Link>
  );
}

function CardMetric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-md bg-slate-50 px-2 py-2">
      <p className="text-[11px] font-semibold uppercase text-slate-500">{label}</p>
      <p className="mt-1 text-sm font-bold text-ink">{value}</p>
    </div>
  );
}
