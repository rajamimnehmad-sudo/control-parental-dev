const SWIPE_RATIO = 0.20;
const SWIPE_MINIMUM = 72;

const reasonOptions = [
  ["neckline_or_chest", "Escote o pecho"],
  ["shoulder_or_armpit", "Hombro o axila"],
  ["elbow_uncovered", "Codo descubierto"],
  ["abdomen_visible", "Abdomen visible"],
  ["knee_uncovered", "Rodilla descubierta"],
  ["tight_clothing", "Ropa ajustada"],
  ["transparent_clothing", "Ropa transparente"],
  ["underwear_or_swimwear", "Ropa interior o de baño"],
  ["explicit_or_nudity", "Desnudez o explícita"],
  ["sexualized_pose", "Pose sexualizada"],
  ["other", "Otro"],
  ["uncertain_reason", "Motivo incierto"],
];

const actionLabels = {
  allow: "Permitida",
  filter: "Filtrada",
  doubt: "Dudosa",
};

const categoryLabels = {
  boundary_current: "Borde actual",
  safe_hard: "Permitida difícil",
  collage_group: "Grupo o collage",
  children_normal: "Menores en contexto normal",
  sensitive_control: "Control sensible",
};

const elements = {
  action: document.querySelector("#action"),
  human: document.querySelector("#human"),
  relation: document.querySelector("#relation"),
  reviewQueue: document.querySelector("#review-queue"),
  origin: document.querySelector("#origin"),
  allowButton: document.querySelector("#allow-button"),
  allowStamp: document.querySelector("#allow-stamp"),
  card: document.querySelector("#swipe-card"),
  cardPosition: document.querySelector("#card-position"),
  category: document.querySelector("#category"),
  deck: document.querySelector("#deck"),
  doubtButton: document.querySelector("#doubt-button"),
  empty: document.querySelector("#empty-state"),
  export: document.querySelector("#export"),
  feedback: document.querySelector("#feedback"),
  feedbackDetail: document.querySelector("#feedback-detail"),
  feedbackIcon: document.querySelector("#feedback-icon"),
  feedbackTitle: document.querySelector("#feedback-title"),
  filterButton: document.querySelector("#filter-button"),
  filterStamp: document.querySelector("#filter-stamp"),
  image: document.querySelector("#review-image"),
  modelAction: document.querySelector("#model-action"),
  modelCategory: document.querySelector("#model-category"),
  modelScore: document.querySelector("#model-score"),
  progressBar: document.querySelector("#progress-bar"),
  progressLabel: document.querySelector("#progress-label"),
  progressPercent: document.querySelector("#progress-percent"),
  reasonList: document.querySelector("#reason-list"),
  reasonPicker: document.querySelector("#reason-picker"),
  reload: document.querySelector("#reload"),
  previous: document.querySelector("#previous"),
  next: document.querySelector("#next"),
  importButton: document.querySelector("#import"),
  importFile: document.querySelector("#import-file"),
  restart: document.querySelector("#restart"),
  revealedResult: document.querySelector("#revealed-result"),
  sampleId: document.querySelector("#sample-id"),
  scope: document.querySelector("#scope"),
  sealed: document.querySelector("#sealed"),
  skeleton: document.querySelector("#photo-skeleton"),
  undo: document.querySelector("#undo"),
};

let items = [];
let currentIndex = 0;
let status = {
  reviewed_total: 0,
  sealed_unlocked: false,
};
let busy = false;
let lastDecision = null;
let pointer = null;

function actionLabel(action) {
  return actionLabels[action] ?? "Sin análisis";
}

function categoryLabel(category) {
  return categoryLabels[category] ?? category ?? "";
}

function wait(milliseconds) {
  return new Promise((resolve) => window.setTimeout(resolve, milliseconds));
}

async function fetchJson(url, options) {
  const response = await fetch(url, options);
  const body = await response.text();
  if (!response.ok) {
    throw new Error("No se pudo guardar. Probá nuevamente.");
  }
  if (!body.trim()) {
    const error = new Error("El servidor no confirmó la operación.");
    error.code = "empty_json_response";
    throw error;
  }
  try {
    return JSON.parse(body);
  } catch {
    throw new Error("El servidor devolvió una respuesta inválida.");
  }
}

async function saveReview(payload) {
  const options = {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Origin": window.location.origin,
    },
    body: JSON.stringify(payload),
  };
  try {
    return await fetchJson("/api/review", options);
  } catch (error) {
    if (error.code !== "empty_json_response") throw error;
    await wait(150);
    return fetchJson("/api/review", options);
  }
}

function renderReasonOptions() {
  const fragment = document.createDocumentFragment();
  for (const [value, label] of reasonOptions) {
    const wrapper = document.createElement("label");
    const input = document.createElement("input");
    input.type = "checkbox";
    input.value = value;
    wrapper.append(input, ` ${label}`);
    fragment.append(wrapper);
  }
  elements.reasonList.replaceChildren(fragment);
}

function selectedReasons() {
  return [...elements.reasonList.querySelectorAll("input:checked")].map(
    (input) => input.value,
  );
}

function setSelectedReasons(values = []) {
  const selected = new Set(values);
  for (const input of elements.reasonList.querySelectorAll("input")) {
    input.checked = selected.has(input.value);
  }
}

function updateProgress() {
  const target = status.review_target ?? status.corpus_rows ?? 0;
  const completed = Math.min(status.reviewed_total ?? 0, target);
  const percentage = target ? Math.round((completed / target) * 100) : 0;
  elements.progressLabel.textContent = `${completed} de ${target} revisadas`;
  elements.progressPercent.textContent = `${percentage}%`;
  elements.progressBar.style.width = `${percentage}%`;
  elements.sealed.textContent = status.sealed_unlocked ? "Final abierto" : "Final sellado";
}

function populateSelect(select, values, emptyLabel) {
  const current = select.value;
  select.replaceChildren(new Option(emptyLabel, ""));
  for (const value of values ?? []) {
    select.append(new Option(categoryLabel(value), value));
  }
  if ([...select.options].some((option) => option.value === current)) {
    select.value = current;
  }
}

function setDecisionEnabled(enabled) {
  elements.allowButton.disabled = !enabled;
  elements.filterButton.disabled = !enabled;
  elements.doubtButton.disabled = !enabled;
}

function clearCardMotion() {
  elements.card.classList.remove("dragging", "exit-left", "exit-right");
  elements.card.style.removeProperty("transform");
  elements.allowStamp.style.opacity = "0";
  elements.filterStamp.style.opacity = "0";
}

function showFeedback(kind, title, detail) {
  elements.feedback.className = `feedback ${kind}`;
  elements.feedback.hidden = false;
  elements.feedbackTitle.textContent = title;
  elements.feedbackDetail.textContent = detail;
  elements.feedbackIcon.textContent = kind === "match" ? "✓" : kind === "mismatch" ? "≠" : "?";
}

function hideFeedback() {
  elements.feedback.hidden = true;
  elements.feedback.className = "feedback";
}

function revealModel(item) {
  const prediction = item.model_prediction;
  if (!prediction) {
    elements.revealedResult.hidden = true;
    return;
  }
  elements.modelAction.textContent = `GloshIA: ${actionLabel(prediction.action)}`;
  elements.modelScore.textContent =
    `Puntaje máximo: ${Number(prediction.maximum_probability ?? 0).toFixed(3)}`;
  elements.modelCategory.textContent = categoryLabel(item.category);
  elements.revealedResult.hidden = false;
}

function preloadNextImage() {
  const next = items.slice(currentIndex + 1).find((item) => !item.human_decision);
  if (!next) return;
  const preload = new Image();
  preload.src = next.image_url;
}

function renderCurrent() {
  clearCardMotion();
  elements.reasonPicker.open = false;
  elements.skeleton.hidden = false;
  elements.image.classList.remove("loaded");
  elements.image.removeAttribute("src");

  const item = items[currentIndex];
  if (!item || ((status.reviewed_total ?? 0) >= (status.review_target ?? 0) && elements.scope.value === "queue")) {
    elements.card.hidden = true;
    elements.empty.hidden = false;
    elements.empty.querySelector("h2").textContent = "Ronda terminada";
    elements.empty.querySelector("p").textContent =
      "Las decisiones quedaron guardadas en esta Mac.";
    setDecisionEnabled(false);
    return;
  }

  elements.empty.hidden = true;
  elements.card.hidden = false;
  elements.cardPosition.textContent = `Foto ${currentIndex + 1} de ${items.length}`;
  elements.sampleId.textContent = item.sample_id;
  setSelectedReasons(item.human_decision?.reasons);
  revealModel(item);

  const pending = !item.human_decision;
  setDecisionEnabled(pending && !busy);
  elements.reasonPicker.hidden = !pending;
  elements.image.src = item.image_url;
  preloadNextImage();
}

function nextPendingIndex(afterIndex) {
  for (let index = afterIndex + 1; index < items.length; index += 1) {
    if (!items[index].human_decision) return index;
  }
  for (let index = 0; index <= afterIndex; index += 1) {
    if (!items[index].human_decision) return index;
  }
  return -1;
}

async function decide(action, direction) {
  const item = items[currentIndex];
  if (busy || !item || item.human_decision) return;
  busy = true;
  setDecisionEnabled(false);
  elements.card.classList.add(direction === "right" ? "exit-right" : "exit-left");

  try {
    const payload = await saveReview({
      sample_id: item.sample_id,
      action,
      reasons: action === "allow" ? [] : selectedReasons(),
    });
    const decidedIndex = currentIndex;
    item.human_decision = payload.review;
    item.model_prediction = payload.model_prediction;
    item.category = payload.category;
    item.split = payload.split;
    status.reviewed_total = (status.reviewed_total ?? 0) + 1;
    lastDecision = {
      index: decidedIndex,
      sample_id: item.sample_id,
    };
    elements.undo.disabled = false;

    if (payload.matched_model === true) {
      showFeedback(
        "match",
        "Coincidiste con GloshIA",
        `Ambos marcaron: ${actionLabel(action)}.`,
      );
    } else if (payload.matched_model === false) {
      showFeedback(
        "mismatch",
        "No coincidieron",
        `Vos: ${actionLabel(action)} · GloshIA: ${actionLabel(
          payload.model_prediction?.action,
        )}.`,
      );
    } else {
      showFeedback(
        "doubt",
        "Quedó marcada como dudosa",
        `GloshIA había elegido: ${actionLabel(payload.model_prediction?.action)}.`,
      );
    }

    updateProgress();
    await wait(240);
    currentIndex = nextPendingIndex(decidedIndex);
    renderCurrent();
  } catch (error) {
    clearCardMotion();
    showFeedback("mismatch", "No se guardó", error.message);
    setDecisionEnabled(true);
  } finally {
    busy = false;
    if (items[currentIndex] && !items[currentIndex].human_decision) {
      setDecisionEnabled(true);
    }
  }
}

async function undoLastDecision() {
  if (busy || !lastDecision) return;
  busy = true;
  elements.undo.disabled = true;
  try {
    await fetchJson("/api/review", {
      method: "DELETE",
      headers: {
        "Content-Type": "application/json",
        "Origin": window.location.origin,
      },
      body: JSON.stringify({sample_id: lastDecision.sample_id}),
    });
    const item = items[lastDecision.index];
    item.human_decision = null;
    item.model_prediction = null;
    item.category = null;
    item.split = null;
    status.reviewed_total = Math.max(0, (status.reviewed_total ?? 0) - 1);
    currentIndex = lastDecision.index;
    lastDecision = null;
    hideFeedback();
    updateProgress();
    renderCurrent();
  } catch (error) {
    showFeedback("mismatch", "No se pudo deshacer", error.message);
    elements.undo.disabled = false;
  } finally {
    busy = false;
    if (items[currentIndex] && !items[currentIndex].human_decision) {
      setDecisionEnabled(true);
    }
  }
}

function resetDrag() {
  pointer = null;
  elements.card.classList.remove("dragging");
  elements.card.style.removeProperty("transform");
  elements.allowStamp.style.opacity = "0";
  elements.filterStamp.style.opacity = "0";
}

function pointerDown(event) {
  if (
    busy ||
    items[currentIndex]?.human_decision ||
    event.target.closest("button, input, label, summary, select")
  ) {
    return;
  }
  pointer = {
    id: event.pointerId,
    startX: event.clientX,
    startY: event.clientY,
    deltaX: 0,
  };
  elements.card.setPointerCapture(event.pointerId);
  elements.card.classList.add("dragging");
}

function pointerMove(event) {
  if (!pointer || pointer.id !== event.pointerId) return;
  const deltaX = event.clientX - pointer.startX;
  const deltaY = event.clientY - pointer.startY;
  if (Math.abs(deltaY) > Math.abs(deltaX) && Math.abs(deltaY) > 16) {
    resetDrag();
    return;
  }
  pointer.deltaX = deltaX;
  const rotation = Math.max(-9, Math.min(9, deltaX / 24));
  const strength = Math.min(1, Math.abs(deltaX) / 130);
  elements.card.style.transform = `translate3d(${deltaX}px, 0, 0) rotate(${rotation}deg)`;
  elements.allowStamp.style.opacity = deltaX > 0 ? String(strength) : "0";
  elements.filterStamp.style.opacity = deltaX < 0 ? String(strength) : "0";
}

function pointerEnd(event) {
  if (!pointer || pointer.id !== event.pointerId) return;
  const deltaX = pointer.deltaX;
  const threshold = Math.max(
    SWIPE_MINIMUM,
    elements.card.getBoundingClientRect().width * SWIPE_RATIO,
  );
  resetDrag();
  if (Math.abs(deltaX) < threshold) return;
  if (deltaX > 0) {
    decide("allow", "right");
  } else {
    decide("filter", "left");
  }
}

async function loadStatus() {
  status = await fetchJson("/api/status");
  populateSelect(elements.category, status.categories, "Todos y pendientes");
  populateSelect(elements.origin, status.origins, "Todos");
  updateProgress();
}

async function loadItems() {
  if ((status.reviewed_total ?? 0) >= (status.review_target ?? 0) && elements.scope.value === "queue") {
    items = [];
    renderCurrent();
    return;
  }
  const parameters = new URLSearchParams({
    scope: elements.scope.value,
    action: elements.action.value,
    category: elements.category.value,
    human: elements.human.value,
    relation: elements.relation.value,
    review_queue: elements.reviewQueue.value,
    origin: elements.origin.value,
    limit: "600",
  });
  const payload = await fetchJson(`/api/items?${parameters}`);
  items = payload.items;
  currentIndex = Math.max(
    0,
    items.findIndex((item) => !item.human_decision),
  );
  renderCurrent();
}

function moveCurrent(delta) {
  if (!items.length) return;
  currentIndex = Math.max(0, Math.min(items.length - 1, currentIndex + delta));
  hideFeedback();
  renderCurrent();
}

async function restartReview() {
  if (!window.confirm("Se hará una copia local y se borrarán las decisiones actuales. ¿Continuar?")) return;
  await fetchJson("/api/restart", {
    method: "POST",
    headers: {"Content-Type": "application/json", "Origin": window.location.origin},
    body: "{}",
  });
  lastDecision = null;
  await reload();
}

async function importReview(file) {
  const payload = JSON.parse(await file.text());
  await fetchJson("/api/import", {
    method: "POST",
    headers: {"Content-Type": "application/json", "Origin": window.location.origin},
    body: JSON.stringify(payload),
  });
  lastDecision = null;
  await reload();
}

async function reload() {
  busy = true;
  setDecisionEnabled(false);
  try {
    await loadStatus();
    await loadItems();
  } catch (error) {
    elements.card.hidden = true;
    elements.empty.hidden = false;
    elements.empty.querySelector("h2").textContent = "No se pudo abrir la ronda";
    elements.empty.querySelector("p").textContent = error.message;
  } finally {
    busy = false;
    if (items[currentIndex] && !items[currentIndex].human_decision) {
      setDecisionEnabled(true);
    }
  }
}

elements.image.addEventListener("load", () => {
  elements.skeleton.hidden = true;
  elements.image.classList.add("loaded");
});
elements.image.addEventListener("error", () => {
  elements.skeleton.hidden = true;
  showFeedback("mismatch", "Imagen no disponible", "Actualizá la ronda para reintentar.");
  setDecisionEnabled(false);
});

elements.card.addEventListener("pointerdown", pointerDown);
elements.card.addEventListener("pointermove", pointerMove);
elements.card.addEventListener("pointerup", pointerEnd);
elements.card.addEventListener("pointercancel", resetDrag);

elements.allowButton.addEventListener("click", () => decide("allow", "right"));
elements.filterButton.addEventListener("click", () => decide("filter", "left"));
elements.doubtButton.addEventListener("click", () => decide("doubt", "left"));
elements.undo.addEventListener("click", undoLastDecision);
elements.previous.addEventListener("click", () => moveCurrent(-1));
elements.next.addEventListener("click", () => moveCurrent(1));
elements.reload.addEventListener("click", reload);
elements.restart.addEventListener("click", () => restartReview().catch((error) => showFeedback("mismatch", "No se pudo reiniciar", error.message)));
elements.importButton.addEventListener("click", () => elements.importFile.click());
elements.importFile.addEventListener("change", () => {
  const [file] = elements.importFile.files ?? [];
  if (file) importReview(file).catch((error) => showFeedback("mismatch", "No se pudo importar", error.message));
  elements.importFile.value = "";
});
elements.export.addEventListener("click", () => {
  window.location.href = "/api/export";
});

for (const select of [elements.scope, elements.reviewQueue, elements.action, elements.relation, elements.human, elements.category, elements.origin]) {
  select.addEventListener("change", reload);
}

document.addEventListener("keydown", (event) => {
  if (
    event.repeat ||
    event.target.closest("input, select, button, summary") ||
    busy
  ) {
    return;
  }
  if (event.key === "ArrowRight") {
    event.preventDefault();
    decide("allow", "right");
  } else if (event.key === "ArrowLeft") {
    event.preventDefault();
    decide("filter", "left");
  } else if (event.key === "ArrowUp") {
    event.preventDefault();
    decide("doubt", "left");
  }
});

renderReasonOptions();
await reload();
