import { ArrowLeft, ShieldCheck } from "lucide-react";
import Link from "next/link";
import { PasswordRecoveryForm } from "@/components/PasswordRecoveryForm";

export default function PasswordRecoveryPage() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-canvas px-4 py-10">
      <section className="w-full max-w-md rounded-md border border-line bg-white p-6 shadow-soft">
        <div className="mb-6 flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-md bg-teal-50 text-accent">
            <ShieldCheck className="h-5 w-5" />
          </div>
          <div>
            <h1 className="text-xl font-semibold text-ink">Recuperar contraseña</h1>
            <p className="text-sm text-slate-500">Te enviaremos un enlace de un solo uso.</p>
          </div>
        </div>
        <PasswordRecoveryForm />
        <Link className="mt-5 flex items-center justify-center gap-2 text-sm font-medium text-accent hover:underline" href="/login">
          <ArrowLeft className="h-4 w-4" />
          Volver al ingreso
        </Link>
      </section>
    </main>
  );
}
