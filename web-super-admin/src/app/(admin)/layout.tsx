import Link from "next/link";
import { LogOut, ShieldCheck } from "lucide-react";
import { AdminNavigation } from "@/components/AdminNavigation";
import { requireSuperAdmin } from "@/lib/auth";
import { signOutAction } from "@/lib/actions";
import { getDeploymentBuildLabel } from "@/lib/build-info";

export const dynamic = "force-dynamic";

export default async function AdminLayout({ children }: { children: React.ReactNode }) {
  const claims = await requireSuperAdmin();
  const email = typeof claims.email === "string" ? claims.email : "Super Admin";
  const buildLabel = getDeploymentBuildLabel();

  return (
    <div className="min-h-screen bg-canvas">
      <header className="super-admin-header sticky top-0 z-40 border-b border-white/70 bg-white/90 shadow-sm backdrop-blur-xl">
        <div className="mx-auto flex max-w-7xl items-center justify-between gap-3 px-4 py-3 lg:px-6">
          <Link href="/communities" className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-gradient-to-br from-teal-50 to-cyan-100 text-accent shadow-sm">
              <ShieldCheck className="h-5 w-5" />
            </div>
            <div>
              <p className="text-base font-semibold text-ink">Super Admin</p>
              <p className="text-xs text-slate-500">{buildLabel}</p>
            </div>
          </Link>
          <div className="flex items-center gap-2">
            <span className="hidden max-w-48 truncate text-sm text-slate-500 lg:block">{email}</span>
            <form action={signOutAction}>
              <button className="button button-secondary" type="submit" aria-label="Cerrar sesion">
                <LogOut className="h-4 w-4" />
              </button>
            </form>
          </div>
        </div>
      </header>
      <div className="hidden border-b border-line/70 bg-white md:block">
        <div className="mx-auto max-w-7xl px-4 lg:px-6"><AdminNavigation /></div>
      </div>
      <div className="pb-24 md:pb-0">{children}</div>
      <div className="fixed inset-x-0 bottom-0 z-50 border-t border-line/80 bg-white/95 px-2 pb-[max(0.5rem,env(safe-area-inset-bottom))] pt-2 shadow-[0_-8px_30px_rgba(15,23,42,0.08)] backdrop-blur-xl md:hidden">
        <AdminNavigation />
      </div>
    </div>
  );
}
