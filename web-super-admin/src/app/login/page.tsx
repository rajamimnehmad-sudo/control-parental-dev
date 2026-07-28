import { LockKeyhole, ShieldCheck } from "lucide-react";
import { LoginForm } from "@/components/LoginForm";
import { getDeploymentBuildLabel } from "@/lib/build-info";

export default async function LoginPage({ searchParams }: { searchParams: Promise<{ passwordUpdated?: string; recoveryError?: string }> }) {
  const params = await searchParams;
  const buildLabel = getDeploymentBuildLabel();

  return (
    <main className="grid min-h-screen bg-sidebar lg:grid-cols-[minmax(0,1.05fr)_minmax(520px,.95fr)]">
      <section className="relative hidden overflow-hidden p-12 lg:flex lg:flex-col lg:justify-between">
        <div className="absolute -left-40 top-1/3 h-96 w-96 rounded-full bg-teal-400/10 blur-3xl" />
        <div className="relative flex items-center gap-3"><span className="brand-mark"><ShieldCheck className="h-5 w-5" /></span><div><p className="text-lg font-bold text-white">Glosh</p><p className="text-[10px] font-bold uppercase tracking-[0.2em] text-slate-400">Control Center</p></div></div>
        <div className="relative max-w-xl">
          <p className="text-xs font-bold uppercase tracking-[0.2em] text-teal-300">Administración central</p>
          <h1 className="mt-5 text-5xl font-bold leading-[1.08] tracking-tight text-white">Protección clara.<br />Control responsable.</h1>
          <p className="mt-6 max-w-lg text-lg leading-8 text-slate-300">Gestioná comunidades, dispositivos y políticas desde un entorno privado diseñado para decisiones importantes.</p>
        </div>
        <p className="relative text-xs text-slate-500">Entorno protegido · Acceso restringido</p>
      </section>
      <section className="flex items-center justify-center bg-canvas px-4 py-10 sm:px-8">
        <div className="w-full max-w-md">
          <div className="mb-8 lg:hidden"><div className="flex items-center gap-3"><span className="brand-mark"><ShieldCheck className="h-5 w-5" /></span><div><p className="text-lg font-bold text-ink">Glosh</p><p className="text-[10px] font-bold uppercase tracking-[0.2em] text-slate-500">Control Center</p></div></div></div>
          <div className="rounded-3xl border border-line bg-white p-6 shadow-xl shadow-slate-900/5 sm:p-8">
            <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-teal-50 text-accent"><LockKeyhole className="h-5 w-5" /></span>
            <h2 className="mt-6 text-2xl font-bold tracking-tight text-ink">Ingresar a la plataforma</h2>
            <p className="mt-2 text-sm leading-6 text-slate-500">Usá tu cuenta autorizada de Super Administrador.</p>
            <div className="mt-7"><LoginForm passwordUpdated={params.passwordUpdated === "1"} recoveryError={params.recoveryError} /></div>
          </div>
          <p className="mt-5 text-center text-xs text-slate-400">{buildLabel}</p>
        </div>
      </section>
    </main>
  );
}
