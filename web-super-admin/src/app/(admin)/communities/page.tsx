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
        <div><p className="eyebrow">Organizaciones</p><h1>Comunidades</h1><p>Licencias, responsables y capacidad con una lectura rápida.</p></div>
        <details className="group relative w-full sm:w-auto">
          <summary className="button button-primary w-full cursor-pointer list-none sm:w-auto">
            <Plus className="h-4 w-4 transition group-open:rotate-45" />
            <span className="group-open:hidden">Nueva comunidad</span>
            <span className="hidden group-open:inline">Cerrar formulario</span>
          </summary>
          <div className="mt-4 border-y border-line bg-white py-5 sm:absolute sm:right-0 sm:z-20 sm:mt-2 sm:w-[min(88vw,560px)] sm:rounded-2xl sm:border sm:p-5 sm:shadow-2xl"><CreateCommunityForm compact /></div>
        </details>
      </section>

      <section aria-label="Resumen de comunidades" className="grid grid-cols-2 gap-px overflow-hidden border-y border-line bg-line sm:rounded-2xl sm:border lg:grid-cols-4">
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
  return <article className="bg-canvas p-4 sm:bg-white"><div className="flex items-center justify-between gap-2"><p className="text-xs font-semibold text-slate-500">{label}</p><Icon className="h-4 w-4 text-accent" /></div><p className="mt-2 text-2xl font-bold tracking-tight text-ink">{value}</p></article>;
}
