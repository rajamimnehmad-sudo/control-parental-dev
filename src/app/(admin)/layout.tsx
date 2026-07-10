import Link from "next/link";
import { Building2, LogOut, ShieldCheck } from "lucide-react";
import { requireSuperAdmin } from "@/lib/auth";
import { signOutAction } from "@/lib/actions";

export const dynamic = "force-dynamic";

export default async function AdminLayout({ children }: { children: React.ReactNode }) {
  const claims = await requireSuperAdmin();
  const email = typeof claims.email === "string" ? claims.email : "Super Admin";

  return (
    <div className="min-h-screen bg-canvas">
      <header className="border-b border-line bg-white">
        <div className="mx-auto flex max-w-7xl flex-col gap-3 px-4 py-4 sm:flex-row sm:items-center sm:justify-between lg:px-6">
          <Link href="/communities" className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-md bg-teal-50 text-accent">
              <ShieldCheck className="h-5 w-5" />
            </div>
            <div>
              <p className="text-base font-semibold text-ink">Super Admin</p>
              <p className="text-xs text-slate-500">Comunidades, licencias y dispositivos</p>
            </div>
          </Link>
          <div className="flex flex-wrap items-center gap-3">
            <nav className="flex items-center gap-2 text-sm font-medium text-slate-600">
              <Link className="button button-secondary" href="/communities">
                <Building2 className="h-4 w-4" />
                Comunidades
              </Link>
            </nav>
            <span className="max-w-64 truncate text-sm text-slate-500">{email}</span>
            <form action={signOutAction}>
              <button className="button button-secondary" type="submit" aria-label="Cerrar sesion">
                <LogOut className="h-4 w-4" />
              </button>
            </form>
          </div>
        </div>
      </header>
      {children}
    </div>
  );
}
