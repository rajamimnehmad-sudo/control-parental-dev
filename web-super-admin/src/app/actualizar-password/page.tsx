import { ShieldCheck } from "lucide-react";
import { UpdatePasswordForm } from "@/components/UpdatePasswordForm";
import { requireSuperAdmin } from "@/lib/auth";

export default async function UpdatePasswordPage() {
  await requireSuperAdmin();

  return (
    <main className="flex min-h-screen items-center justify-center bg-canvas px-4 py-10">
      <section className="w-full max-w-md rounded-md border border-line bg-white p-6 shadow-soft">
        <div className="mb-6 flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-md bg-teal-50 text-accent">
            <ShieldCheck className="h-5 w-5" />
          </div>
          <div>
            <h1 className="text-xl font-semibold text-ink">Contraseña nueva</h1>
            <p className="text-sm text-slate-500">El cambio cerrará las sesiones anteriores.</p>
          </div>
        </div>
        <UpdatePasswordForm />
      </section>
    </main>
  );
}
