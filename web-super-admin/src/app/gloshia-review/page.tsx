"use client";

import { useCallback, useEffect, useState } from "react";
import styles from "./page.module.css";

const endpoint = "https://syeycayasyufedwoprea.supabase.co/functions/v1/gloshia-r3-review";
const names: Record<string, string> = {
  explicit_or_nudity: "Desnudez o contenido explícito",
  underwear_or_swimwear: "Ropa interior o traje de baño",
  transparent_clothing: "Ropa transparente",
  neckline_or_chest: "Escote o pecho",
  abdomen_visible: "Abdomen visible",
  shoulder_or_armpit: "Hombro o axila",
  elbow_uncovered: "Brazo por encima del codo",
  knee_uncovered: "Rodilla o pierna descubierta",
  tight_clothing: "Ropa ajustada",
  sexualized_pose: "Pose sugerente",
};

type ReviewItem = { sample_id: string; existing_positive_signals: string[]; signals_to_review: string[] };
type State = { total: number; reviewed: number; completed: boolean; item: ReviewItem | null };

export default function GloshiaReviewPage() {
  const [token, setToken] = useState("");
  const [state, setState] = useState<State | null>(null);
  const [choices, setChoices] = useState<Record<string, "positive" | "negative">>({});
  const [message, setMessage] = useState("");
  const [saving, setSaving] = useState(false);

  const request = useCallback(async (activeToken: string, action: string, init?: RequestInit) => {
    const response = await fetch(`${endpoint}?token=${encodeURIComponent(activeToken)}&api=${action}`, { cache: "no-store", ...init });
    const data = await response.json();
    if (!response.ok) throw new Error(data.error ?? "No se pudo conectar");
    return data as State;
  }, []);

  useEffect(() => {
    const activeToken = new URL(window.location.href).searchParams.get("token") ?? "";
    setToken(activeToken);
    request(activeToken, "state").then(setState).catch((error) => setMessage(error.message));
  }, [request]);

  const save = async () => {
    if (!state?.item) return;
    const missing = state.item.signals_to_review.filter((signal) => !choices[signal]);
    if (missing.length) { setMessage(`Falta decidir ${missing.length} motivo${missing.length === 1 ? "" : "s"}.`); return; }
    setSaving(true); setMessage("");
    try {
      const next = await request(token, "review", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ sample_id: state.item.sample_id, labels: choices }) });
      setState(next); setChoices({}); window.scrollTo({ top: 0, behavior: "smooth" });
    } catch (error) { setMessage(error instanceof Error ? error.message : "No se pudo guardar"); }
    finally { setSaving(false); }
  };

  const item = state?.item;
  return <main className={styles.page}><div className={styles.shell}>
    <header className={styles.top}><div className={styles.brand}>GloshIA Visual</div><div className={styles.progress}>{state ? `${state.reviewed} de ${state.total}` : "Cargando…"}</div></header>
    <div className={styles.bar}><div className={styles.fill} style={{ width: state?.total ? `${state.reviewed / state.total * 100}%` : "0%" }} /></div>
    <section className={styles.card}>
      {state?.completed ? <div className={styles.done}><h1>¡Listo!</h1><p className={styles.sub}>Las {state.total} revisiones quedaron guardadas.</p></div> : item ? <>
        <div className={styles.photo}><img src={`${endpoint}?token=${encodeURIComponent(token)}&image=${encodeURIComponent(item.sample_id)}`} alt="Foto para revisar" /></div>
        <div className={styles.body}><p className={styles.hint}>Marcá todos los motivos: rojo significa sí; verde significa no.</p>
          {item.existing_positive_signals.length > 0 && <div className={styles.fixed}>Ya confirmado: {item.existing_positive_signals.map((signal) => names[signal] ?? signal).join(", ")}</div>}
          <div className={styles.signals}>{item.signals_to_review.map((signal) => <button key={signal} className={`${styles.signal} ${choices[signal] === "positive" ? styles.yes : choices[signal] === "negative" ? styles.no : ""}`} onClick={() => setChoices((current) => ({ ...current, [signal]: current[signal] === "positive" ? "negative" : "positive" }))}>{names[signal] ?? signal}</button>)}</div>
          <button className={styles.quick} onClick={() => setChoices(Object.fromEntries(item.signals_to_review.map((signal) => [signal, "negative"]))) }>Ningún motivo adicional</button>
          <button className={styles.save} disabled={saving} onClick={save}>{saving ? "Guardando…" : "Guardar y seguir"}</button><div className={styles.message}>{message}</div>
        </div></> : <div className={styles.done}><h1>{message ? "No se pudo abrir" : "Cargando…"}</h1>{message && <p className={styles.sub}>{message}</p>}</div>}
    </section>
  </div></main>;
}
