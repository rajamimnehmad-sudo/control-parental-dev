import { Megaphone } from "lucide-react";
import { AnnouncementForm } from "@/components/AnnouncementForm";
import { EmptyState } from "@/components/EmptyState";
import { listAnnouncements, listCommunities } from "@/lib/data";
import { formatDate } from "@/lib/utils";
import { ArchiveButton } from "@/components/ArchiveButton";
import { archiveAnnouncementAction } from "@/lib/actions";

const roleLabel = { all: "Usuarios y administradores", user: "Usuarios", admin: "Administradores" } as const;

export default async function AnnouncementsPage() {
  const [communities, announcements] = await Promise.all([listCommunities(), listAnnouncements()]);
  return (
    <main className="page-shell">
      <section className="page-heading">
        <div><p className="eyebrow">Comunicación</p><div className="flex items-center gap-2"><Megaphone className="h-6 w-6 text-accent" /><h1>Avisos</h1></div><p>Mensajes unidireccionales por comunidad, sin chats ni respuestas.</p></div>
      </section>
      <AnnouncementForm communities={communities} />
      <section className="grid gap-3">
        <h2 className="text-lg font-semibold text-ink">Historial</h2>
        {announcements.length === 0 ? <EmptyState title="Sin avisos" body="Los avisos enviados aparecerán aquí." /> : announcements.map((item) => (
          <article key={item.announcement_id} className="rounded-2xl border border-line bg-white p-5 shadow-panel">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div><p className="font-bold text-ink">{item.title}</p><p className="mt-1 whitespace-pre-wrap text-sm text-muted">{item.body}</p></div>
              <span className="rounded-full bg-teal-50 px-2.5 py-1 text-xs font-bold text-teal-700">{roleLabel[item.target_role]}</span>
            </div>
            <p className="mt-3 text-xs text-slate-500">{item.community_name} · {formatDate(item.created_at)}{item.expires_at ? ` · vence ${formatDate(item.expires_at)}` : ""}</p>
            <div className="mt-3"><ArchiveButton id={item.announcement_id} action={archiveAnnouncementAction} label="Borrar aviso" /></div>
          </article>
        ))}
      </section>
    </main>
  );
}
