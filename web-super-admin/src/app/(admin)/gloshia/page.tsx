import {
  AlertTriangle,
  BrainCircuit,
  CheckCircle2,
  Clock3,
  Database,
  Fingerprint,
  FlaskConical,
  Gauge,
  Layers3,
  ShieldCheck,
  XCircle,
} from "lucide-react";
import { gloshiaVisualSnapshot } from "@/lib/gloshia-visual";

const percent = new Intl.NumberFormat("es-AR", {
  style: "percent",
  maximumFractionDigits: 1,
});

const number = new Intl.NumberFormat("es-AR");

export default function GloshiaPage() {
  const { active, latestCandidate } = gloshiaVisualSnapshot;
  const activeAccuracy =
    (active.evaluation.correctFilters + active.evaluation.correctAllows) /
    active.evaluation.binary;
  const activeFilterRecall =
    active.evaluation.correctFilters /
    (active.evaluation.correctFilters + active.evaluation.falseAllows);
  const activeAllowRecall =
    active.evaluation.correctAllows /
    (active.evaluation.correctAllows + active.evaluation.falseFilters);
  const accuracyDelta = latestCandidate.accuracy - activeAccuracy;
  const accuracyDeltaPoints = Math.abs(accuracyDelta * 100).toLocaleString("es-AR", {
    maximumFractionDigits: 1,
    minimumFractionDigits: 1,
  });

  return (
    <main className="page-shell">
      <section className="page-heading">
        <div>
          <p className="eyebrow">Motor visual local</p>
          <h1>GloshIA visual</h1>
          <p>
            Versión activa, calidad medida y experimentos del filtro de imágenes
            utilizado por DAG.
          </p>
        </div>
        <span className="inline-flex w-fit items-center gap-2 rounded-full bg-emerald-50 px-3 py-2 text-xs font-bold text-emerald-700">
          <CheckCircle2 className="h-4 w-4" />
          {active.status}
        </span>
      </section>

      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard
          icon={Gauge}
          label="Fiabilidad observada"
          value={percent.format(activeAccuracy)}
          detail={`${active.evaluation.correctFilters + active.evaluation.correctAllows} de ${active.evaluation.binary} decisiones binarias`}
          tone="sky"
        />
        <MetricCard
          icon={ShieldCheck}
          label="Detección de filtros"
          value={percent.format(activeFilterRecall)}
          detail={`${active.evaluation.correctFilters} de ${active.evaluation.correctFilters + active.evaluation.falseAllows} casos que debían filtrarse`}
          tone="emerald"
        />
        <MetricCard
          icon={Layers3}
          label="Respeto de permitidas"
          value={percent.format(activeAllowRecall)}
          detail={`${active.evaluation.correctAllows} de ${active.evaluation.correctAllows + active.evaluation.falseFilters} fotos permitidas`}
          tone="amber"
        />
        <MetricCard
          icon={Clock3}
          label="Latencia local"
          value={`${active.localLatencyMs.median.toFixed(1)} ms`}
          detail={`p95 ${active.localLatencyMs.p95.toFixed(1)} ms · referencia Mac`}
          tone="violet"
        />
      </section>

      <section className="grid gap-5 xl:grid-cols-[minmax(0,1.25fr)_minmax(320px,.75fr)]">
        <article className="panel">
          <div className="panel-header">
            <div className="flex items-center gap-3">
              <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-sky-50 text-sky-700">
                <BrainCircuit className="h-5 w-5" />
              </span>
              <div>
                <h2 className="section-title">Modelo activo</h2>
                <p className="muted mt-0.5">La versión que actualmente decide en DAG.</p>
              </div>
            </div>
            <span className="rounded-full bg-emerald-50 px-3 py-1.5 text-xs font-bold text-emerald-700">
              Activo en DAG
            </span>
          </div>

          <dl className="mt-5 grid gap-3 sm:grid-cols-2">
            <ModelDetail label="Versión" value={active.displayVersion} />
            <ModelDetail label="Política" value={active.policyVersion} />
            <ModelDetail label="Formato" value={active.format} />
            <ModelDetail label="Umbral base" value={active.threshold.toFixed(2)} />
            <ModelDetail label="Artefacto" value={active.artifactName} mono />
            <ModelDetail
              label="SHA-256"
              value={`${active.sha256.slice(0, 16)}…${active.sha256.slice(-12)}`}
              mono
              title={active.sha256}
            />
          </dl>

          <div className="mt-5 rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm leading-6 text-amber-900">
            <div className="flex items-start gap-3">
              <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0" />
              <p>
                <strong>La fiabilidad no es una garantía universal.</strong> El {percent.format(activeAccuracy)}
                corresponde a una prueba de estrés enriquecida con casos difíciles. Por eso se muestran
                también seguridad y sobre-filtrado por separado.
              </p>
            </div>
          </div>
        </article>

        <article className="panel">
          <div className="flex items-center gap-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-violet-50 text-violet-700">
              <Database className="h-5 w-5" />
            </span>
            <div>
              <h2 className="section-title">Cobertura de evidencia</h2>
              <p className="muted mt-0.5">Banco local sin abrir el examen final.</p>
            </div>
          </div>
          <div className="mt-5 grid gap-3">
            <EvidenceRow label="Corpus total" value={number.format(active.corpus.total)} />
            <EvidenceRow label="Evaluadas" value={number.format(active.corpus.evaluated)} />
            <EvidenceRow label="Revisión humana" value={`${active.evaluation.reviewed} fotos`} />
            <EvidenceRow label="Decisiones binarias" value={String(active.evaluation.binary)} />
            <EvidenceRow label="Dudosas excluidas" value={String(active.evaluation.doubts)} />
            <EvidenceRow label="Examen sellado" value={`${active.corpus.sealed} sin abrir`} />
          </div>
          <p className="mt-4 rounded-xl bg-slate-100 px-3 py-2 text-xs leading-5 text-slate-600">
            Este banco sirve para evaluación. No está autorizado automáticamente para entrenamiento
            o redistribución.
          </p>
        </article>
      </section>

      <section className="panel">
        <div className="panel-header">
          <div className="flex items-center gap-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-rose-50 text-rose-700">
              <FlaskConical className="h-5 w-5" />
            </span>
            <div>
              <h2 className="section-title">Último experimento</h2>
              <p className="muted mt-0.5">Candidato privado comparado sin reemplazar el modelo activo.</p>
            </div>
          </div>
          <span className="inline-flex items-center gap-1.5 rounded-full bg-rose-50 px-3 py-1.5 text-xs font-bold text-rose-700">
            <XCircle className="h-3.5 w-3.5" />
            {latestCandidate.status}
          </span>
        </div>

        <div className="mt-5 grid gap-4 md:grid-cols-2 xl:grid-cols-5">
          <ExperimentValue label="Entrenamiento" value={`${latestCandidate.trainingSamples} fotos`} />
          <ExperimentValue label="Nuevas dirigidas" value={`${latestCandidate.directedSamples} fotos`} />
          <ExperimentValue label="Exactitud" value={percent.format(latestCandidate.accuracy)} />
          <ExperimentValue label="Detección" value={percent.format(latestCandidate.filterRecall)} />
          <ExperimentValue label="Permitidas" value={percent.format(latestCandidate.allowRecall)} />
        </div>

        <div className="mt-5 grid gap-3 rounded-2xl border border-line bg-slate-50 p-4 lg:grid-cols-[1fr_auto] lg:items-center">
          <div>
            <p className="font-bold text-ink">Por qué no se publicó</p>
            <p className="mt-1 text-sm leading-6 text-slate-600">
              Mejoró la detección de 80% a 90%, pero la exactitud total cayó {accuracyDeltaPoints} puntos
              porcentuales y el sobre-filtrado subió de 40 a 42 fotos. Además, la versión INT8 no conservó
              todas las decisiones.
            </p>
          </div>
          <span className="inline-flex w-fit items-center gap-2 rounded-xl bg-white px-3 py-2 text-xs font-bold text-slate-600 shadow-sm">
            <Fingerprint className="h-4 w-4" />
            Modelo activo intacto
          </span>
        </div>
      </section>

      <p className="text-xs leading-5 text-slate-500">
        Corte de métricas: {gloshiaVisualSnapshot.snapshotDate} · Evidencia: {gloshiaVisualSnapshot.evidence}.
      </p>
    </main>
  );
}

const tones = {
  sky: "bg-sky-50 text-sky-700",
  emerald: "bg-emerald-50 text-emerald-700",
  amber: "bg-amber-50 text-amber-700",
  violet: "bg-violet-50 text-violet-700",
} as const;

function MetricCard({
  icon: Icon,
  label,
  value,
  detail,
  tone,
}: {
  icon: typeof Gauge;
  label: string;
  value: string;
  detail: string;
  tone: keyof typeof tones;
}) {
  return (
    <article className="metric-card">
      <div className="flex items-center justify-between gap-3">
        <p className="metric-label">{label}</p>
        <span className={`flex h-9 w-9 items-center justify-center rounded-xl ${tones[tone]}`}>
          <Icon className="h-4 w-4" />
        </span>
      </div>
      <p className="mt-3 text-3xl font-bold tracking-tight text-ink">{value}</p>
      <p className="mt-1 text-xs leading-5 text-slate-500">{detail}</p>
    </article>
  );
}

function ModelDetail({
  label,
  value,
  mono = false,
  title,
}: {
  label: string;
  value: string;
  mono?: boolean;
  title?: string;
}) {
  return (
    <div className="rounded-2xl border border-line bg-slate-50 p-4" title={title}>
      <dt className="text-xs font-bold uppercase tracking-[0.12em] text-slate-500">{label}</dt>
      <dd className={`mt-2 break-words text-sm font-bold text-ink ${mono ? "font-mono" : ""}`}>{value}</dd>
    </div>
  );
}

function EvidenceRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-4 border-b border-line pb-3 last:border-0 last:pb-0">
      <span className="text-sm text-slate-600">{label}</span>
      <strong className="text-sm text-ink">{value}</strong>
    </div>
  );
}

function ExperimentValue({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-2xl bg-slate-50 p-4">
      <p className="text-xs font-semibold text-slate-500">{label}</p>
      <p className="mt-2 text-xl font-bold text-ink">{value}</p>
    </div>
  );
}
