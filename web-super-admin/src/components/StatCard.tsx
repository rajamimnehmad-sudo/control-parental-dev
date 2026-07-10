import type { LucideIcon } from "lucide-react";

export function StatCard({ label, value, icon: Icon }: { label: string; value: string; icon: LucideIcon }) {
  return (
    <div className="rounded-md border border-line bg-white p-4 shadow-soft">
      <div className="flex items-center justify-between gap-3">
        <p className="text-sm font-medium text-slate-500">{label}</p>
        <Icon className="h-4 w-4 text-accent" />
      </div>
      <p className="mt-3 text-2xl font-semibold text-ink">{value}</p>
    </div>
  );
}
