"use client";

import Link from "next/link";
import { ArrowRight, Search } from "lucide-react";
import { useMemo, useState } from "react";
import { LicenseBadge } from "@/components/Badge";
import type { CommunitySummary, LicenseStatus } from "@/lib/types";
import { formatDate, formatShortDate } from "@/lib/utils";

const filters: Array<{ value: "all" | LicenseStatus; label: string }> = [
  { value: "all", label: "Todas" },
  { value: "active", label: "Activas" },
  { value: "scheduled", label: "Programadas" },
  { value: "suspended", label: "Suspendidas" },
  { value: "expired", label: "Vencidas" },
];

export function CommunityDirectory({ communities }: { communities: CommunitySummary[] }) {
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<"all" | LicenseStatus>("all");
  const visible = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase("es");
    return communities.filter((community) => {
      const matchesFilter = filter === "all" || community.license_status === filter;
      const matchesQuery = !normalized || `${community.name} ${community.guide_label} ${community.plan_name}`.toLocaleLowerCase("es").includes(normalized);
      return matchesFilter && matchesQuery;
    });
  }, [communities, filter, query]);

  return (
    <>
      <div className="toolbar">
        <label className="search-field">
          <Search className="h-4 w-4 shrink-0" />
          <span className="sr-only">Buscar comunidades</span>
          <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Buscar por nombre, guía o plan…" />
        </label>
        <div className="flex gap-1 overflow-x-auto">
          {filters.map((item) => (
            <button key={item.value} type="button" onClick={() => setFilter(item.value)} className={`min-h-10 whitespace-nowrap rounded-lg px-3 text-xs font-semibold transition ${filter === item.value ? "bg-slate-900 text-white" : "text-slate-500 hover:bg-slate-100 hover:text-ink"}`}>
              {item.label}
            </button>
          ))}
        </div>
      </div>

      <div className="table-shell hidden md:block">
        <table className="data-table">
          <thead><tr><th className="table-cell">Comunidad</th><th className="table-cell">Estado</th><th className="table-cell">Usuarios</th><th className="table-cell">Administradores</th><th className="table-cell">Vencimiento</th><th className="table-cell"><span className="sr-only">Abrir</span></th></tr></thead>
          <tbody>
            {visible.map((community) => (
              <tr key={community.community_id}>
                <td className="table-cell"><Link href={`/communities/${community.community_id}`} className="block"><span className="block font-semibold text-ink">{community.name}</span><span className="mt-0.5 block text-xs text-slate-500">{community.guide_label} · {community.plan_name}</span></Link></td>
                <td className="table-cell"><LicenseBadge status={community.license_status} /></td>
                <td className="table-cell"><strong className="text-ink">{community.user_device_count}</strong><span className="text-slate-400"> / {community.max_user_devices ?? "Sin límite"}</span></td>
                <td className="table-cell"><strong className="text-ink">{community.admin_count}</strong><span className="text-slate-400"> / {community.max_admins ?? "Sin límite"}</span></td>
                <td className="table-cell text-slate-600">{formatShortDate(community.expires_at, "Sin vencimiento")}</td>
                <td className="table-cell text-right"><Link href={`/communities/${community.community_id}`} className="inline-flex h-9 w-9 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-100 hover:text-accent" aria-label={`Abrir ${community.name}`}><ArrowRight className="h-4 w-4" /></Link></td>
              </tr>
            ))}
          </tbody>
        </table>
        {!visible.length ? <p className="px-5 py-10 text-center text-sm text-slate-500">No hay comunidades que coincidan con la búsqueda.</p> : null}
      </div>

      <div className="grid gap-3 md:hidden">
        {visible.map((community) => (
          <Link className="subtle-card" href={`/communities/${community.community_id}`} key={community.community_id}>
            <div className="flex items-start justify-between gap-3"><div className="min-w-0"><p className="truncate font-bold text-ink">{community.name}</p><p className="mt-1 text-xs text-slate-500">{community.guide_label}</p></div><LicenseBadge status={community.license_status} /></div>
            <div className="mt-4 grid grid-cols-2 gap-2"><SmallMetric label="Usuarios" value={`${community.user_device_count} / ${community.max_user_devices ?? "Sin límite"}`} /><SmallMetric label="Admins" value={`${community.admin_count} / ${community.max_admins ?? "Sin límite"}`} /></div>
            <p className="mt-3 text-xs text-slate-500">Actualizada {formatDate(community.updated_at, "sin fecha informada")}</p>
          </Link>
        ))}
        {!visible.length ? <p className="rounded-2xl border border-dashed border-line bg-white px-4 py-10 text-center text-sm text-slate-500">No hay resultados.</p> : null}
      </div>
    </>
  );
}

function SmallMetric({ label, value }: { label: string; value: string }) {
  return <div className="rounded-xl bg-slate-50 px-3 py-2"><p className="text-[10px] font-bold uppercase tracking-wide text-slate-400">{label}</p><p className="mt-1 text-sm font-bold text-ink">{value}</p></div>;
}
