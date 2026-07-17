import Link from "next/link";
import { ArrowLeft, CalendarClock, CheckCircle2, Download, KeyRound, Mail, MonitorSmartphone, Settings2, ShieldCheck, Smartphone, UserRound, UsersRound } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { AdminTokenForm } from "@/components/AdminTokenForm";
import { DeleteCommunityButton } from "@/components/DeleteCommunityButton";
import { DeleteAdminButton } from "@/components/DeleteAdminButton";
import { DeleteProtectedUserButton } from "@/components/DeleteProtectedUserButton";
import { DagEntitlementForm } from "@/components/DagEntitlementForm";
import { DeviceDagForm } from "@/components/DeviceDagForm";
import { EmptyState } from "@/components/EmptyState";
import { LicenseBadge, ProtectedUserBadge } from "@/components/Badge";
import { LicenseForm } from "@/components/LicenseForm";
import { RevokeAdminTokenButton } from "@/components/RevokeAdminTokenButton";
import { getCommunityBundle } from "@/lib/data";
import type { CommunityAdmin, CommunityDevice, DevAppVersions, ProtectedUser } from "@/lib/types";
import { compactNumber, formatDate } from "@/lib/utils";

type Props = {
  params: Promise<{ communityId: string }>;
};

export default async function CommunityDetailPage({ params }: Props) {
  const { communityId } = await params;
  const { detail, admins, protectedUsers, devices, devVersions } = await getCommunityBundle(communityId);
  const pendingUsers = protectedUsers.filter((user) => user.status === "pending").length;
  const activatedUsers = protectedUsers.filter((user) => user.status === "activated").length;

  return (
    <main className="community-detail mx-auto grid max-w-4xl gap-5 px-4 py-5 lg:px-6">
      <div className="sticky top-0 z-30 -mx-4 flex flex-col gap-4 border-b border-line bg-canvas/95 px-4 py-4 backdrop-blur lg:-mx-6 lg:px-6">
        <Link className="inline-flex items-center gap-2 text-sm font-semibold text-accent" href="/communities">
          <ArrowLeft className="h-4 w-4" />
          Volver
        </Link>
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <h1 className="text-2xl font-semibold text-ink">{detail.name}</h1>
            <p className="mt-1 text-sm text-slate-500">{detail.guide_label}</p>
          </div>
          <div className="flex items-center gap-2">
            <LicenseBadge status={detail.license_status} />
            <span className="text-sm font-medium text-slate-600">{detail.plan_name}</span>
          </div>
        </div>
      </div>

      <section className="grid grid-cols-2 gap-3 md:grid-cols-4">
        <MiniMetric label="Admins" value={`${compactNumber(detail.admin_count)} / ${detail.max_admins ?? "-"}`} icon={UsersRound} />
        <MiniMetric label="Usuarios activos" value={`${compactNumber(activatedUsers)} / ${detail.max_user_devices ?? "-"}`} icon={MonitorSmartphone} />
        <MiniMetric label="Pendientes" value={compactNumber(pendingUsers)} icon={KeyRound} />
        <MiniMetric label="Vence" value={detail.expires_at ? new Date(detail.expires_at).toLocaleDateString("es-AR") : "Sin fecha"} icon={CalendarClock} />
      </section>

      <section className="grid gap-3">
        <SectionTitle title="Administradores" count={admins.length} />
        {admins.length === 0 ? (
          <EmptyState title="Sin administradores" body="Crea un administrador y comparte el token para activar App Admin." />
        ) : (
          <div className="grid gap-3">
            {admins.map((admin) => (
              <AdminCard key={admin.admin_id} admin={admin} communityId={communityId} />
            ))}
          </div>
        )}
      </section>

      <section className="grid gap-3">
        <SectionTitle title="Usuarios protegidos" count={protectedUsers.length} />
        {protectedUsers.length === 0 ? (
          <EmptyState title="Sin usuarios protegidos" body="Aparecerán cuando un administrador genere tokens de App Usuario o cuando esos celulares se activen." />
        ) : (
          <div className="grid gap-3">
            {protectedUsers.map((user) => (
              <ProtectedUserCard key={user.protected_user_id} user={user} communityId={communityId} dagEntitled={detail.dag_entitled} device={devices.find((item) => item.device_id === user.device_id)} versions={devVersions} />
            ))}
          </div>
        )}
      </section>

      <section className="grid gap-3">
        <SectionTitle title="Actualizaciones App Admin" count={devices.filter((device) => device.app_role === "admin").length} />
        <p className="text-sm text-slate-500">
          Estado informado por cada dispositivo frente a la publicación DEV vigente. Android pide confirmación para instalar.
        </p>
        {devices.filter((device) => device.app_role === "admin").length === 0 ? (
          <EmptyState title="Sin administradores activos" body="El estado aparecerá cuando una App Admin complete su activación." />
        ) : (
          <div className="grid gap-3 sm:grid-cols-2">
            {devices.filter((device) => device.app_role === "admin").map((device) => (
              <DeviceUpdateCard key={device.device_id} device={device} versions={devVersions} />
            ))}
          </div>
        )}
      </section>

      <section className="grid gap-3">
        <SectionTitle title="Acciones" />
        <details className="group rounded-md border border-line bg-white shadow-soft">
          <summary className="flex cursor-pointer list-none items-center justify-between gap-3 p-4">
            <div className="flex items-center gap-2">
              <KeyRound className="h-5 w-5 text-accent" />
              <h2 className="text-base font-semibold text-ink">Agregar administrador</h2>
            </div>
            <span className="text-sm font-semibold text-accent group-open:hidden">Abrir</span>
            <span className="hidden text-sm font-semibold text-accent group-open:inline">Cerrar</span>
          </summary>
          <div className="border-t border-line p-4">
            <AdminTokenForm communityId={communityId} />
          </div>
        </details>

        <details className="group rounded-md border border-line bg-white shadow-soft">
          <summary className="flex cursor-pointer list-none items-center justify-between gap-3 p-4">
            <div className="flex items-center gap-2">
              <Settings2 className="h-5 w-5 text-accent" />
              <h2 className="text-base font-semibold text-ink">Licencia y límites</h2>
            </div>
            <span className="text-sm font-semibold text-accent group-open:hidden">Abrir</span>
            <span className="hidden text-sm font-semibold text-accent group-open:inline">Cerrar</span>
          </summary>
          <div className="border-t border-line p-4">
            <div className="grid gap-4">
              <DagEntitlementForm detail={detail} />
              <LicenseForm detail={detail} compact />
            </div>
          </div>
        </details>
      </section>

      <details className="group rounded-md border border-red-200 bg-red-50">
        <summary className="flex cursor-pointer list-none items-center justify-between gap-3 p-4">
          <h2 className="text-base font-bold text-danger">Zona de peligro</h2>
          <span className="text-sm font-semibold text-danger group-open:hidden">Abrir</span>
          <span className="hidden text-sm font-semibold text-danger group-open:inline">Cerrar</span>
        </summary>
        <div className="border-t border-red-200 p-4">
          <DeleteCommunityButton communityId={communityId} communityName={detail.name} />
        </div>
      </details>
    </main>
  );
}

function DeviceUpdateCard({ device, versions }: { device: CommunityDevice; versions: DevAppVersions }) {
  const latest = device.app_role === "admin" ? versions.admin : versions.user;
  const current = device.app_version_code;
  const needsUpdate = latest !== null && current < latest;
  const badgeStyle = latest === null ? "bg-slate-100 text-slate-600" : needsUpdate ? "bg-amber-100 text-amber-800" : "bg-emerald-100 text-emerald-800";
  return (
    <div className="rounded-md border border-line bg-white p-4 shadow-soft">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate text-sm font-bold text-ink">{device.display_name}</p>
          <p className="mt-1 text-xs font-medium text-slate-500">App {device.app_role === "admin" ? "Admin" : "Usuario"} · v{current}</p>
        </div>
        <span className={`inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-bold ${badgeStyle}`}>
          {needsUpdate ? <Download className="h-3.5 w-3.5" /> : latest === null ? <Smartphone className="h-3.5 w-3.5" /> : <CheckCircle2 className="h-3.5 w-3.5" />}
          {needsUpdate ? `Actualizar a v${latest}` : latest === null ? "Sin manifiesto" : "Actualizada"}
        </span>
      </div>
      <p className="mt-3 text-xs text-slate-500">Última conexión: {formatDate(device.last_seen_at)}</p>
    </div>
  );
}

function MiniMetric({ label, value, icon: Icon }: { label: string; value: string; icon: LucideIcon }) {
  return (
    <div className="rounded-md border border-line bg-white p-3 shadow-soft">
      <div className="flex items-center justify-between gap-2">
        <p className="text-xs font-semibold text-slate-500">{label}</p>
        <Icon className="h-4 w-4 text-accent" />
      </div>
      <p className="mt-2 text-lg font-bold text-ink">{value}</p>
    </div>
  );
}

function SectionTitle({ title, count }: { title: string; count?: number }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <h2 className="text-lg font-semibold text-ink">{title}</h2>
      {typeof count === "number" ? <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-600">{count} total</span> : null}
    </div>
  );
}

function AdminCard({ admin, communityId }: { admin: CommunityAdmin; communityId: string }) {
  const active = Boolean(admin.activated_device_id);

  return (
    <article className="rounded-md border border-line bg-white p-4 shadow-soft">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <ShieldCheck className="h-5 w-5 shrink-0 text-accent" />
            <h3 className="truncate text-base font-bold text-ink">{admin.display_name}</h3>
          </div>
          <p className="mt-1 text-sm font-medium text-slate-500">{active ? "Activado" : "Token generado"}</p>
        </div>
        <DeleteAdminButton communityId={communityId} adminId={admin.admin_id} adminName={admin.display_name} />
      </div>
      <div className="mt-4 grid gap-2 text-sm text-slate-600">
        <InfoLine icon={Mail} label={admin.email ?? "Sin email todavía"} />
        <InfoLine icon={Smartphone} label={admin.activated_device_name ?? "Sin dispositivo activado"} />
        <InfoLine icon={CalendarClock} label={`Última conexión: ${formatDate(admin.last_seen_at)}`} />
        {!active ? <InfoLine icon={KeyRound} label={`Token pendiente: ${formatDate(admin.pending_token_expires_at)}`} /> : null}
        {!active && admin.pending_token_expires_at ? (
          <RevokeAdminTokenButton communityId={communityId} adminId={admin.admin_id} />
        ) : null}
      </div>
    </article>
  );
}

function ProtectedUserCard({ user, communityId, dagEntitled, device, versions }: { user: ProtectedUser; communityId: string; dagEntitled: boolean; device?: CommunityDevice; versions: DevAppVersions }) {
  return (
    <article className="rounded-md border border-line bg-white p-4 shadow-soft">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <UserRound className="h-5 w-5 shrink-0 text-accent" />
            <h3 className="truncate text-base font-bold text-ink">{user.display_name}</h3>
          </div>
          <div className="mt-2">
            <ProtectedUserBadge status={user.status} />
          </div>
        </div>
        <DeleteProtectedUserButton communityId={communityId} protectedUserId={user.protected_user_id} userName={user.display_name} />
      </div>
      <div className="mt-4 grid gap-2 text-sm text-slate-600">
        <InfoLine icon={ShieldCheck} label={`Creado por: ${user.creator_admin_name ?? "Sin dato"}`} />
        <InfoLine icon={Smartphone} label={user.device_id ? `Dispositivo activo · v${user.app_version_code ?? "-"}` : "Token sin activar"} />
        <InfoLine icon={KeyRound} label={`Token vence: ${formatDate(user.token_expires_at)}`} />
        <InfoLine icon={CalendarClock} label={`Última conexión: ${formatDate(user.last_seen_at)}`} />
      </div>
      <DeviceDagForm communityId={communityId} deviceId={user.device_id} enabled={user.dag_enabled} entitled={dagEntitled} />
      {device ? <div className="mt-3"><DeviceUpdateCard device={device} versions={versions} /></div> : null}
    </article>
  );
}

function InfoLine({ icon: Icon, label }: { icon: LucideIcon; label: string }) {
  return (
    <div className="flex items-start gap-2">
      <Icon className="mt-0.5 h-4 w-4 shrink-0 text-slate-400" />
      <span>{label}</span>
    </div>
  );
}
