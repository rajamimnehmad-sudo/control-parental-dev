"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { BarChart3, BellRing, BrainCircuit, Building2, Database, Megaphone } from "lucide-react";

const items = [
  { href: "/communities", label: "Comunidades", shortLabel: "Inicio", icon: Building2 },
  { href: "/web-protection/domain-list", label: "Base Web", shortLabel: "Base", icon: Database },
  { href: "/dag-usage", label: "Uso DAG", shortLabel: "DAG", icon: BarChart3 },
  { href: "/dag-calibration", label: "Calibración DAG", shortLabel: "Calibrar", icon: BrainCircuit },
  { href: "/alerts", label: "Alertas", shortLabel: "Alertas", icon: BellRing },
  { href: "/announcements", label: "Avisos", shortLabel: "Avisos", icon: Megaphone },
] as const;

export function AdminNavigation() {
  const pathname = usePathname();

  return (
    <nav aria-label="Navegación principal" className="admin-navigation">
      {items.map(({ href, label, shortLabel, icon: Icon }) => {
        const active = pathname === href || pathname.startsWith(`${href}/`);
        return (
          <Link
            key={href}
            href={href}
            aria-current={active ? "page" : undefined}
            className={active ? "admin-nav-item admin-nav-item-active" : "admin-nav-item"}
          >
            <Icon className="h-5 w-5" />
            <span className="hidden md:inline">{label}</span>
            <span className="md:hidden">{shortLabel}</span>
          </Link>
        );
      })}
    </nav>
  );
}
