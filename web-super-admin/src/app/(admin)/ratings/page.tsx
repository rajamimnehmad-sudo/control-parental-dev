import { MessageSquareText, Smartphone, Star } from "lucide-react";
import { listAppRatings } from "@/lib/data";
import { formatDate } from "@/lib/utils";

export default async function RatingsPage() {
  const ratings = await listAppRatings();
  const userRatings = ratings.filter((rating) => rating.app_role === "user");
  const adminRatings = ratings.filter((rating) => rating.app_role === "admin");
  return (
    <main className="page-shell">
      <section className="page-heading">
        <div><p className="eyebrow">Experiencia de producto</p><h1>Valoraciones</h1><p>Seguimiento separado de la experiencia de usuarios protegidos y administradores.</p></div>
      </section>

      <section className="grid gap-4 sm:grid-cols-3">
        <RatingMetric label="Promedio general" ratings={ratings} />
        <RatingMetric label="App Usuario" ratings={userRatings} />
        <RatingMetric label="App Administrador" ratings={adminRatings} />
      </section>

      <section className="panel">
        <div className="panel-header">
          <div className="flex items-center gap-3"><span className="flex h-10 w-10 items-center justify-center rounded-xl bg-violet-50 text-violet-700"><MessageSquareText className="h-5 w-5" /></span><div><h2 className="section-title">Comentarios recientes</h2><p className="muted mt-0.5">Opiniones enviadas desde Ajustes.</p></div></div>
          <span className="rounded-full bg-slate-100 px-3 py-1.5 text-xs font-bold text-slate-600">{ratings.length} respuestas</span>
        </div>
        {ratings.length === 0 ? (
          <div className="grid place-items-center py-16 text-center"><span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-slate-100 text-slate-400"><Star className="h-5 w-5" /></span><p className="mt-4 font-semibold text-ink">Todavía no hay valoraciones</p><p className="mt-1 text-sm text-slate-500">Aparecerán cuando se envíen desde las aplicaciones.</p></div>
        ) : (
          <div className="mt-1 divide-y divide-line">
            {ratings.map((rating) => (
              <article key={rating.rating_id} className="grid gap-3 py-5 lg:grid-cols-[minmax(0,1fr)_auto]">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="font-bold text-ink">{rating.device_name}</p>
                    <span className={`rounded-full px-2.5 py-1 text-[11px] font-bold ${rating.app_role === "user" ? "bg-sky-50 text-sky-700" : "bg-teal-50 text-teal-700"}`}>App {rating.app_role === "user" ? "Usuario" : "Administrador"}</span>
                  </div>
                  <p className="mt-1 text-xs text-slate-500">{rating.community_name ?? "Sin comunidad"} · versión {rating.app_version_code}</p>
                  {rating.comment ? <p className="mt-3 max-w-3xl text-sm leading-6 text-slate-700">{rating.comment}</p> : <p className="mt-3 text-sm italic text-slate-400">Sin comentario escrito</p>}
                  <p className="mt-3 flex items-center gap-1.5 text-xs text-slate-500"><Smartphone className="h-3.5 w-3.5" />{[rating.manufacturer, rating.model, rating.android_version ? `Android ${rating.android_version}` : null].filter(Boolean).join(" · ") || "Dispositivo sin sincronizar"} · {formatDate(rating.updated_at)}</p>
                </div>
                <Stars value={rating.stars} />
              </article>
            ))}
          </div>
        )}
      </section>
    </main>
  );
}

function RatingMetric({ label, ratings }: { label: string; ratings: Array<{ stars: number }> }) {
  const average = ratings.length ? ratings.reduce((sum, rating) => sum + rating.stars, 0) / ratings.length : 0;
  return <article className="metric-card"><p className="metric-label">{label}</p><div className="mt-3 flex items-end gap-2"><p className="text-3xl font-bold tracking-tight text-ink">{average ? average.toFixed(1) : "—"}</p><Star className="mb-1 h-5 w-5 fill-amber-400 text-amber-400" /></div><p className="mt-1 text-xs text-slate-500">{ratings.length} valoraciones</p></article>;
}

function Stars({ value }: { value: number }) {
  return <div className="flex w-fit rounded-xl bg-amber-50 px-3 py-2" aria-label={`${value} de 5 estrellas`}>{[1, 2, 3, 4, 5].map((star) => <Star key={star} className={`h-4 w-4 ${star <= value ? "fill-amber-400 text-amber-400" : "text-amber-200"}`} />)}</div>;
}
