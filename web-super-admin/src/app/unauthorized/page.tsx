import { Lock } from "lucide-react";
import { signOutAction } from "@/lib/actions";

export default function UnauthorizedPage() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-canvas px-4 py-10">
      <section className="w-full max-w-lg rounded-md border border-line bg-white p-6 text-center shadow-soft">
        <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-md bg-red-50 text-danger">
          <Lock className="h-5 w-5" />
        </div>
        <h1 className="mt-4 text-xl font-semibold text-ink">Acceso no autorizado</h1>
        <p className="mt-2 text-sm text-slate-500">Tu usuario existe en Supabase Auth, pero no esta habilitado como Super Admin.</p>
        <form action={signOutAction} className="mt-6">
          <button className="button button-secondary" type="submit">
            Cerrar sesion
          </button>
        </form>
      </section>
    </main>
  );
}
