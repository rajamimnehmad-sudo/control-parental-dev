import {
  AlertTriangle,
  CheckCircle2,
  ChevronDown,
  Clock3,
  Eye,
  EyeOff,
  FlaskConical,
  ShieldCheck,
} from "lucide-react";
import { gloshiaVisualSnapshot } from "@/lib/gloshia-visual";

const percent = new Intl.NumberFormat("es-AR", {
  style: "percent",
  maximumFractionDigits: 1,
});

const number = new Intl.NumberFormat("es-AR");

export default function GloshiaPage() {
  const { active, latestCandidate } = gloshiaVisualSnapshot;
  const correctDecisions =
    active.evaluation.correctFilters + active.evaluation.correctAllows;
  const activeAccuracy = correctDecisions / active.evaluation.binary;
  const activeFilterRecall =
    active.evaluation.correctFilters /
    (active.evaluation.correctFilters + active.evaluation.falseAllows);
  const activeAllowRecall =
    active.evaluation.correctAllows /
    (active.evaluation.correctAllows + active.evaluation.falseFilters);

  return (
    <main className="page-shell">
      <section className="page-heading">
        <div>
          <p className="eyebrow">Filtro de imágenes de DAG</p>
          <h1>GloshIA Visual</h1>
          <p>
            Acá podés ver, en palabras simples, cómo está funcionando el filtro
            que revisa las fotos antes de mostrarlas.
          </p>
        </div>
        <span className="inline-flex w-fit items-center gap-2 rounded-full bg-emerald-50 px-3 py-2 text-xs font-bold text-emerald-700">
          <CheckCircle2 className="h-4 w-4" />
          Funcionando en DAG
        </span>
      </section>

      <section className="rounded-3xl border border-amber-200 bg-amber-50 p-5 sm:p-6">
        <div className="flex items-start gap-4">
          <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-white text-amber-700 shadow-sm">
            <AlertTriangle className="h-5 w-5" />
          </span>
          <div>
            <h2 className="text-xl font-bold text-amber-950">
              Funciona, pero todavía necesita mejorar
            </h2>
            <p className="mt-2 max-w-3xl text-sm leading-6 text-amber-900">
              En la prueba más exigente encontró 8 de cada 10 fotos que debía
              filtrar. El punto débil es que también ocultó muchas fotos que se
              podían mostrar. Por eso seguimos ajustándolo con cuidado.
            </p>
            <p className="mt-2 max-w-3xl text-sm font-semibold leading-6 text-amber-950">
              No es infalible: alguna imagen puede pasar por alto, especialmente
              si es muy pequeña, está recortada o borrosa, aparece como fondo o
              cambia mientras la página está cargando.
            </p>
          </div>
        </div>
      </section>

      <section className="grid gap-4 md:grid-cols-3">
        <SimpleCard
          icon={EyeOff}
          title="Fotos que debía filtrar"
          value="8 de cada 10"
          detail={`${active.evaluation.correctFilters} detectadas y ${active.evaluation.falseAllows} que se escaparon en esta prueba.`}
          tone="emerald"
        />
        <SimpleCard
          icon={Eye}
          title="Fotos que podía mostrar"
          value="5 de cada 10"
          detail={`${active.evaluation.correctAllows} mostradas correctamente y ${active.evaluation.falseFilters} filtradas de más.`}
          tone="amber"
        />
        <SimpleCard
          icon={Clock3}
          title="Velocidad habitual"
          value="Menos de 0,1 segundo"
          detail="Medición de referencia en la Mac. En el teléfono puede variar según la página."
          tone="sky"
        />
      </section>

      <section className="panel">
        <div className="panel-header">
          <div className="flex items-center gap-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-rose-50 text-rose-700">
              <FlaskConical className="h-5 w-5" />
            </span>
            <div>
              <h2 className="section-title">Última mejora probada</h2>
              <p className="muted mt-0.5">Se comparó antes de cambiar el filtro de todos.</p>
            </div>
          </div>
          <span className="rounded-full bg-slate-100 px-3 py-1.5 text-xs font-bold text-slate-700">
            No se aplicó
          </span>
        </div>

        <div className="mt-5 rounded-2xl border border-line bg-slate-50 p-4 sm:p-5">
          <p className="font-bold text-ink">¿Por qué no se aplicó?</p>
          <p className="mt-2 text-sm leading-6 text-slate-600">
            La prueba nueva encontraba una foto problemática más, pero también
            ocultaba más fotos permitidas y el resultado general era peor. Para
            no empeorar DAG, GloshIA conservó la versión anterior.
          </p>
          <div className="mt-4 flex items-center gap-2 text-sm font-bold text-emerald-700">
            <ShieldCheck className="h-4 w-4" />
            El filtro activo no fue reemplazado
          </div>
        </div>
      </section>

      <details className="group panel">
        <summary className="flex cursor-pointer list-none items-center justify-between gap-4">
          <div>
            <h2 className="section-title">Ver información técnica</h2>
            <p className="muted mt-1">Versión, pruebas y datos para diagnóstico.</p>
          </div>
          <ChevronDown className="h-5 w-5 text-slate-500 transition-transform group-open:rotate-180" />
        </summary>

        <div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <TechnicalValue label="Versión activa" value={active.displayVersion} />
          <TechnicalValue label="Reglas de DAG" value={active.policyVersion} />
          <TechnicalValue label="Resultado de la prueba" value={percent.format(activeAccuracy)} />
          <TechnicalValue label="Detección de filtros" value={percent.format(activeFilterRecall)} />
          <TechnicalValue label="Respeto de permitidas" value={percent.format(activeAllowRecall)} />
          <TechnicalValue label="Fotos revisadas por una persona" value={String(active.evaluation.reviewed)} />
          <TechnicalValue label="Banco de prueba" value={`${number.format(active.corpus.total)} fotos`} />
          <TechnicalValue label="Examen reservado" value={`${active.corpus.sealed} fotos`} />
          <TechnicalValue label="Formato" value={active.format} />
          <TechnicalValue label="Umbral" value={active.threshold.toFixed(2)} />
          <TechnicalValue label="Archivo" value={active.artifactName} />
          <TechnicalValue label="Último candidato" value={latestCandidate.displayVersion} />
        </div>

        <p className="mt-4 text-xs leading-5 text-slate-500">
          Estas cifras provienen de una prueba difícil, preparada para encontrar
          fallas. No representan por sí solas todas las páginas de Internet.
          Corte: {gloshiaVisualSnapshot.snapshotDate}.
        </p>
      </details>
    </main>
  );
}

const tones = {
  emerald: "bg-emerald-50 text-emerald-700",
  amber: "bg-amber-50 text-amber-700",
  sky: "bg-sky-50 text-sky-700",
} as const;

function SimpleCard({
  icon: Icon,
  title,
  value,
  detail,
  tone,
}: {
  icon: typeof Eye;
  title: string;
  value: string;
  detail: string;
  tone: keyof typeof tones;
}) {
  return (
    <article className="metric-card">
      <span className={`flex h-10 w-10 items-center justify-center rounded-xl ${tones[tone]}`}>
        <Icon className="h-5 w-5" />
      </span>
      <p className="mt-4 text-sm font-semibold text-slate-600">{title}</p>
      <p className="mt-1 text-2xl font-bold tracking-tight text-ink">{value}</p>
          <p className="mt-2 text-xs leading-5 text-slate-500">{detail}</p>
    </article>
  );
}

function TechnicalValue({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-2xl border border-line bg-slate-50 p-4">
      <p className="text-xs font-semibold text-slate-500">{label}</p>
      <p className="mt-2 break-words text-sm font-bold text-ink">{value}</p>
    </div>
  );
}
