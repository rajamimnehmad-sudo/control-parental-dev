import { Building2, MonitorSmartphone, Plus, UsersRound } from "lucide-react";
import { CommunityDirectory } from "@/components/CommunityDirectory";
import { CreateCommunityForm } from "@/components/CreateCommunityForm";
import { EmptyState } from "@/components/EmptyState";
import { listCommunities } from "@/lib/data";
import { compactNumber } from "@/lib/utils";

export default async function CommunitiesPage() {
  const communities = (await listCommunities()).sort((left, right) => priority(left.license_status) - priority(right.license_status) || left.name.localeCompare(right.name, "es"));
  const users = communities.reduce((sum, item) => sum + Number(item.user_device_count), 0);
  const admins = communities.reduce((sum, item) => sum + Number(item.admin_count), 0);
  const active = communities.filter((item) => item.license_status === "active").length;

  return (
    <main className="page-shell">
      <section className="page-heading">
        <div><p className="eyebrow">Organizaciones</p><h1>Comunidades</h1><p>Administrá licencias, responsables y dispositivos desde un único lugar.</p></div>
        <details className="group relative">
          <summary className="button button-primary cursor-pointer list-none"><Plus className="h-4 w-4" />Nueva comunidad</summary>
          <div className="absolute right-0 z-20 mt-2 w-[min(92vw,480px)] rounded-2xl border border-line bg-white p-5 shadow-2xl"><CreateCommunityForm compact /></div>
        </details>
      </section>

      <section className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Summary label="Total" value={compactNumber(communities.length)} icon={Building2} />
        <Summary label="Activas" value={compactNumber(active)} icon={Building2} />
        <Summary label="Usuarios" value={compactNumber(users)} icon={MonitorSmartphone} />
        <Summary label="Administradores" value={compactNumber(admins)} icon={UsersRound} />
      </section>

      {communities.length === 0 ? (
        <EmptyState title="Todavía no hay comunidades" body="Usá “Nueva comunidad” para crear la primera organización junto con su licencia y cupos iniciales." />
      ) : <CommunityDirectory communities={communities} />}
    </main>
  );
}

function priority(status: string) {
  return status === "expired" || status === "suspended" ? 0 : status === "scheduled" ? 1 : 2;
}

function Summary({ label, value, icon: Icon }: { label: string; value: string; icon: typeof Building2 }) {
  return <article className="metric-card p-4"><div className="flex items-center justify-between gap-2"><p className="metric-label">{label}</p><Icon className="h-4 w-4 text-slate-400" /></div><p className="mt-2 text-2xl font-bold tracking-tight text-ink">{value}</p></article>;
}
