import { Megaphone } from "lucide-react";
import { AnnouncementForm } from "@/components/AnnouncementForm";
import { EmptyState } from "@/components/EmptyState";
import { listAnnouncements, listCommunities } from "@/lib/data";
import { formatDate } from "@/lib/utils";

const roleLabel = { all: "Usuarios y administradores", user: "Usuarios", admin: "Administradores" } as const;

export default async function AnnouncementsPage() {
  const [communities, announcements] = await Promise.all([listCommunities(), listAnnouncements()]);
  return (
    <main className="mx-auto grid max-w-5xl gap-5 px-4 py-5 lg:px-6">
      <div>
        <div className="flex items-center gap-2"><Megaphone className="h-6 w-6 text-accent" /><h1 className="text-2xl font-semibold text-ink">Avisos</h1></div>
        <p className="mt-1 text-sm text-muted">Mensajes unidireccionales por comunidad. No incluyen chat ni respuestas.</p>
      </div>
      <AnnouncementForm communities={communities} />
      <section className="grid gap-3">
        <h2 className="text-lg font-semibold text-ink">Historial</h2>
        {announcements.length === 0 ? <EmptyState title="Sin avisos" body="Los avisos enviados aparecerán aquí." /> : announcements.map((item) => (
          <article key={item.announcement_id} className="rounded-md border border-line bg-white p-4 shadow-soft">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div><p className="font-bold text-ink">{item.title}</p><p className="mt-1 whitespace-pre-wrap text-sm text-muted">{item.body}</p></div>
              <span className="rounded-full bg-teal-50 px-2.5 py-1 text-xs font-bold text-teal-700">{roleLabel[item.target_role]}</span>
            </div>
            <p className="mt-3 text-xs text-slate-500">{item.community_name} · {formatDate(item.created_at)}{item.expires_at ? ` · vence ${formatDate(item.expires_at)}` : ""}</p>
          </article>
        ))}
      </section>
    </main>
  );
}
