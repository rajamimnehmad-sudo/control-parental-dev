"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  BarChart3,
  BellRing,
  BrainCircuit,
  Building2,
  Database,
  LayoutDashboard,
  Menu,
  Megaphone,
  ShieldCheck,
  Sparkles,
  Star,
  X,
} from "lucide-react";
import { useEffect, useState } from "react";

const mobileItems = [
  { href: "/dashboard", label: "Resumen", icon: LayoutDashboard },
  { href: "/communities", label: "Comunidades", icon: Building2 },
  { href: "/alerts", label: "Alertas", icon: BellRing },
] as const;

const groups = [
  {
    label: "Operación",
    items: [
      { href: "/dashboard", label: "Resumen", icon: LayoutDashboard },
      { href: "/communities", label: "Comunidades", icon: Building2 },
      { href: "/alerts", label: "Alertas", icon: BellRing },
      { href: "/announcements", label: "Comunicaciones", icon: Megaphone },
    ],
  },
  {
    label: "Protección",
    items: [
      { href: "/web-protection/domain-list", label: "Protección web", icon: Database },
      { href: "/dag-usage", label: "Consumo DAG", icon: BarChart3 },
      { href: "/dag-calibration", label: "Calibración DAG", icon: BrainCircuit },
    ],
  },
  {
    label: "Experiencia",
    items: [{ href: "/ratings", label: "Valoraciones", icon: Star }],
  },
] as const;

export function AdminNavigation() {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  const moreActive = !mobileItems.some(({ href }) => pathname === href || pathname.startsWith(`${href}/`));

  useEffect(() => {
    if (!open) return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    window.addEventListener("keydown", onKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", onKeyDown);
    };
  }, [open]);

  return (
    <>
      <button className="mobile-menu-button lg:hidden" type="button" aria-label="Abrir menú" aria-expanded={open} onClick={() => setOpen(true)}>
        <Menu className="h-5 w-5" />
      </button>
      {open ? (
        <button
          className="fixed inset-0 z-40 bg-slate-950/35 backdrop-blur-[2px] lg:hidden"
          type="button"
          aria-label="Cerrar menú"
          onClick={() => setOpen(false)}
        />
      ) : null}
      <aside aria-label="Menú principal" className={`admin-sidebar ${open ? "admin-sidebar-open" : ""}`}>
        <div className="flex h-full flex-col">
          <div className="flex items-center justify-between border-b border-white/10 px-1 pb-5 lg:border-0 lg:pb-8">
            <Link href="/dashboard" className="flex items-center gap-3" onClick={() => setOpen(false)}>
              <span className="brand-mark"><ShieldCheck className="h-5 w-5" /></span>
              <span>
                <span className="block text-lg font-bold tracking-tight text-white">Glosh</span>
                <span className="block text-[11px] font-semibold uppercase tracking-[0.18em] text-slate-400">Control Center</span>
              </span>
            </Link>
            <button className="icon-button border border-white/10 bg-white/[0.06] text-slate-200 hover:bg-white/10 hover:text-white lg:hidden" onClick={() => setOpen(false)} aria-label="Cerrar menú">
              <X className="h-5 w-5" />
            </button>
          </div>

          <nav aria-label="Navegación principal" className="grid gap-7 overflow-y-auto py-6 lg:py-0">
            {groups.map((group) => (
              <div key={group.label}>
                <p className="nav-group-label">{group.label}</p>
                <div className="mt-2 grid gap-1">
                  {group.items.map(({ href, label, icon: Icon }) => {
                    const active = pathname === href || pathname.startsWith(`${href}/`);
                    return (
                      <Link
                        key={href}
                        href={href}
                        aria-current={active ? "page" : undefined}
                        className={active ? "admin-nav-item admin-nav-item-active" : "admin-nav-item"}
                        onClick={() => setOpen(false)}
                      >
                        <Icon className="h-[18px] w-[18px]" />
                        <span>{label}</span>
                      </Link>
                    );
                  })}
                </div>
              </div>
            ))}
          </nav>

          <div className="mt-auto border-t border-white/10 px-3 pt-4">
            <div className="flex items-center gap-2 text-xs font-semibold text-emerald-300">
              <Sparkles className="h-4 w-4" />
              Entorno DEV
            </div>
            <p className="mt-1.5 text-xs leading-5 text-slate-400">Administración central de protección y dispositivos.</p>
          </div>
        </div>
      </aside>

      <nav
        aria-label="Navegación móvil"
        className="fixed inset-x-3 bottom-3 z-30 grid grid-cols-4 rounded-2xl border border-slate-200/90 bg-white/95 p-1.5 shadow-[0_18px_48px_rgba(15,23,42,0.18)] backdrop-blur-lg lg:hidden"
        style={{ bottom: "max(0.75rem, env(safe-area-inset-bottom))" }}
      >
        {mobileItems.map(({ href, label, icon: Icon }) => {
          const active = pathname === href || pathname.startsWith(`${href}/`);
          return (
            <Link
              key={href}
              href={href}
              aria-current={active ? "page" : undefined}
              className={`flex min-h-14 flex-col items-center justify-center gap-1 rounded-xl px-1 text-[10px] font-semibold transition ${active ? "bg-slate-900 text-white" : "text-slate-500 hover:bg-slate-100 hover:text-ink"}`}
              onClick={() => setOpen(false)}
            >
              <Icon className="h-[18px] w-[18px]" />
              <span>{label}</span>
            </Link>
          );
        })}
        <button
          type="button"
          aria-label="Abrir más secciones"
          aria-expanded={open}
          className={`flex min-h-14 flex-col items-center justify-center gap-1 rounded-xl px-1 text-[10px] font-semibold transition ${moreActive || open ? "bg-slate-900 text-white" : "text-slate-500 hover:bg-slate-100 hover:text-ink"}`}
          onClick={() => setOpen(true)}
        >
          <Menu className="h-[18px] w-[18px]" />
          <span>Más</span>
        </button>
      </nav>
    </>
  );
}
