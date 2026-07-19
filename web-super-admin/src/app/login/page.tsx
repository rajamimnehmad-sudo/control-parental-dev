import { ShieldCheck } from "lucide-react";
import { LoginForm } from "@/components/LoginForm";

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ passwordUpdated?: string; recoveryError?: string }>;
}) {
  const params = await searchParams;

  return (
    <main className="flex min-h-screen items-center justify-center bg-canvas px-4 py-10">
      <section className="w-full max-w-md rounded-md border border-line bg-white p-6 shadow-soft">
        <div className="mb-6 flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-md bg-teal-50 text-accent">
            <ShieldCheck className="h-5 w-5" />
          </div>
          <div>
            <h1 className="text-xl font-semibold text-ink">Super Admin</h1>
            <p className="text-sm text-slate-500">Content Filter</p>
          </div>
        </div>
        <LoginForm
          passwordUpdated={params.passwordUpdated === "1"}
          recoveryError={params.recoveryError}
        />
      </section>
    </main>
  );
}
