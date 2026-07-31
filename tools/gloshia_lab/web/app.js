const reasons = [
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
  ["safe_male_or_child", "Hombre o menor permitido"],
  ["safe_product_or_logo", "Producto o logo permitido"],
  ["other", "Otro"],
];

const esc = (value) => String(value ?? "").replace(/[&<>"']/g, (character) => ({
  "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
}[character]));
const actionLabel = (action) => ({
  allow: "Permitida",
  filter: "Filtrada",
  doubt: "Dudosa",
}[action] ?? "Sin análisis");
const categoryLabel = (category) => ({
  boundary_current: "Borde actual",
  safe_hard: "Permitida difícil",
  collage_group: "Grupo o collage",
  children_normal: "Menores en contexto normal",
  sensitive_control: "Control sensible",
}[category] ?? category);

async function loadStatus() {
  const status = await (await fetch("/api/status")).json();
  const cells = [
    ["Corpus evaluable", status.corpus_rows],
    ["Analizadas", status.valid_predictions],
    ["Revisadas", status.reviewed_reference],
    ["Cola sugerida", status.queue],
    ["Examen final", status.sealed_unlocked ? "ABIERTO" : "SELLADO"],
  ];
  document.querySelector("#summary").innerHTML = cells.map(([label, value]) =>
    `<div class="metric"><small>${esc(label)}</small><strong>${esc(value)}</strong></div>`
  ).join("");
}

async function saveReview(article, sampleId, action) {
  const checked = action === "allow"
    ? []
    : [...article.querySelectorAll("input:checked")].map((input) => input.value);
  const response = await fetch("/api/review", {
    method: "POST",
    headers: {"Content-Type": "application/json"},
    body: JSON.stringify({sample_id: sampleId, action, reasons: checked}),
  });
  if (!response.ok) throw new Error("No se pudo guardar");
  const payload = await response.json();
  const prediction = payload.model_prediction ?? {};
  article.classList.add("reviewed");
  article.querySelector(".review-state").textContent = `Tu decisión: ${actionLabel(action)}`;
  article.querySelector("[data-result]").textContent = actionLabel(prediction.action);
  article.querySelector("[data-result]").classList.toggle(
    "filter",
    prediction.action === "filter",
  );
  article.querySelector("[data-score]").textContent =
    `máx. ${Number(prediction.maximum_probability ?? 0).toFixed(3)}`;
  article.querySelector("[data-category]").textContent = categoryLabel(payload.category);
}

async function loadItems() {
  const parameters = new URLSearchParams({
    scope: document.querySelector("#scope").value,
    action: document.querySelector("#action").value,
    category: document.querySelector("#category").value,
    limit: "200",
  });
  const payload = await (await fetch(`/api/items?${parameters}`)).json();
  const grid = document.querySelector("#grid");
  if (!payload.items.length) {
    grid.innerHTML = '<div class="empty">No hay imágenes en este filtro.</div>';
    return;
  }
  grid.innerHTML = payload.items.map((item) => {
    const prediction = item.model_prediction ?? {};
    const reviewed = item.human_decision ? " reviewed" : "";
    const selectedReasons = new Set(item.human_decision?.reasons ?? []);
    return `<article class="${reviewed}" data-id="${esc(item.sample_id)}">
      <img class="image" src="${esc(item.image_url)}" loading="lazy" alt="">
      <div class="body">
        <div class="id">${esc(item.sample_id)}</div>
        <div class="score">
          <span class="pill prediction ${prediction.action === "filter" ? "filter" : ""}" data-result>${esc(actionLabel(prediction.action))}</span>
          <span class="pill prediction" data-score>máx. ${Number(prediction.maximum_probability ?? 0).toFixed(3)}</span>
          <span class="pill prediction" data-category>${esc(categoryLabel(item.category))}</span>
        </div>
        <div class="review-state">${item.human_decision ? `Tu decisión: ${esc(actionLabel(item.human_decision.action))}` : "Pendiente de tu revisión"}</div>
        <div class="reasons">${reasons.map(([value, label]) => `<label><input type="checkbox" value="${value}" ${selectedReasons.has(value) ? "checked" : ""}> ${label}</label>`).join("")}</div>
        <div class="actions">
          <button class="allow" data-action="allow">Permitir</button>
          <button class="filter-button" data-action="filter">Filtrar</button>
          <button class="doubt" data-action="doubt">Dudosa</button>
        </div>
      </div>
    </article>`;
  }).join("");
  grid.querySelectorAll("article").forEach((article) => {
    article.querySelector(".image").addEventListener("click", () => article.classList.toggle("open"));
    article.querySelectorAll("[data-action]").forEach((button) => {
      button.addEventListener("click", () =>
        saveReview(article, article.dataset.id, button.dataset.action).catch((error) => alert(error.message))
      );
    });
  });
}

document.querySelectorAll("select").forEach((element) => element.addEventListener("change", loadItems));
document.querySelector("#reload").addEventListener("click", async () => { await loadStatus(); await loadItems(); });
document.querySelector("#export").addEventListener("click", () => { window.location.href = "/api/export"; });
await loadStatus();
await loadItems();
