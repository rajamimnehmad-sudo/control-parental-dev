import { MessageSquareText, Star } from "lucide-react";
import { listAppRatings } from "@/lib/data";
import { formatDate } from "@/lib/utils";

export default async function RatingsPage() {
  const ratings = await listAppRatings();
  const userRatings = ratings.filter((rating) => rating.app_role === "user");
  const adminRatings = ratings.filter((rating) => rating.app_role === "admin");
  return (
    <main className="page-shell">
      <section className="page-heading">
        <div><p className="eyebrow">Opiniones</p><h1>Valoraciones</h1><p>Calificaciones separadas de App Usuario y App Administrador.</p></div>
      </section>
      <section className="grid gap-3 sm:grid-cols-3">
        <RatingMetric label="Promedio general" ratings={ratings} />
        <RatingMetric label="App Usuario" ratings={userRatings} />
        <RatingMetric label="App Administrador" ratings={adminRatings} />
      </section>
      <section className="panel">
        <div className="flex items-center gap-2"><MessageSquareText className="h-5 w-5 text-accent" /><h2 className="section-title">Comentarios recientes</h2></div>
        <div className="mt-4 grid gap-3">
          {ratings.length === 0 ? <p className="muted">Todavía no hay valoraciones.</p> : ratings.map((rating) => (
            <article key={rating.rating_id} className="rounded-xl border border-line bg-slate-50 p-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div><p className="font-semibold text-ink">{rating.device_name}</p><p className="text-xs text-slate-500">{rating.community_name ?? "Sin comunidad"} · App {rating.app_role === "user" ? "Usuario" : "Administrador"} · v{rating.app_version_code}</p></div>
                <Stars value={rating.stars} />
              </div>
              {rating.comment ? <p className="mt-3 text-sm text-slate-700">{rating.comment}</p> : <p className="mt-3 text-sm italic text-slate-400">Sin comentario</p>}
              <p className="mt-3 text-xs text-slate-500">{[rating.manufacturer, rating.model, rating.android_version ? `Android ${rating.android_version}` : null].filter(Boolean).join(" · ")} · {formatDate(rating.updated_at)}</p>
            </article>
          ))}
        </div>
      </section>
    </main>
  );
}

function RatingMetric({ label, ratings }: { label: string; ratings: Array<{ stars: number }> }) {
  const average = ratings.length ? ratings.reduce((sum, rating) => sum + rating.stars, 0) / ratings.length : 0;
  return <div className="metric-card"><p className="metric-label">{label}</p><div className="mt-2 flex items-end gap-2"><p className="text-3xl font-bold text-ink">{average ? average.toFixed(1) : "—"}</p><Star className="mb-1 h-5 w-5 fill-amber-400 text-amber-400" /></div><p className="muted mt-1">{ratings.length} valoraciones</p></div>;
}

function Stars({ value }: { value: number }) {
  return <div className="flex" aria-label={`${value} de 5 estrellas`}>{[1, 2, 3, 4, 5].map((star) => <Star key={star} className={`h-4 w-4 ${star <= value ? "fill-amber-400 text-amber-400" : "text-slate-300"}`} />)}</div>;
}
