import { BrainCircuit, ClipboardCheck, History, Images, ShieldCheck } from "lucide-react";
import {
  ActivateDagCalibrationButton,
  ClearDagCalibrationButton,
  ManualDagCalibrationForm,
} from "@/components/DagCalibrationActions";
import { DagCalibrationReviewForm } from "@/components/DagCalibrationReviewForm";
import { EmptyState } from "@/components/EmptyState";
import { getDagCalibrationBundle } from "@/lib/data";
import { formatDate } from "@/lib/utils";
import { dagCalibrationExplanation } from "@/lib/dag-calibration-explanation";

export const dynamic = "force-dynamic";

export default async function DagCalibrationPage() {
  const { pending, reviewed, versions, audit, models } = await getDagCalibrationBundle();
  const allowCount = reviewed.filter((item) => item.review_decision === "allow").length;
  const blockCount = reviewed.filter((item) => item.review_decision === "block").length;
  const activeVersion = versions.find((version) => version.status === "active");
  const availableVersion = versions.find((version) => version.status === "candidate");
  const lastEvaluatedCount = Math.max(0, ...versions.map((version) => version.labeled_item_count));
  const labelsUntilEvaluation = reviewed.length < 40
    ? 40 - reviewed.length
    : Math.max(0, 10 - (reviewed.length - lastEvaluatedCount));
  const displayedThresholds = activeVersion?.thresholds ?? {};
  return (
    <main className="mx-auto grid max-w-7xl gap-5 px-4 py-5 lg:px-6">
      <section className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <div className="flex items-center gap-2"><BrainCircuit className="h-6 w-6 text-accent" /><h1 className="text-2xl font-semibold text-ink">Calibración DAG</h1></div>
          <p className="mt-1 max-w-3xl text-sm text-slate-500">Revisá casos dudosos, registrá tu criterio y creá calibraciones medibles y reversibles. Calibrar ajusta umbrales; entrenar un modelo nuevo será una etapa separada.</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <ClearDagCalibrationButton disabled={pending.length + reviewed.length === 0} />
        </div>
      </section>

      <section className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Metric label="Pendientes" value={pending.length} icon={Images} />
        <Metric label="Revisadas" value={reviewed.length} icon={ClipboardCheck} />
        <Metric label="Permitidas" value={allowCount} icon={ShieldCheck} />
        <Metric label="Difuminadas" value={blockCount} icon={BrainCircuit} />
      </section>

      <section className="rounded-md border border-cyan-100 bg-cyan-50 p-4 text-sm text-cyan-950">
        <p className="font-bold">
          {availableVersion
            ? `Calibración #${availableVersion.version_number} disponible`
            : activeVersion
              ? `Calibración activa #${activeVersion.version_number}`
              : "Reuniendo la primera calibración"}
        </p>
        <p className="mt-1">
          {availableVersion
            ? "La propuesta ya fue evaluada y espera activación manual. Nada cambia en los teléfonos hasta que la actives."
            : labelsUntilEvaluation > 0
              ? `Faltan ${labelsUntilEvaluation} etiquetas para la próxima evaluación automática.`
              : "La próxima etiqueta completará la evaluación automática."}
        </p>
      </section>

      <section className="grid gap-3">
        <div>
          <h2 className="text-lg font-semibold text-ink">Umbrales actuales</h2>
          <p className="text-sm text-slate-500">Valores activos en los teléfonos y propuesta disponible para comparación.</p>
        </div>
        <ThresholdGrid active={displayedThresholds} proposed={availableVersion?.thresholds} />
      </section>

      <section className="grid gap-3 rounded-md border border-line bg-white p-4 shadow-soft">
        <div>
          <h2 className="text-lg font-semibold text-ink">Ajuste manual avanzado</h2>
          <p className="text-sm text-slate-500">Podés preparar otros valores dentro de límites seguros. Siempre se guardan como propuesta y requieren activación manual.</p>
        </div>
        <ManualDagCalibrationForm
          modelVersion={activeVersion?.model_version ?? models.find((model) => model.status === "active")?.model_version ?? "marqo-nsfw-vit-tiny-384-2"}
          thresholds={displayedThresholds}
        />
      </section>

      <section className="grid gap-3">
        <h2 className="text-lg font-semibold text-ink">Fotos para revisar</h2>
        {pending.length === 0 ? <EmptyState title="No hay imágenes dudosas pendientes" body="DAG enviará únicamente casos dentro de la franja real de incertidumbre." /> : (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {pending.map((review) => (
              <article key={review.review_id} className="overflow-hidden rounded-md border border-line bg-white shadow-soft">
                <div className="aspect-[4/3] bg-slate-100">
                  {review.image_url ? (
                    // Signed private thumbnails are intentionally served without an optimization proxy.
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={review.image_url} alt="Caso dudoso para Calibración DAG" className="h-full w-full object-contain" />
                  ) : <div className="grid h-full place-items-center text-sm text-slate-500">Miniatura no disponible</div>}
                </div>
                <div className="grid gap-3 p-4">
                  <div>
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="font-bold text-ink">{review.community_name}</p>
                      {review.submission_source === "manual_dag" ? <span className="rounded-full bg-red-50 px-2 py-1 text-[11px] font-bold text-red-700">Marcada desde DAG</span> : null}
                      {review.submission_source === "manual_dag_false_positive" ? <span className="rounded-full bg-cyan-50 px-2 py-1 text-[11px] font-bold text-cyan-700">Revisar difuminado</span> : null}
                    </div>
                    <p className="text-xs text-slate-500">{review.device_name} · {formatDate(review.created_at)}</p>
                    {review.submission_source === "manual_dag" ? <p className="mt-1 text-xs text-slate-600">La X indicó “prohibida”. Confirmá la etiqueta para incorporarla a una calibración.</p> : null}
                    {review.submission_source === "manual_dag_false_positive" ? <p className="mt-1 text-xs text-slate-600">La R indicó que DAG habría difuminado esta foto y podría ser un falso positivo. Decidí si debe permitirse o bloquearse y registrá el motivo.</p> : null}
                  </div>
                  <div className="rounded-md border border-cyan-100 bg-cyan-50 p-3 text-sm text-cyan-950">
                    <p className="font-semibold">Por qué llegó a revisión</p>
                    <p className="mt-1">{dagCalibrationExplanation(review)}</p>
                    <p className="mt-1 text-xs text-cyan-800">Clasificada con Calibración #{review.classification_calibration_version || "base"}.</p>
                  </div>
                  <details className="text-xs text-slate-600"><summary className="cursor-pointer font-semibold">Ver puntajes del modelo</summary><pre className="mt-2 overflow-x-auto rounded bg-slate-50 p-2">{JSON.stringify(review.scores, null, 2)}</pre></details>
                  <DagCalibrationReviewForm reviewId={review.review_id} />
                </div>
              </article>
            ))}
          </div>
        )}
      </section>

      <section className="grid gap-3">
        <h2 className="text-lg font-semibold text-ink">Modelos de inteligencia artificial</h2>
        <p className="text-sm text-slate-500">El modelo reconoce patrones visuales; la calibración adapta tu criterio. Un modelo nuevo solamente podrá activarse cuando tenga artefacto firmado, métricas de validación y compatibilidad Android.</p>
        {models.map((model) => <article key={model.model_id} className="rounded-md border border-line bg-white p-4 shadow-soft"><div className="flex items-start justify-between gap-3"><div><p className="font-bold text-ink">{model.model_version}</p><p className="mt-1 text-sm text-slate-500">{model.notes}</p></div><span className={model.status === "active" ? "rounded-full bg-teal-50 px-3 py-1 text-xs font-bold text-teal-700" : "rounded-full bg-slate-100 px-3 py-1 text-xs font-bold text-slate-600"}>{model.status === "active" ? "Activo" : model.status}</span></div><p className="mt-3 text-xs text-slate-500">Ejemplos propios registrados: {model.training_example_count}. El registro no permite activar modelos sin firma y validación.</p></article>)}
      </section>

      <section className="grid gap-3">
        <h2 className="text-lg font-semibold text-ink">Versiones de calibración</h2>
        {versions.length === 0 ? <EmptyState title="Todavía no hay calibraciones" body="Etiquetá suficientes fotos y presioná Calibrar DAG." /> : versions.map((version) => (
          <article key={version.calibration_id} className="rounded-md border border-line bg-white p-4 shadow-soft">
            <div className="flex items-start justify-between gap-3"><div><p className="font-bold text-ink">Calibración #{version.version_number}</p><p className="text-xs text-slate-500">{version.labeled_item_count} ejemplos · modelo {version.model_version} · {formatDate(version.created_at)}</p></div><ActivateDagCalibrationButton calibrationId={version.calibration_id} active={version.status === "active"} /></div>
            <p className="mt-3 text-sm text-slate-600">{version.explanation}</p>
            <details className="mt-3 text-xs text-slate-600"><summary className="cursor-pointer font-semibold">Umbrales y métricas</summary><pre className="mt-2 overflow-x-auto rounded bg-slate-50 p-3">{JSON.stringify({ thresholds: version.thresholds, metrics: version.metrics }, null, 2)}</pre></details>
          </article>
        ))}
      </section>

      <section className="grid gap-3"><div className="flex items-center gap-2"><History className="h-5 w-5 text-accent" /><h2 className="text-lg font-semibold text-ink">Registro</h2></div>{audit.length === 0 ? <p className="text-sm text-slate-500">Sin acciones registradas.</p> : <div className="overflow-x-auto rounded-md border border-line bg-white"><table className="min-w-full"><thead className="bg-slate-50 text-xs uppercase text-slate-500"><tr><th className="table-cell">Fecha</th><th className="table-cell">Acción</th><th className="table-cell">Detalle</th></tr></thead><tbody className="divide-y divide-line">{audit.map((entry) => <tr key={entry.audit_id}><td className="table-cell text-slate-600">{formatDate(entry.created_at)}</td><td className="table-cell font-semibold text-ink">{actionLabel(entry.action)}</td><td className="table-cell text-xs text-slate-500">{JSON.stringify(entry.details)}</td></tr>)}</tbody></table></div>}</section>
    </main>
  );
}

function Metric({ label, value, icon: Icon }: { label: string; value: number; icon: typeof Images }) { return <div className="rounded-md border border-line bg-white p-4 shadow-soft"><div className="flex items-center justify-between"><p className="text-xs font-semibold text-slate-500">{label}</p><Icon className="h-4 w-4 text-accent" /></div><p className="mt-2 text-2xl font-bold text-ink">{value}</p></div>; }
function actionLabel(action: string) { return ({ review_labeled: "Foto etiquetada", reviews_cleared: "Fotos borradas", manual_image_reported: "Foto marcada desde DAG", calibration_created: "Calibración creada", calibration_activated: "Calibración activada", calibration_rollback: "Cambio de calibración", model_registered: "Modelo registrado" } as Record<string, string>)[action] ?? action; }

const thresholdLabels: Array<[string, string]> = [
  ["professional_block", "Contenido NSFW"],
  ["female_face", "Contexto femenino"],
  ["male_face", "Contexto masculino"],
  ["male_breast_exposed", "Torso masculino"],
  ["belly_exposed", "Abdomen expuesto"],
  ["armpits_exposed", "Hombros"],
  ["sleeves_above_elbow", "Mangas"],
  ["hem_above_knee", "Largo sobre la rodilla"],
  ["explicit_region", "Riesgo visual general"],
];

function ThresholdGrid({
  active,
  proposed,
}: {
  active: Record<string, number>;
  proposed?: Record<string, number>;
}) {
  return (
    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
      {thresholdLabels.map(([key, label]) => (
        <article key={key} className="rounded-md border border-line bg-white p-4 shadow-soft">
          <p className="text-sm font-semibold text-ink">{label}</p>
          <div className="mt-2 flex items-end justify-between gap-3">
            <div><p className="text-xs text-slate-500">Activo</p><p className="text-xl font-bold text-ink">{formatThreshold(active[key])}</p></div>
            <div className="text-right"><p className="text-xs text-slate-500">Propuesto</p><p className="text-xl font-bold text-accent">{formatThreshold(proposed?.[key])}</p></div>
          </div>
        </article>
      ))}
    </div>
  );
}

function formatThreshold(value: number | undefined) {
  return typeof value === "number" ? value.toFixed(2) : "—";
}
