"use client";

import Link from "next/link";
import { ArrowRight, Search } from "lucide-react";
import { useMemo, useState } from "react";
import { LicenseBadge } from "@/components/Badge";
import type { CommunitySummary, LicenseStatus } from "@/lib/types";
import { capacitySnapshot, compactNumber, formatDate, formatShortDate } from "@/lib/utils";

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
      <div className="grid gap-3 border-y border-line py-3 sm:flex sm:items-center">
        <label className="search-field">
          <Search className="h-4 w-4 shrink-0" />
          <span className="sr-only">Buscar comunidades</span>
          <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Buscar por nombre, guía o plan…" />
        </label>
        <div className="-mx-1 flex gap-1 overflow-x-auto px-1">
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
                <td className="table-cell"><CapacityValue used={community.user_device_count} maximum={community.max_user_devices} /></td>
                <td className="table-cell"><CapacityValue used={community.admin_count} maximum={community.max_admins} /></td>
                <td className="table-cell text-slate-600">{formatShortDate(community.expires_at, "Sin vencimiento")}</td>
                <td className="table-cell text-right"><Link href={`/communities/${community.community_id}`} className="inline-flex h-9 w-9 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-100 hover:text-accent" aria-label={`Abrir ${community.name}`}><ArrowRight className="h-4 w-4" /></Link></td>
              </tr>
            ))}
          </tbody>
        </table>
        {!visible.length ? <p className="px-5 py-10 text-center text-sm text-slate-500">No hay comunidades que coincidan con la búsqueda.</p> : null}
      </div>

      <div className="divide-y divide-line border-y border-line md:hidden">
        {visible.map((community) => (
          <Link className="group block py-4" href={`/communities/${community.community_id}`} key={community.community_id}>
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="truncate font-bold text-ink">{community.name}</p>
                <p className="mt-1 truncate text-xs text-slate-500">{community.guide_label} · {community.plan_name}</p>
              </div>
              <LicenseBadge status={community.license_status} />
            </div>
            <div className="mt-4 grid grid-cols-2 divide-x divide-line">
              <CapacityLine label="Usuarios" used={community.user_device_count} maximum={community.max_user_devices} />
              <CapacityLine label="Admins" used={community.admin_count} maximum={community.max_admins} indented />
            </div>
            <div className="mt-4 flex items-center justify-between gap-3 text-xs text-slate-500">
              <span>Actualizada {formatDate(community.updated_at, "sin fecha informada")}</span>
              <ArrowRight className="h-4 w-4 shrink-0 transition group-hover:translate-x-0.5 group-hover:text-accent" />
            </div>
          </Link>
        ))}
        {!visible.length ? <p className="px-4 py-10 text-center text-sm text-slate-500">No hay resultados.</p> : null}
      </div>
    </>
  );
}

function CapacityValue({ used, maximum }: { used: number; maximum: number | null }) {
  const capacity = capacitySnapshot(used, maximum);
  return (
    <div>
      <p><strong className="text-ink">{compactNumber(capacity.used)}</strong><span className="text-slate-400"> usados de {capacity.maximum === null ? "un límite sin definir" : compactNumber(capacity.maximum)}</span></p>
      <p className={`mt-0.5 text-xs ${capacity.exceeded ? "font-semibold text-danger" : "text-slate-500"}`}>
        {capacity.available === null ? "Disponibilidad sin definir" : `${compactNumber(capacity.available)} disponibles`}
      </p>
    </div>
  );
}

function CapacityLine({ label, used, maximum, indented = false }: { label: string; used: number; maximum: number | null; indented?: boolean }) {
  const capacity = capacitySnapshot(used, maximum);
  return (
    <div className={indented ? "min-w-0 pl-4" : "min-w-0 pr-4"}>
      <p className="text-[10px] font-bold uppercase tracking-wide text-slate-400">{label}</p>
      <p className="mt-1 truncate text-sm font-bold text-ink">{compactNumber(capacity.used)} / {capacity.maximum === null ? "Sin límite" : compactNumber(capacity.maximum)}</p>
      <p className={`mt-0.5 text-[11px] ${capacity.exceeded ? "font-semibold text-danger" : "text-slate-500"}`}>{capacity.available === null ? "Disponibilidad sin definir" : `${compactNumber(capacity.available)} disponibles`}</p>
    </div>
  );
}
