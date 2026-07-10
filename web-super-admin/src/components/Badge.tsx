import { cn } from "@/lib/utils";
import type { LicenseStatus, ProtectedUserStatus } from "@/lib/types";

const labels: Record<LicenseStatus, string> = {
  active: "Activa",
  suspended: "Suspendida",
  expired: "Vencida",
};

const styles: Record<LicenseStatus, string> = {
  active: "border-teal-200 bg-teal-50 text-teal-800",
  suspended: "border-amber-200 bg-amber-50 text-warning",
  expired: "border-red-200 bg-red-50 text-danger",
};

export function LicenseBadge({ status }: { status: LicenseStatus }) {
  return (
    <span className={cn("inline-flex items-center rounded-full border px-2.5 py-1 text-xs font-semibold", styles[status])}>
      {labels[status]}
    </span>
  );
}

export function RoleBadge({ role }: { role: "admin" | "user" }) {
  return (
    <span className="inline-flex items-center rounded-full border border-slate-200 bg-white px-2.5 py-1 text-xs font-semibold text-slate-700">
      {role === "admin" ? "Admin" : "Usuario"}
    </span>
  );
}

const protectedUserLabels: Record<ProtectedUserStatus, string> = {
  pending: "Token generado",
  activated: "Activado",
  expired: "Token vencido",
};

const protectedUserStyles: Record<ProtectedUserStatus, string> = {
  pending: "border-amber-200 bg-amber-50 text-warning",
  activated: "border-teal-200 bg-teal-50 text-teal-800",
  expired: "border-red-200 bg-red-50 text-danger",
};

export function ProtectedUserBadge({ status }: { status: ProtectedUserStatus }) {
  return (
    <span className={cn("inline-flex items-center rounded-full border px-2.5 py-1 text-xs font-semibold", protectedUserStyles[status])}>
      {protectedUserLabels[status]}
    </span>
  );
}
