import { LogOut, UserRound } from "lucide-react";
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
    <div className="min-h-screen bg-canvas lg:grid lg:grid-cols-[272px_minmax(0,1fr)]">
      <div className="hidden bg-sidebar lg:block">
        <div className="fixed inset-y-0 w-[272px] p-5">
          <AdminNavigation />
        </div>
      </div>

      <div className="min-w-0">
        <header className="app-topbar">
          <div className="flex min-w-0 items-center gap-3">
            <div className="lg:hidden"><AdminNavigation /></div>
            <div className="hidden min-w-0 sm:block">
              <p className="truncate text-sm font-semibold text-ink">{email}</p>
              <p className="text-xs text-slate-500">{buildLabel}</p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <span className="system-status"><span className="status-dot" />Sistema operativo</span>
            <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-slate-100 text-slate-600">
              <UserRound className="h-4 w-4" />
            </span>
            <form action={signOutAction}>
              <button className="icon-button border border-line bg-white" type="submit" aria-label="Cerrar sesión">
                <LogOut className="h-4 w-4" />
              </button>
            </form>
          </div>
        </header>
        <div className="min-w-0">{children}</div>
      </div>
    </div>
  );
}
