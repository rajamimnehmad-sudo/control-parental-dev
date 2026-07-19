import type { DagCalibrationReview } from "@/lib/types";

const signalDescriptions: Record<string, string> = {
  professional_content: "contenido visual posiblemente inapropiado",
  legacy_content: "contenido sensible detectado por el clasificador de respaldo",
  female_breast_covered: "una posible zona de pecho o escote",
  female_genitalia_covered: "una posible zona íntima cubierta",
  buttocks_covered: "una posible zona de glúteos",
  armpits_exposed: "hombros o parte superior de los brazos expuestos",
  belly_exposed: "vientre expuesto",
  explicit_region: "una posible zona explícita",
  sleeves_above_elbow: "una posible manga por encima del codo",
  hem_above_knee: "una posible prenda por encima de la rodilla",
  uncertain_content: "señales cercanas al límite de decisión",
};

export function dagCalibrationExplanation(review: DagCalibrationReview): string {
  const signals = review.signals.length > 0 ? review.signals : inferSignals(review);
  const descriptions = signals.map((signal) => signalDescriptions[signal]).filter(Boolean).slice(0, 2);
  if (descriptions.length === 0) {
    return review.initial_decision === "blocked"
      ? "DAG la difuminó por una señal visual que requiere confirmación."
      : "DAG encontró señales dudosas y necesita tu criterio antes de ajustar la protección.";
  }
  const prefix = review.initial_decision === "blocked" ? "DAG la difuminó porque detectó " : "DAG pidió revisión porque detectó ";
  return `${prefix}${joinDescriptions(descriptions)}.`;
}

function inferSignals(review: DagCalibrationReview): string[] {
  const scores = review.scores;
  return Object.entries(scores)
    .filter(([key, value]) => key !== "female_face" && Number.isFinite(value) && value >= fallbackThreshold(key))
    .sort((left, right) => right[1] - left[1])
    .map(([key]) => key === "professional" ? "professional_content" : key === "legacy" ? "legacy_content" : key);
}

function fallbackThreshold(key: string): number {
  if (key === "professional" || key === "legacy") return 0.15;
  if (key === "sleeves_above_elbow" || key === "hem_above_knee") return 0.72;
  return 0.18;
}

function joinDescriptions(values: string[]): string {
  return values.length === 1 ? values[0] : `${values[0]} y ${values[1]}`;
}
