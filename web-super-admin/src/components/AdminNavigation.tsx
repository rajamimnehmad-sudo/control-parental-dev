"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { BarChart3, BellRing, BrainCircuit, Building2, Database, Menu, Megaphone, Star, X } from "lucide-react";
import { useState } from "react";

const items = [
  { href: "/communities", label: "Comunidades", shortLabel: "Inicio", icon: Building2 },
  { href: "/web-protection/domain-list", label: "Base Web", shortLabel: "Base", icon: Database },
  { href: "/ratings", label: "Valoraciones", shortLabel: "Opiniones", icon: Star },
  { href: "/dag-usage", label: "Uso DAG", shortLabel: "DAG", icon: BarChart3 },
  { href: "/dag-calibration", label: "Calibración DAG", shortLabel: "Calibrar", icon: BrainCircuit },
  { href: "/alerts", label: "Alertas", shortLabel: "Alertas", icon: BellRing },
  { href: "/announcements", label: "Avisos", shortLabel: "Avisos", icon: Megaphone },
] as const;

export function AdminNavigation() {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);

  return (
    <>
      <button className="mobile-menu-button md:hidden" type="button" aria-expanded={open} onClick={() => setOpen(true)}>
        <Menu className="h-5 w-5" />Menú
      </button>
      {open ? <button className="fixed inset-0 z-40 bg-slate-950/35 md:hidden" aria-label="Cerrar menú" onClick={() => setOpen(false)} /> : null}
      <aside className={`admin-sidebar ${open ? "admin-sidebar-open" : ""}`}>
        <div className="mb-4 flex items-center justify-between md:hidden"><p className="font-bold text-ink">Navegación</p><button className="icon-button" onClick={() => setOpen(false)}><X className="h-5 w-5" /></button></div>
        <nav aria-label="Navegación principal" className="grid gap-1">
      {items.map(({ href, label, icon: Icon }) => {
        const active = pathname === href || pathname.startsWith(`${href}/`);
        return (
          <Link
            key={href}
            href={href}
            aria-current={active ? "page" : undefined}
            className={active ? "admin-nav-item admin-nav-item-active" : "admin-nav-item"}
            onClick={() => setOpen(false)}
          >
            <Icon className="h-5 w-5" />
            <span>{label}</span>
          </Link>
        );
      })}
        </nav>
      </aside>
    </>
  );
}
