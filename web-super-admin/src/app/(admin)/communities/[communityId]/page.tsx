import Link from "next/link";
import { ArrowLeft, ArrowRight, CalendarClock, CheckCircle2, CreditCard, Download, KeyRound, Mail, MonitorSmartphone, ShieldCheck, Smartphone, UserRound, UsersRound, WifiOff } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { AdminTokenForm } from "@/components/AdminTokenForm";
import { DeleteCommunityButton } from "@/components/DeleteCommunityButton";
import { DeleteAdminButton } from "@/components/DeleteAdminButton";
import { DeleteProtectedUserButton } from "@/components/DeleteProtectedUserButton";
import { DagEntitlementForm } from "@/components/DagEntitlementForm";
import { DeviceDagForm } from "@/components/DeviceDagForm";
import { DeviceRelinkButton } from "@/components/DeviceRelinkButton";
import { EmptyState } from "@/components/EmptyState";
import { LicenseBadge, ProtectedUserBadge } from "@/components/Badge";
import { LicenseForm } from "@/components/LicenseForm";
import { RevokeAdminTokenButton } from "@/components/RevokeAdminTokenButton";
import { getCommunityBundle } from "@/lib/data";
import type { CommunityAdmin, CommunityDevice, DevAppVersions, ProtectedUser } from "@/lib/types";
import { capacitySnapshot, compactNumber, formatDate, formatShortDate } from "@/lib/utils";

type Props = {
  params: Promise<{ communityId: string }>;
  searchParams: Promise<{ section?: string | string[] }>;
};

type CommunitySection = "summary" | "users" | "admins" | "license" | "devices";

const sections: Array<{ id: CommunitySection; label: string }> = [
  { id: "summary", label: "Resumen" },
  { id: "users", label: "Usuarios" },
  { id: "admins", label: "Administradores" },
  { id: "license", label: "Licencia" },
  { id: "devices", label: "Dispositivos y actualizaciones" },
];

export default async function CommunityDetailPage({ params, searchParams }: Props) {
  const { communityId } = await params;
  const section = normalizedSection((await searchParams).section);
  const { detail, admins, protectedUsers, devices, devVersions } = await getCommunityBundle(communityId);
  const orderedUsers = [...protectedUsers].sort((left, right) => userPriority(left) - userPriority(right) || left.display_name.localeCompare(right.display_name, "es"));
  const pendingUsers = protectedUsers.filter((user) => user.status === "pending").length;
  const offlineUsers = protectedUsers.filter((user) => user.last_seen_at && Date.now() - new Date(user.last_seen_at).getTime() >= 24 * 60 * 60 * 1000).length;

  return (
    <main className="community-detail page-shell">
      <div className="sticky top-[64px] z-20 -mx-4 flex flex-col gap-4 border-b border-line bg-canvas/95 px-4 py-4 backdrop-blur sm:-mx-6 sm:top-[72px] sm:px-6 xl:-mx-8 xl:px-8">
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

      <nav aria-label="Secciones de la comunidad" className="-mx-4 flex gap-1 overflow-x-auto border-y border-line bg-white px-4 py-2 sm:mx-0 sm:rounded-2xl sm:border sm:p-2 sm:shadow-panel">
        {sections.map((item) => (
          <Link
            key={item.id}
            href={`/communities/${communityId}?section=${item.id}`}
            aria-current={section === item.id ? "page" : undefined}
            className={`min-h-10 shrink-0 rounded-xl px-3.5 py-2.5 text-sm font-semibold transition ${section === item.id ? "bg-slate-900 text-white" : "text-slate-600 hover:bg-slate-100 hover:text-ink"}`}
          >
            {item.label}
          </Link>
        ))}
      </nav>

      {section === "summary" ? (
        <>
          <section className="grid grid-cols-2 gap-3 md:grid-cols-4">
            <CapacityMetric label="Administradores" used={detail.admin_count} maximum={detail.max_admins} icon={UsersRound} />
            <CapacityMetric label="Usuarios" used={detail.user_device_count} maximum={detail.max_user_devices} icon={MonitorSmartphone} />
            <CapacityMetric label="Dispositivos Admin" used={detail.admin_device_count} maximum={detail.max_admin_devices} icon={Smartphone} />
            <MiniMetric label="Vence" value={formatShortDate(detail.expires_at, "Sin vencimiento")} icon={CalendarClock} />
          </section>
          <section className="grid gap-4 lg:grid-cols-2">
            <article className="panel">
              <div className="flex items-start justify-between gap-3">
                <div><p className="eyebrow">Licencia</p><h2 className="section-title">{detail.plan_name}</h2></div>
                <LicenseBadge status={detail.license_status} />
              </div>
              <dl className="mt-5 grid gap-3 text-sm sm:grid-cols-2">
                <SummaryDetail label="Inicio" value={formatShortDate(detail.starts_at, "Inicio sin informar")} />
                <SummaryDetail label="Vencimiento" value={formatShortDate(detail.expires_at, "Sin vencimiento")} />
                <SummaryDetail label="DAG" value={detail.dag_entitled ? "Habilitado" : "No habilitado"} />
                <SummaryDetail label="Notas internas" value={detail.internal_notes || "Sin notas internas"} />
              </dl>
              <SectionLink href={`/communities/${communityId}?section=license`} label="Administrar licencia y límites" icon={CreditCard} />
            </article>
            <article className="panel">
              <p className="eyebrow">Operación</p>
              <h2 className="section-title">Actividad de la comunidad</h2>
              <div className="mt-5 grid grid-cols-3 gap-3">
                <SummaryCount label="Usuarios" value={protectedUsers.length} />
                <SummaryCount label="Pendientes" value={pendingUsers} />
                <SummaryCount label="Sin conexión" value={offlineUsers} />
              </div>
              <div className="mt-5 grid gap-2 sm:grid-cols-2">
                <SectionLink href={`/communities/${communityId}?section=users`} label="Gestionar usuarios" icon={UserRound} />
                <SectionLink href={`/communities/${communityId}?section=admins`} label="Gestionar administradores" icon={ShieldCheck} />
              </div>
            </article>
          </section>
        </>
      ) : null}

      {section === "users" ? (
        <section className="grid gap-3">
          <SectionTitle title="Usuarios protegidos" count={protectedUsers.length} />
          {protectedUsers.length === 0 ? (
            <EmptyState title="Sin usuarios protegidos" body="Aparecerán cuando un administrador genere tokens de App Usuario o cuando esos celulares se activen." />
          ) : (
            <div className="grid gap-3 xl:grid-cols-2">
              {orderedUsers.map((user) => (
                <ProtectedUserCard key={user.protected_user_id} user={user} communityId={communityId} dagEntitled={detail.dag_entitled} />
              ))}
            </div>
          )}
        </section>
      ) : null}

      {section === "admins" ? (
        <section className="grid gap-4">
          <div className="panel" id="new-admin">
            <div className="panel-header"><div><p className="eyebrow">Acción frecuente</p><h2 className="section-title">Agregar administrador</h2><p className="muted mt-1">Generá un token de activación para una nueva App Admin.</p></div><KeyRound className="h-5 w-5 text-accent" /></div>
            <div className="mt-4"><AdminTokenForm communityId={communityId} /></div>
          </div>
          <SectionTitle title="Administradores" count={admins.length} />
          {admins.length === 0 ? (
            <EmptyState title="Sin administradores" body="Creá el primero con el formulario de esta sección." />
          ) : (
            <div className="grid gap-3 xl:grid-cols-2">{admins.map((admin) => <AdminCard key={admin.admin_id} admin={admin} communityId={communityId} />)}</div>
          )}
        </section>
      ) : null}

      {section === "license" ? (
        <section className="grid gap-4">
          <div><p className="eyebrow">Configuración comercial</p><h2 className="section-title">Licencia y límites</h2><p className="muted mt-1">Los cambios se aplican mediante las funciones seguras del Super Admin.</p></div>
          <div className="grid gap-4 xl:grid-cols-[minmax(280px,.65fr)_minmax(0,1.35fr)]">
            <DagEntitlementForm detail={detail} />
            <LicenseForm detail={detail} compact />
          </div>
        </section>
      ) : null}

      {section === "devices" ? (
        <section className="grid gap-3">
          <SectionTitle title="Dispositivos y actualizaciones" count={devices.length} />
          <p className="text-sm text-slate-500">Estado informado por cada dispositivo frente a la publicación DEV vigente. El reenlace y la versión están junto al equipo correspondiente.</p>
          {devices.length === 0 ? (
            <EmptyState title="Sin dispositivos activos" body="Los equipos aparecerán cuando una App Admin o App Usuario complete su activación." />
          ) : (
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">{devices.map((device) => <DeviceUpdateCard key={device.device_id} device={device} versions={devVersions} communityId={communityId} />)}</div>
          )}
        </section>
      ) : null}

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

function normalizedSection(value: string | string[] | undefined): CommunitySection {
  const candidate = Array.isArray(value) ? value[0] : value;
  return sections.some((section) => section.id === candidate) ? candidate as CommunitySection : "summary";
}

function userPriority(user: ProtectedUser) {
  if (user.status === "pending") return 0;
  if (user.last_seen_at && Date.now() - new Date(user.last_seen_at).getTime() >= 24 * 60 * 60 * 1000) return 1;
  if (user.status === "activated") return 2;
  return 3;
}

function DeviceUpdateCard({ device, versions, communityId }: { device: CommunityDevice; versions: DevAppVersions; communityId: string }) {
  const latest = device.app_role === "admin" ? versions.admin : versions.user;
  const current = device.app_version_code;
  const needsUpdate = latest !== null && current < latest;
  const badgeStyle = latest === null ? "bg-slate-100 text-slate-600" : needsUpdate ? "bg-amber-100 text-amber-800" : "bg-emerald-100 text-emerald-800";
  return (
    <div className="rounded-2xl border border-line bg-white p-4 shadow-panel">
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
      <p className="mt-3 text-xs text-slate-500">Última conexión: {formatDate(device.last_seen_at, "Sin conexión informada")}</p>
      <p className="mt-1 text-xs text-slate-500">
        {device.manufacturer || device.model
          ? `${device.manufacturer ?? ""} ${device.model ?? ""}`.trim()
          : "Modelo pendiente de sincronización"}
        {device.android_version ? ` · Android ${device.android_version}` : ""}
      </p>
      <div className="mt-4 border-t border-line pt-3">
        <DeviceRelinkButton communityId={communityId} deviceId={device.device_id} />
      </div>
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

function CapacityMetric({ label, used, maximum, icon: Icon }: { label: string; used: number; maximum: number | null; icon: LucideIcon }) {
  const capacity = capacitySnapshot(used, maximum);
  return (
    <div className="rounded-md border border-line bg-white p-3 shadow-soft">
      <div className="flex items-center justify-between gap-2">
        <p className="text-xs font-semibold text-slate-500">{label}</p>
        <Icon className="h-4 w-4 text-accent" />
      </div>
      <p className="mt-2 text-lg font-bold text-ink">{compactNumber(capacity.used)} / {capacity.maximum === null ? "Sin límite" : compactNumber(capacity.maximum)}</p>
      <p className={`mt-1 text-xs ${capacity.exceeded ? "font-semibold text-danger" : "text-slate-500"}`}>{capacity.available === null ? "Disponibilidad sin definir" : `${compactNumber(capacity.available)} disponibles`}</p>
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

function SummaryDetail({ label, value }: { label: string; value: string }) {
  return <div><dt className="text-xs font-semibold text-slate-500">{label}</dt><dd className="mt-1 text-sm font-semibold text-ink">{value}</dd></div>;
}

function SummaryCount({ label, value }: { label: string; value: number }) {
  return <div className="rounded-xl bg-slate-50 p-3 text-center"><p className="text-2xl font-bold text-ink">{compactNumber(value)}</p><p className="mt-1 text-[11px] font-semibold text-slate-500">{label}</p></div>;
}

function SectionLink({ href, label, icon: Icon }: { href: string; label: string; icon: LucideIcon }) {
  return (
    <Link href={href} className="mt-5 flex min-h-11 items-center justify-between gap-3 rounded-xl border border-line px-3.5 text-sm font-semibold text-ink transition hover:border-teal-200 hover:bg-teal-50">
      <span className="flex items-center gap-2"><Icon className="h-4 w-4 text-accent" />{label}</span>
      <ArrowRight className="h-4 w-4 text-slate-400" />
    </Link>
  );
}

function AdminCard({ admin, communityId }: { admin: CommunityAdmin; communityId: string }) {
  const active = Boolean(admin.activated_device_id);

  return (
    <article className="rounded-2xl border border-line bg-white p-5 shadow-panel">
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
        <InfoLine icon={Smartphone} label={admin.phone_e164 ?? "Sin WhatsApp informado"} />
        <InfoLine icon={Smartphone} label={admin.activated_device_name ?? "Sin dispositivo activado"} />
        <InfoLine icon={CalendarClock} label={`Última conexión: ${formatDate(admin.last_seen_at, "Sin conexión informada")}`} />
        {!active ? <InfoLine icon={KeyRound} label={`Token pendiente: ${formatDate(admin.pending_token_expires_at, "Sin vencimiento informado")}`} /> : null}
        {!active && admin.pending_token_expires_at ? (
          <RevokeAdminTokenButton communityId={communityId} adminId={admin.admin_id} />
        ) : null}
        {admin.activated_device_id ? <DeviceRelinkButton communityId={communityId} deviceId={admin.activated_device_id} /> : null}
      </div>
    </article>
  );
}

function ProtectedUserCard({ user, communityId, dagEntitled }: { user: ProtectedUser; communityId: string; dagEntitled: boolean }) {
  const lastSeen = user.last_seen_at ? new Date(user.last_seen_at).getTime() : null;
  const offline = lastSeen !== null && Date.now() - lastSeen >= 24 * 60 * 60 * 1000;
  return (
    <article className="rounded-2xl border border-line bg-white p-5 shadow-panel">
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
        <InfoLine icon={Smartphone} label={user.device_id ? `Dispositivo activo · ${user.app_version_code === null ? "versión sin informar" : `v${user.app_version_code}`}` : "Token sin activar"} />
        <InfoLine icon={KeyRound} label={`Token vence: ${formatDate(user.token_expires_at, "Sin vencimiento informado")}`} />
        <InfoLine icon={CalendarClock} label={`Última conexión: ${formatDate(user.last_seen_at, "Sin conexión informada")}`} />
        {offline ? <InfoLine icon={WifiOff} label="Sin comunicación desde hace más de 24 horas" /> : null}
      </div>
      <DeviceDagForm communityId={communityId} deviceId={user.device_id} enabled={user.dag_enabled} entitled={dagEntitled} />
      {user.device_id ? <div className="mt-3"><DeviceRelinkButton communityId={communityId} deviceId={user.device_id} /></div> : null}
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
